package com.daftari.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Period { TODAY, YESTERDAY, WEEK, MONTH, YEAR, CUSTOM }

data class UiState(
    val shops: List<ShopEntity> = emptyList(),
    val shop: ShopEntity? = null,
    val customers: List<PartyEntity> = emptyList(),
    val suppliers: List<PartyEntity> = emptyList(),
    val docs: List<DocumentEntity> = emptyList(),
    val totals: LedgerRepository.PeriodTotals = LedgerRepository.PeriodTotals(0, 0, 0, 0, 0, 0, 0, 0),
    val owedToYou: Long = 0,
    val youOwe: Long = 0,
    val period: Period = Period.TODAY,
    val message: String? = null,
    val loading: Boolean = true,
    val locked: Boolean = false,
    val hasPin: Boolean = false,
    val biometric: Boolean = false,
    val autoBackup: Boolean = false,
    val aging: List<com.daftari.ledger.data.AgingRow> = emptyList(),
    val csvPreview: List<com.daftari.ledger.data.CsvPreviewRow> = emptyList(),
    val shareFile: java.io.File? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as DaftariApp).repo
    private val _s = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _s

    init {
        viewModelScope.launch {
            repo.ensureSettings()
            repo.shops.observeActive().collectLatest { shops ->
                var shop = _s.value.shop
                if (shop == null || shops.none { it.id == shop?.id }) shop = shops.firstOrNull()
                if (shops.isEmpty()) {
                    val id = repo.createShop("المحل الرئيسي")
                    shop = repo.shops.get(id)
                }
                _s.value = _s.value.copy(shops = shops.ifEmpty { listOfNotNull(shop) }, shop = shop, loading = false)
                shop?.let { bindShop(it.id) }
            }
        }
    }

    private fun range(p: Period): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        fun startDay(c: Calendar) = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        return when (p) {
            Period.TODAY -> startDay(cal) to now
            Period.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val s = startDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                s to startDay(cal) - 1
            }
            Period.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                startDay(cal) to now
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                startDay(cal) to now
            }
            Period.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                startDay(cal) to now
            }
            Period.CUSTOM -> startDay(Calendar.getInstance()) to now
        }
    }

    private fun bindShop(id: Long) {
        viewModelScope.launch {
            repo.observeCustomers(id).collectLatest { c ->
                _s.value = _s.value.copy(customers = c)
            }
        }
        viewModelScope.launch {
            repo.observeSuppliers(id).collectLatest { s ->
                _s.value = _s.value.copy(suppliers = s)
            }
        }
        refreshTotals()
    }

    fun setPeriod(p: Period) {
        _s.value = _s.value.copy(period = p)
        refreshTotals()
    }

    fun selectShop(s: ShopEntity) {
        _s.value = _s.value.copy(shop = s)
        bindShop(s.id)
    }

    fun refreshTotals() {
        val shop = _s.value.shop ?: return
        viewModelScope.launch {
            val (a, b) = range(_s.value.period)
            val t = repo.totals(shop.id, a, b)
            val docs = repo.documents.listPeriod(shop.id, a, b)
            _s.value = _s.value.copy(
                totals = t,
                docs = docs.sortedByDescending { it.occurredAt },
                owedToYou = repo.youAreOwed(shop.id),
                youOwe = repo.youOwe(shop.id)
            )
        }
    }

    fun addShop(name: String) = viewModelScope.launch {
        repo.createShop(name)
    }

    fun addParty(kind: PartyKind, name: String, phone: String, openingMajor: String) = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val open = Money.fromMajor(openingMajor)?.minor ?: 0L
        try {
            repo.addParty(shop.id, kind, name, phone, open)
            refreshTotals()
        } catch (e: LedgerException) {
            _s.value = _s.value.copy(message = e.message)
        }
    }

    fun addDoc(
        type: DocType, amount: String, partyId: Long?, credit: Boolean,
        notes: String, docNumber: String, cashCode: String = "1000"
    ) = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val m = Money.fromMajor(amount) ?: run {
            _s.value = _s.value.copy(message = "أدخل مبلغًا صحيحًا"); return@launch
        }
        try {
            repo.postDocument(
                shopId = shop.id,
                type = type,
                amountMinor = m.minor,
                occurredAt = System.currentTimeMillis(),
                partyId = partyId,
                cashCode = cashCode,
                docNumber = docNumber,
                notes = notes,
                paymentMethod = if (credit) "CREDIT" else "CASH",
                transferToCode = if (type == DocType.TRANSFER) "1010" else null
            )
            refreshTotals()
            _s.value = _s.value.copy(message = "تم الحفظ")
        } catch (e: LedgerException) {
            _s.value = _s.value.copy(message = e.message)
        }
    }

    fun deleteDoc(id: Long) = viewModelScope.launch {
        repo.softDeleteDocument(id)
        refreshTotals()
        _s.value = _s.value.copy(message = "تم الأرشفة. يمكن مراجعة السجل.")
    }

    fun consumeMessage() { _s.value = _s.value.copy(message = null) }
    fun consumeShare() { _s.value = _s.value.copy(shareFile = null) }

    suspend fun searchParties(q: String) = _s.value.shop?.let { repo.parties.search(it.id, q) }.orEmpty()

    fun unlock(pin: String) = viewModelScope.launch {
        if (repo.pinOk(pin)) _s.value = _s.value.copy(locked = false, message = "تم الفتح")
        else _s.value = _s.value.copy(message = "رمز غير صحيح")
    }

    fun unlockOk() { _s.value = _s.value.copy(locked = false) }

    fun savePin(pin: String) = viewModelScope.launch {
        if (pin.length < 4) { _s.value = _s.value.copy(message = "أربعة أرقام على الأقل"); return@launch }
        repo.setPin(pin)
        _s.value = _s.value.copy(hasPin = true, message = "تم حفظ رمز القفل")
    }

    fun clearPin() = viewModelScope.launch {
        repo.setPin(null)
        _s.value = _s.value.copy(hasPin = false, locked = false, message = "أُلغي القفل")
    }

    fun toggleBackup(on: Boolean) = viewModelScope.launch {
        repo.setAutoBackup(on)
        _s.value = _s.value.copy(autoBackup = on)
        com.daftari.ledger.backup.AutoBackupWorker.schedule(getApplication(), on)
    }

    fun toggleBio(on: Boolean) = viewModelScope.launch {
        repo.setBiometric(on)
        _s.value = _s.value.copy(biometric = on)
    }

    fun closeDay(actual: String, notes: String) = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val m = Money.fromMajor(actual) ?: run { _s.value = _s.value.copy(message = "أدخل النقد الفعلي"); return@launch }
        try {
            repo.closeDay(shop.id, m.minor, notes)
            _s.value = _s.value.copy(message = "تم إغلاق اليوم")
        } catch (e: LedgerException) { _s.value = _s.value.copy(message = e.message) }
    }

    fun closeParty(id: Long) = viewModelScope.launch {
        try {
            repo.closePartyAccount(id)
            refreshTotals()
            _s.value = _s.value.copy(message = "أُغلق الحساب مع الاحتفاظ بالسجل")
        } catch (e: LedgerException) { _s.value = _s.value.copy(message = e.message) }
    }

    fun loadAging() = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        _s.value = _s.value.copy(aging = repo.aging(shop.id, "CUSTOMER"))
    }

    fun previewCsv(text: String) {
        _s.value = _s.value.copy(csvPreview = repo.parseCsv(text))
    }

    fun commitCsv() = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val rows = _s.value.csvPreview
        try {
            val r = repo.importCsv(shop.id, rows)
            refreshTotals()
            _s.value = _s.value.copy(csvPreview = emptyList(), message = "استيراد ${r.created} صف، تخطي ${r.skipped}")
        } catch (e: Exception) {
            _s.value = _s.value.copy(message = "فشل الاستيراد ولم يُحفظ جزئيًا إن أمكن")
        }
    }

    fun exportPdf() {
        val ctx = getApplication<Application>()
        val f = com.daftari.ledger.export.PdfReports.writePeriodReport(ctx, _s.value)
        _s.value = _s.value.copy(shareFile = f, message = "تم إنشاء PDF")
    }

    fun backupNow() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val db = com.daftari.ledger.data.AppDb.get(ctx)
        val f = com.daftari.ledger.backup.BackupManager(ctx, db).exportJson()
        _s.value = _s.value.copy(shareFile = f, message = "نسخة احتياطية جاهزة")
    }

    val repoPublic get() = repo
}
