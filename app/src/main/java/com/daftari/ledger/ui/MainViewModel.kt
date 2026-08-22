package com.daftari.ledger.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.R
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.data.AccountCodes
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as DaftariApp).repo
    private val services = MainUiServices(app, repo)
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val totalsMutex = Mutex()
    private var shopBindJob: Job? = null

    init {
        viewModelScope.launch {
            repo.ensureSettings()
            repo.settings.get()?.let { settings ->
                mutableState.update {
                    it.copy(
                        hasPin = settings.pinHash != null,
                        locked = settings.pinHash != null,
                        biometric = settings.biometricUnlock,
                        autoBackup = settings.autoBackupEnabled
                    )
                }
            }
            repo.shops.observeActive().collectLatest { shops ->
                var selected = mutableState.value.shop
                if (selected == null || shops.none { it.id == selected?.id }) selected = shops.firstOrNull()
                if (shops.isEmpty()) {
                    val id = repo.createShop(getApplication<Application>().getString(R.string.default_shop_name))
                    selected = repo.shops.get(id)
                }
                mutableState.update {
                    it.copy(shops = shops.ifEmpty { listOfNotNull(selected) }, shop = selected, loading = false)
                }
                selected?.let { bindShop(it.id) }
            }
        }
        viewModelScope.launch {
            repo.audit.observe().collectLatest { rows -> mutableState.update { it.copy(audit = rows) } }
        }
        // تتغير أعمار الديون بمرور الوقت حتى من دون عملية جديدة.
        viewModelScope.launch {
            while (isActive) {
                delay(AGING_REFRESH_INTERVAL_MS)
                refreshInsights()
            }
        }
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SetPeriod -> setPeriod(event.period)
            is UiEvent.SetCustomRange -> setCustomRange(event.from, event.to)
            is UiEvent.SelectShop -> selectShop(event.shop)
            is UiEvent.AddShop -> addShop(event.name)
            is UiEvent.AddParty -> addParty(event)
            is UiEvent.UpdateParty -> updateParty(event)
            is UiEvent.AddDocument -> addDocument(event.draft)
            is UiEvent.UpdateDocument -> updateDocument(event)
            is UiEvent.DeleteDocument -> deleteDocument(event.id)
            is UiEvent.Unlock -> unlock(event.pin)
            UiEvent.BiometricUnlocked -> mutableState.update { it.copy(locked = false) }
            is UiEvent.SavePin -> savePin(event.pin)
            UiEvent.ClearPin -> clearPin()
            is UiEvent.ToggleBackup -> toggleBackup(event.enabled)
            is UiEvent.ToggleBiometric -> toggleBiometric(event.enabled)
            is UiEvent.CloseDay -> closeDay(event.actual, event.notes)
            is UiEvent.CloseParty -> closeParty(event.id)
            UiEvent.LoadInsights -> refreshInsights()
            is UiEvent.PreviewCsv -> mutableState.update { it.copy(csvPreview = repo.parseCsv(event.text)) }
            UiEvent.CommitCsv -> commitCsv()
            UiEvent.ExportPdf -> createFile(R.string.msg_pdf_ready, R.string.msg_pdf_failed) { services.exportPdf(state.value) }
            UiEvent.ExportExcel -> createFile(R.string.msg_excel_ready, R.string.msg_excel_failed) { services.exportExcel(state.value) }
            UiEvent.BackupNow -> backupNow()
            UiEvent.RefreshBackups -> mutableState.update { it.copy(backups = services.listBackups()) }
            is UiEvent.RestoreBackup -> restoreBackup(event.file, event.password)
            is UiEvent.BackupEncrypted -> backupEncrypted(event.password)
            is UiEvent.OpenParty -> openParty(event.party)
            UiEvent.ClosePartyDialog -> mutableState.update { it.copy(selectedParty = null, partyStats = null) }
            is UiEvent.ShareStatement -> shareStatement(event.party)
            UiEvent.ExportCsv -> exportCsv()
            UiEvent.ConsumeMessage -> mutableState.update { it.copy(message = null) }
            UiEvent.ConsumeShareFile -> mutableState.update { it.copy(shareFile = null) }
            UiEvent.ConsumeShareText -> mutableState.update { it.copy(shareText = null) }
            UiEvent.ConsumeRestart -> mutableState.update { it.copy(restartRequested = false) }
        }
    }

    private fun bindShop(id: Long) {
        shopBindJob?.cancel()
        shopBindJob = viewModelScope.launch {
            launch {
                repo.observeCustomers(id).collectLatest { customers ->
                    mutableState.update { it.copy(customers = customers) }
                }
            }
            launch {
                repo.observeSuppliers(id).collectLatest { suppliers ->
                    mutableState.update { it.copy(suppliers = suppliers) }
                }
            }
        }
        refreshTotals()
        refreshInsights()
    }

    private fun setPeriod(period: Period) {
        mutableState.update { it.copy(period = period) }
        refreshTotals()
    }

    private fun setCustomRange(from: Long, to: Long) {
        val (start, end) = if (from <= to) from to to else to to from
        mutableState.update { it.copy(period = Period.CUSTOM, customFrom = start, customTo = end) }
        refreshTotals()
    }

    private fun selectShop(shop: ShopEntity) {
        mutableState.update { it.copy(shop = shop) }
        bindShop(shop.id)
    }

    private fun refreshTotals() {
        val shop = state.value.shop ?: return
        val period = state.value.period
        viewModelScope.launch {
            totalsMutex.withLock {
                val currentRange = PeriodRanges.current(period, state.value.customFrom, state.value.customTo)
                val (from, to) = currentRange
                val (previousFrom, previousTo) = PeriodRanges.previous(currentRange)
                val totals = repo.totals(shop.id, from, to)
                val docs = repo.documents.listPeriod(shop.id, from, to)
                val previous = repo.totals(shop.id, previousFrom, previousTo)
                mutableState.update {
                    it.copy(
                        totals = totals,
                        prevTotals = previous,
                        docs = docs.sortedByDescending { document -> document.occurredAt },
                        owedToYou = repo.youAreOwed(shop.id),
                        youOwe = repo.youOwe(shop.id)
                    )
                }
            }
        }
    }

    private fun refreshInsights() {
        val shop = state.value.shop ?: return
        viewModelScope.launch {
            val aging = repo.aging(shop.id, PartyKind.CUSTOMER.name)
            val late = repo.lateCustomers(shop.id)
            mutableState.update {
                it.copy(
                    aging = aging,
                    late = late,
                    agingAlert = aging.count { row -> row.b61 > 0 || row.b90 > 0 }
                )
            }
        }
    }

    private fun addShop(name: String) = viewModelScope.launch { repo.createShop(name) }

    private fun addParty(event: UiEvent.AddParty) = viewModelScope.launch {
        val shop = state.value.shop ?: return@launch
        try {
            repo.addParty(
                shop.id,
                event.kind,
                event.name,
                event.phone,
                Money.fromMajor(event.openingMajor)?.minor ?: 0L,
                category = event.category,
                creditLimitMinor = Money.fromMajor(event.limitMajor)?.minor ?: 0L
            )
            refreshAll()
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun updateParty(event: UiEvent.UpdateParty) = viewModelScope.launch {
        try {
            repo.updatePartyExtra(event.id, event.category, Money.fromMajor(event.limitMajor)?.minor ?: 0L)
            val updated = repo.parties.get(event.id)
            mutableState.update {
                it.copy(selectedParty = updated ?: it.selectedParty, message = text(R.string.msg_updated))
            }
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun addDocument(draft: DocumentDraft) = viewModelScope.launch {
        val shop = state.value.shop ?: return@launch
        val money = Money.fromMajor(draft.amount) ?: return@launch message(R.string.msg_invalid_amount)
        try {
            var partyId = draft.partyId
            if (partyId == null && !draft.newPartyName.isNullOrBlank()) {
                val kind = if (draft.type in listOf(DocType.SALE, DocType.COLLECT)) PartyKind.CUSTOMER else PartyKind.SUPPLIER
                partyId = repo.addParty(shop.id, kind, draft.newPartyName.trim())
            }
            repo.postDocument(
                shopId = shop.id,
                type = draft.type,
                amountMinor = money.minor,
                occurredAt = draft.occurredAt,
                partyId = partyId,
                cashCode = AccountCodes.CASH,
                docNumber = draft.documentNumber,
                notes = draft.notes,
                paymentMethod = if (draft.credit) "CREDIT" else "CASH",
                transferToCode = if (draft.type == DocType.TRANSFER) AccountCodes.BANK else null
            )
            refreshAll()
            message(R.string.msg_saved)
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun updateDocument(event: UiEvent.UpdateDocument) = viewModelScope.launch {
        val money = Money.fromMajor(event.amount) ?: return@launch message(R.string.msg_invalid_amount)
        try {
            repo.updateDocument(
                event.id,
                money.minor,
                event.occurredAt,
                event.notes,
                event.documentNumber,
                if (event.credit) "CREDIT" else "CASH"
            )
            refreshAll()
            message(R.string.msg_edited)
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun deleteDocument(id: Long) = viewModelScope.launch {
        repo.softDeleteDocument(id)
        refreshAll()
        message(R.string.msg_archived)
    }

    private fun unlock(pin: String) = viewModelScope.launch {
        if (repo.pinOk(pin)) {
            mutableState.update { it.copy(locked = false, message = text(R.string.msg_unlocked)) }
        } else message(R.string.msg_wrong_pin)
    }

    private fun savePin(pin: String) = viewModelScope.launch {
        if (pin.length < 4) return@launch message(R.string.msg_pin_too_short)
        repo.setPin(pin)
        mutableState.update { it.copy(hasPin = true, message = text(R.string.msg_pin_saved)) }
    }

    private fun clearPin() = viewModelScope.launch {
        repo.setPin(null)
        mutableState.update { it.copy(hasPin = false, locked = false, message = text(R.string.msg_pin_removed)) }
    }

    private fun toggleBackup(enabled: Boolean) = viewModelScope.launch {
        repo.setAutoBackup(enabled)
        mutableState.update { it.copy(autoBackup = enabled) }
        AutoBackupWorker.schedule(getApplication(), enabled)
    }

    private fun toggleBiometric(enabled: Boolean) = viewModelScope.launch {
        repo.setBiometric(enabled)
        mutableState.update { it.copy(biometric = enabled) }
    }

    private fun closeDay(actual: String, notes: String) = viewModelScope.launch {
        val shop = state.value.shop ?: return@launch
        val money = Money.fromMajor(actual) ?: return@launch message(R.string.msg_enter_actual_cash)
        try {
            repo.closeDay(shop.id, money.minor, notes)
            message(R.string.msg_day_closed)
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun closeParty(id: Long) = viewModelScope.launch {
        try {
            repo.closePartyAccount(id)
            refreshAll()
            mutableState.update {
                it.copy(
                    message = text(R.string.msg_account_closed),
                    selectedParty = null,
                    partyStats = null
                )
            }
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun commitCsv() = viewModelScope.launch {
        val shop = state.value.shop ?: return@launch
        try {
            val result = repo.importCsv(shop.id, state.value.csvPreview)
            refreshAll()
            mutableState.update {
                it.copy(
                    csvPreview = emptyList(),
                    message = text(R.string.msg_import_result, result.created, result.skipped)
                )
            }
        } catch (_: Exception) {
            message(R.string.msg_import_failed)
        }
    }

    private fun backupNow() = viewModelScope.launch {
        val file = services.backupDatabase()
        mutableState.update {
            it.copy(shareFile = file, backups = services.listBackups(), message = text(R.string.msg_backup_ready))
        }
    }

    private fun restoreBackup(file: File, password: String?) = viewModelScope.launch {
        try {
            services.restore(file, password)
            mutableState.update {
                it.copy(restartRequested = true, message = text(R.string.msg_restore_restarting))
            }
        } catch (error: Exception) {
            message(R.string.msg_restore_failed, error.message.orEmpty())
        }
    }

    private fun backupEncrypted(password: String) = viewModelScope.launch {
        if (password.isBlank()) return@launch message(R.string.msg_enter_backup_password)
        try {
            val file = services.backupEncrypted(password)
            mutableState.update {
                it.copy(shareFile = file, backups = services.listBackups(), message = text(R.string.msg_encrypted_backup_ready))
            }
        } catch (error: Exception) {
            message(R.string.msg_backup_failed, error.message.orEmpty())
        }
    }

    private fun openParty(party: PartyEntity) {
        mutableState.update { it.copy(selectedParty = party, partyStats = null) }
        viewModelScope.launch {
            val (totals, recent) = repo.partyStats(party.id)
            mutableState.update {
                it.copy(
                    partyStats = PartyStats(
                        totals.sales,
                        totals.purchases,
                        totals.collections,
                        totals.payments,
                        recent
                    )
                )
            }
        }
    }

    private fun shareStatement(party: PartyEntity) = viewModelScope.launch {
        mutableState.update { it.copy(shareText = services.statement(party)) }
    }

    private fun exportCsv() {
        val shop = state.value.shop ?: return
        createFile(R.string.msg_csv_exported, R.string.msg_export_failed) { services.exportCsv(shop.id) }
    }

    private fun createFile(@StringRes success: Int, @StringRes failure: Int, block: suspend () -> File) =
        viewModelScope.launch {
            try {
                val file = block()
                mutableState.update { it.copy(shareFile = file, message = text(success)) }
            } catch (error: Exception) {
                message(failure, error.message.orEmpty())
            }
        }

    private fun refreshAll() {
        refreshTotals()
        refreshInsights()
    }

    private fun dynamicError(error: Exception) {
        mutableState.update { it.copy(message = UiText.Dynamic(error.message.orEmpty())) }
    }

    private fun message(@StringRes id: Int, vararg args: Any) {
        mutableState.update { it.copy(message = text(id, *args)) }
    }

    private fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

    private companion object {
        const val AGING_REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    }
}
