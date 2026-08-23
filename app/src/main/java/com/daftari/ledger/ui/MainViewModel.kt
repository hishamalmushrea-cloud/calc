package com.daftari.ledger.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.R
import com.daftari.ledger.data.AccountCodes
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import com.daftari.ledger.domain.StaffPermission
import com.daftari.ledger.widget.DaftariWidget
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    internal val repo = (app as DaftariApp).repo
    internal val staff = (app as DaftariApp).staff
    internal val services = MainUiServices(app, repo)
    internal val mutableState = MutableStateFlow(
        UiState(
            cloudSettings = (app as DaftariApp).cloudBackup.settings(),
            googleBackup = GoogleBackupUiState(
                settings = (app as DaftariApp).googleBackup.preferences.load(),
                hasLocalData = (app as DaftariApp).googleBackup.hasLocalData()
            )
        )
    )
    val state: StateFlow<UiState> = mutableState.asStateFlow()
    internal val mutableEffects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<UiEffect> = mutableEffects.asSharedFlow()

    private val totalsMutex = Mutex()
    private var shopBindJob: Job? = null

    init {
        viewModelScope.launch {
            repo.ensureSettings()
            repo.settings.get()?.let { settings ->
                val currentEmployee = staff.currentEmployee()
                mutableState.update {
                    it.copy(
                        hasPin = settings.pinHash != null,
                        locked = settings.pinHash != null,
                        biometric = settings.biometricUnlock,
                        autoBackup = settings.autoBackupEnabled,
                        hideBalances = settings.hideBalances,
                        latinDigits = settings.latinDigits,
                        pinLockedUntil = settings.pinLockedUntil,
                        employees = it.employees.copy(
                            enabled = settings.employeesEnabled,
                            currentEmployee = currentEmployee
                        )
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
            UiEvent.UndoDeleteDocument -> undoDeleteDocument()
            is UiEvent.ShareReceipt -> shareReceipt(event.document)
            is UiEvent.Unlock -> unlock(event.pin)
            UiEvent.BiometricUnlocked -> mutableState.update { it.copy(locked = false) }
            is UiEvent.SavePin -> savePin(event.pin)
            UiEvent.ClearPin -> clearPin()
            is UiEvent.ToggleBackup -> toggleBackup(event.enabled)
            is UiEvent.ToggleBiometric -> toggleBiometric(event.enabled)
            is UiEvent.TogglePrivacy -> togglePrivacy(event.enabled)
            is UiEvent.ToggleLatinDigits -> toggleLatinDigits(event.enabled)
            is UiEvent.UpdateCurrency -> updateCurrency(event.currencyCode)
            is UiEvent.AddCategory -> addCategory(event.kind, event.name)
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
            UiEvent.RunDatabaseHealthCheck -> runDatabaseHealthCheck()
            UiEvent.ClearDatabaseHealthCheck -> mutableState.update { it.copy(healthIssues = emptyList(), healthCheckedAt = 0) }
            UiEvent.ChooseCloudFolder -> mutableEffects.tryEmit(UiEffect.PickCloudFolder)
            is UiEvent.CloudFolderSelected -> saveCloudFolder(event.uri)
            UiEvent.ClearCloudFolder -> clearCloudFolder()
            is UiEvent.SaveWebDav -> saveWebDav(event.url, event.user, event.password)
            UiEvent.ClearWebDav -> clearWebDav()
            UiEvent.CloudBackupNow -> cloudBackupNow()
            UiEvent.ChooseCloudRestoreFile -> mutableEffects.tryEmit(UiEffect.PickBackupFile)
            is UiEvent.RestoreCloudFile -> restoreCloudFile(event.uri)
            UiEvent.RestoreLatestWebDav -> restoreLatestWebDav()
            UiEvent.OpenGoogleBackup -> openGoogleBackup()
            UiEvent.CloseGoogleBackup -> closeGoogleBackup()
            UiEvent.LinkGoogleBackup -> linkGoogleBackup()
            UiEvent.UnlinkGoogleBackup -> unlinkGoogleBackup()
            UiEvent.RefreshGoogleBackups -> requestGoogleAuthorization("LIST")
            UiEvent.GoogleBackupNow -> requestGoogleAuthorization("BACKUP")
            is UiEvent.SetGoogleBackupAutomatic -> setGoogleBackupAutomatic(event.enabled)
            is UiEvent.SetGoogleBackupWifiOnly -> setGoogleBackupWifiOnly(event.enabled)
            is UiEvent.PrepareGoogleRestore -> prepareGoogleRestore(event.backup)
            UiEvent.ConfirmGoogleRestore -> confirmGoogleRestore()
            is UiEvent.PrepareGoogleBackupDelete -> prepareGoogleBackupDelete(event.backup)
            UiEvent.ConfirmGoogleBackupDelete -> confirmGoogleBackupDelete()
            is UiEvent.GoogleBackupAuthorized -> handleGoogleBackupAuthorized(event)
            is UiEvent.GoogleBackupAuthorizationFailed -> googleBackupAuthorizationFailed(event.message)
            is UiEvent.CallPhone -> mutableEffects.tryEmit(UiEffect.OpenUri("tel:${Uri.encode(event.phone)}"))
            is UiEvent.OpenWhatsApp -> mutableEffects.tryEmit(UiEffect.OpenUri("https://wa.me/${event.phone.filter(Char::isDigit)}"))
            UiEvent.LoadSalesLedger -> loadSalesLedger()
            is UiEvent.SetSalesBookView -> setSalesBookView(event.view)
            is UiEvent.SetSalesBookRange -> setSalesBookRange(event.range)
            is UiEvent.SetSalesBookCustomRange -> setSalesBookCustomRange(event.from, event.to)
            is UiEvent.SelectSalesDay -> selectSalesDay(event.dayStart)
            UiEvent.CloseSalesDayPage -> closeSalesDayPage()
            is UiEvent.SaveSalesEntry -> saveSalesEntry(event.draft)
            is UiEvent.UpdateSalesEntry -> updateSalesEntry(event.id, event.draft)
            is UiEvent.ArchiveSalesEntry -> archiveSalesEntry(event.id)
            is UiEvent.DuplicateSalesEntry -> duplicateSalesEntry(event.id, event.occurredAt)
            is UiEvent.SaveSalesDayNotes -> saveSalesDayNotes(event.dayStart, event.notes)
            is UiEvent.CloseSalesBookDay -> closeSalesBookDay(event.dayStart, event.notes)
            is UiEvent.ReopenSalesBookDay -> reopenSalesBookDay(event.dayStart)
            is UiEvent.SearchSalesBook -> searchSalesBook(event)
            is UiEvent.ShareSalesDay -> shareSalesDay(event.dayStart, event.detailed)
            is UiEvent.ExportSalesPeriod -> exportSalesPeriod(event.from, event.to, event.format)
            UiEvent.OpenEmployees -> openEmployees()
            UiEvent.CloseEmployees -> closeEmployees()
            is UiEvent.SetEmployeesEnabled -> setEmployeesEnabled(event.enabled)
            is UiEvent.AddEmployee -> addEmployee(event.draft)
            is UiEvent.UpdateEmployee -> updateEmployee(event.id, event.draft)
            is UiEvent.ChangeEmployeeStatus -> changeEmployeeStatus(event.id, event.status)
            is UiEvent.SelectEmployee -> selectEmployee(event.id)
            is UiEvent.SelectEmployeePeriod -> selectEmployeePeriod(event.id, event.from, event.to)
            UiEvent.CloseEmployeeDetail -> closeEmployeeDetail()
            is UiEvent.SetEmployeeRange -> setEmployeeRange(event.range)
            is UiEvent.LoginEmployee -> loginEmployee(event.id, event.pin)
            is UiEvent.SwitchToOwner -> switchToOwner(event.pin)
            is UiEvent.SetEmployeeSwitcher -> setEmployeeSwitcher(event.open)
            is UiEvent.AssignEmployeeShop -> assignEmployeeShop(event.employeeId, event.shopId, event.active)
            is UiEvent.OpenEmployeeShift -> openEmployeeShift(event.employeeId, event.label, event.openingCash)
            is UiEvent.CloseEmployeeShift -> closeEmployeeShift(event.shiftId, event.actualCash, event.notes)
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
            launch {
                repo.observeCategories(id).collectLatest { categories ->
                    mutableState.update { it.copy(categories = categories) }
                }
            }
            launch {
                staff.observeForShop(id).collectLatest { employees ->
                    mutableState.update { it.copy(employees = it.employees.copy(employees = employees)) }
                }
            }
            launch {
                repo.observeItems(id).collectLatest { items ->
                    mutableState.update {
                        it.copy(
                            inventory = it.inventory.copy(
                                items = items,
                                lowStockCount = items.count { item -> item.trackStock && item.qtyMilli <= item.reorderQtyMilli }
                            )
                        )
                    }
                }
            }
        }
        loadEmployeeReport()
        refreshTotals()
        refreshInsights()
        loadSalesLedger()
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
                val uncategorized = getApplication<Application>().getString(R.string.uncategorized)
                val categoryTotals = repo.totalsByCategory(shop.id, DocType.EXPENSE, from, to, uncategorized) +
                    repo.totalsByCategory(shop.id, DocType.INCOME, from, to, uncategorized)
                mutableState.update {
                    it.copy(
                        totals = totals,
                        prevTotals = previous,
                        docs = docs.sortedByDescending { document -> document.occurredAt },
                        owedToYou = repo.youAreOwed(shop.id),
                        customerAdvances = repo.customerAdvances(shop.id),
                        youOwe = repo.youOwe(shop.id),
                        supplierCredits = repo.supplierCredits(shop.id),
                        nextDocumentNumber = repo.shops.get(shop.id)?.nextDocumentNumber ?: it.nextDocumentNumber,
                        categoryTotals = categoryTotals
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
                Money.fromMajor(event.openingMajor, shop.fractionDigits)?.minor ?: 0L,
                category = event.category,
                creditLimitMinor = Money.fromMajor(event.limitMajor, shop.fractionDigits)?.minor ?: 0L
            )
            refreshAll()
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun updateParty(event: UiEvent.UpdateParty) = viewModelScope.launch {
        try {
            val party = repo.parties.get(event.id) ?: throw LedgerException("الحساب غير موجود")
            val shop = repo.shops.get(party.shopId) ?: state.value.shop
            repo.updatePartyExtra(event.id, event.category, Money.fromMajor(event.limitMajor, shop?.fractionDigits ?: 2)?.minor ?: 0L)
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
        val money = Money.fromMajor(draft.amount, shop.fractionDigits) ?: return@launch message(R.string.msg_invalid_amount)
        try {
            val actorId = currentActorId()
            if (actorId != null) {
                staff.requirePermission(
                    actorId,
                    when (draft.type) {
                        DocType.SALE -> StaffPermission.RECORD_SALE
                        DocType.EXPENSE -> StaffPermission.RECORD_OUTFLOW
                        else -> StaffPermission.VIEW_ACCOUNTS
                    }
                )
            }
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
                transferToCode = if (draft.type == DocType.TRANSFER) AccountCodes.BANK else null,
                dueAt = draft.dueAt,
                categoryId = draft.categoryId,
                employeeId = actorId.takeIf { draft.type == DocType.SALE },
                actorEmployeeId = actorId
            )
            refreshAll()
            message(R.string.msg_saved)
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun updateDocument(event: UiEvent.UpdateDocument) = viewModelScope.launch {
        try {
            val actorId = currentActorId()
            val existing = repo.documents.get(event.id) ?: throw LedgerException("العملية غير موجودة")
            val shop = repo.shops.get(existing.shopId) ?: state.value.shop
            val money = Money.fromMajor(event.amount, shop?.fractionDigits ?: 2) ?: return@launch message(R.string.msg_invalid_amount)
            if (actorId != null) {
                val permission = if (existing.type == DocType.SALE.name || existing.type == DocType.EXPENSE.name) {
                    if (existing.employeeId == actorId) StaffPermission.EDIT_OWN_SALE else StaffPermission.EDIT_ANY_SALE
                } else StaffPermission.VIEW_ACCOUNTS
                staff.requirePermission(actorId, permission)
            }
            var partyId = event.partyId
            if (partyId == null && !event.newPartyName.isNullOrBlank()) {
                val kind = if (existing.type in listOf(DocType.SALE.name, DocType.COLLECT.name)) PartyKind.CUSTOMER else PartyKind.SUPPLIER
                partyId = repo.addParty(existing.shopId, kind, event.newPartyName.trim())
            }
            repo.updateDocument(
                event.id,
                money.minor,
                event.occurredAt,
                event.notes,
                event.documentNumber,
                if (event.credit) "CREDIT" else "CASH",
                event.dueAt,
                event.categoryId,
                actorEmployeeId = actorId,
                partyId = partyId,
                replaceParty = existing.type in listOf(DocType.SALE.name, DocType.COLLECT.name, DocType.PURCHASE.name, DocType.PAY.name)
            )
            refreshAll()
            message(R.string.msg_edited)
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun deleteDocument(id: Long) = viewModelScope.launch {
        try {
            val actorId = currentActorId()
            val existing = repo.documents.get(id) ?: throw LedgerException("العملية غير موجودة")
            if (actorId != null) {
                val permission = if (existing.type == DocType.SALE.name || existing.type == DocType.EXPENSE.name) {
                    if (existing.employeeId == actorId) StaffPermission.DELETE_OWN_SALE else StaffPermission.DELETE_ANY_SALE
                } else StaffPermission.VIEW_ACCOUNTS
                staff.requirePermission(actorId, permission)
            }
            repo.softDeleteDocument(id, actorEmployeeId = actorId)
            refreshAll()
            mutableState.update { it.copy(message = text(R.string.msg_archived), undoDocumentId = id) }
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun undoDeleteDocument() = viewModelScope.launch {
        val id = state.value.undoDocumentId ?: return@launch
        try {
            val actorId = currentActorId()
            val existing = repo.documents.get(id) ?: throw LedgerException("العملية غير موجودة")
            if (actorId != null) {
                val permission = if (existing.type == DocType.SALE.name || existing.type == DocType.EXPENSE.name) {
                    if (existing.employeeId == actorId) StaffPermission.DELETE_OWN_SALE else StaffPermission.DELETE_ANY_SALE
                } else StaffPermission.VIEW_ACCOUNTS
                staff.requirePermission(actorId, permission)
            }
            repo.restoreDocument(id, actorEmployeeId = actorId)
            refreshAll()
            mutableState.update { it.copy(message = text(R.string.msg_archive_undone), undoDocumentId = null) }
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }

    private fun shareReceipt(document: com.daftari.ledger.data.DocumentEntity) {
        val party = document.partyId?.let { id -> (state.value.customers + state.value.suppliers).firstOrNull { it.id == id } }
        createFile(R.string.msg_receipt_ready, R.string.msg_receipt_failed) {
            services.receipt(document, party, state.value.shop, state.value)
        }
    }

    private fun closeDay(actual: String, notes: String) = viewModelScope.launch {
        val shop = state.value.shop ?: return@launch
        val money = Money.fromMajor(actual, shop.fractionDigits) ?: return@launch message(R.string.msg_enter_actual_cash)
        try {
            currentActorId()?.let { staff.requirePermission(it, StaffPermission.MANAGE_SHIFTS) }
            repo.closeDay(shop.id, money.minor, notes, currentActorId())
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
            val statement = repo.statement(party)
            mutableState.update {
                it.copy(
                    partyStats = PartyStats(
                        totals.sales,
                        totals.purchases,
                        totals.collections,
                        totals.payments,
                        recent,
                        statement
                    )
                )
            }
        }
    }

    private fun shareStatement(party: PartyEntity) = viewModelScope.launch {
        mutableState.update { it.copy(shareText = services.statement(party, state.value)) }
    }

    private fun exportCsv() {
        val shop = state.value.shop ?: return
        createFile(R.string.msg_csv_exported, R.string.msg_export_failed) { services.exportCsv(shop.id) }
    }

    private fun runDatabaseHealthCheck() = viewModelScope.launch {
        try {
            val report = withContext(Dispatchers.IO) { repo.healthCheck() }
            mutableState.update {
                it.copy(
                    healthIssues = report.issues,
                    healthCheckedAt = System.currentTimeMillis(),
                    message = if (report.ok) text(R.string.msg_database_health_ok) else text(R.string.msg_database_health_issues, report.issues.size)
                )
            }
        } catch (error: Exception) {
            message(R.string.msg_database_health_failed, error.message.orEmpty())
        }
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

    internal fun refreshAll() {
        refreshTotals()
        refreshInsights()
        loadSalesLedger()
        DaftariWidget.updateAll(getApplication())
    }

    internal fun dynamicError(error: Exception) {
        mutableState.update { it.copy(message = UiText.Dynamic(error.message.orEmpty())) }
    }

    internal fun message(@StringRes id: Int, vararg args: Any) {
        mutableState.update { it.copy(message = text(id, *args)) }
    }

    internal fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

    private companion object {
        const val AGING_REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    }
}
