package com.daftari.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.data.AuditLogEntity
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val shareFile: java.io.File? = null,
    val prevTotals: LedgerRepository.PeriodTotals = LedgerRepository.PeriodTotals(0, 0, 0, 0, 0, 0, 0, 0),
    val selectedParty: PartyEntity? = null,
    val partyStats: PartyStats? = null,
    val audit: List<AuditLogEntity> = emptyList(),
    val backups: List<java.io.File> = emptyList(),
    val shareText: String? = null,
    val agingAlert: Int = 0,
    val late: List<LedgerRepository.LateRow> = emptyList()
)

data class PartyStats(
    val sales: Long = 0,
    val purchases: Long = 0,
    val collections: Long = 0,
    val payments: Long = 0,
    val docs: List<DocumentEntity> = emptyList()
) {
    val collectionRate: Int
        get() = if (sales == 0L) 0 else ((collections * 100) / sales).toInt().coerceIn(0, 100)
}

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
        viewModelScope.launch {
            repo.audit.observe().collectLatest { rows ->
                _s.value = _s.value.copy(audit = rows)
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

    private fun previousRange(p: Period): Pair<Long, Long> {
        val (a, b) = range(p)
        val len = (b - a).coerceAtLeast(1L)
        return (a - len) to (a - 1)
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
        loadLate()
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
            val (pa, pb) = previousRange(_s.value.period)
            val pt = repo.totals(shop.id, pa, pb)
            val agingRows = repo.aging(shop.id, "CUSTOMER")
            _s.value = _s.value.copy(
                totals = t,
                prevTotals = pt,
                docs = docs.sortedByDescending { it.occurredAt },
                owedToYou = repo.youAreOwed(shop.id),
                youOwe = repo.youOwe(shop.id),
                agingAlert = agingRows.count { it.b61 > 0 || it.b90 > 0 }
            )
        }
    }

    fun addShop(name: String) = viewModelScope.launch {
        repo.createShop(name)
    }

    fun addParty(
        kind: PartyKind, name: String, phone: String, openingMajor: String,
        category: String, limitMajor: String
    ) = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val open = Money.fromMajor(openingMajor)?.minor ?: 0L
        val limit = Money.fromMajor(limitMajor)?.minor ?: 0L
        try {
            repo.addParty(shop.id, kind, name, phone, open, category = category, creditLimitMinor = limit)
            refreshTotals()
        } catch (e: LedgerException) {
            _s.value = _s.value.copy(message = e.message)
        }
    }

    fun updatePartyExtra(id: Long, category: String, limitMajor: String) = viewModelScope.launch {
        val limit = Money.fromMajor(limitMajor)?.minor ?: 0L
        try {
            repo.updatePartyExtra(id, category, limit)
            val updated = repo.parties.get(id)
            _s.value = _s.value.copy(selectedParty = updated ?: _s.value.selectedParty, message = "تم التحديث")
        } catch (e: LedgerException) {
            _s.value = _s.value.copy(message = e.message)
        }
    }

    fun addDoc(
        type: DocType, amount: String, partyId: Long?, credit: Boolean,
        notes: String, docNumber: String, cashCode: String = "1000",
        newPartyName: String? = null, occurredAt: Long = System.currentTimeMillis()
    ) = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        val m = Money.fromMajor(amount) ?: run {
            _s.value = _s.value.copy(message = "أدخل مبلغًا صحيحًا"); return@launch
        }
        try {
            var pid = partyId
            if (pid == null && !newPartyName.isNullOrBlank()) {
                val kind = if (type == DocType.SALE || type == DocType.COLLECT) PartyKind.CUSTOMER else PartyKind.SUPPLIER
                pid = repo.addParty(shop.id, kind, newPartyName.trim())
            }
            repo.postDocument(
                shopId = shop.id,
                type = type,
                amountMinor = m.minor,
                occurredAt = occurredAt,
                partyId = pid,
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

    fun updateDoc(
        id: Long, amount: String, notes: String, docNumber: String,
        credit: Boolean, occurredAt: Long
    ) = viewModelScope.launch {
        val m = Money.fromMajor(amount) ?: run {
            _s.value = _s.value.copy(message = "أدخل مبلغًا صحيحًا"); return@launch
        }
        try {
            repo.updateDocument(id, m.minor, occurredAt, notes, docNumber, if (credit) "CREDIT" else "CASH")
            refreshTotals()
            _s.value = _s.value.copy(message = "تم التعديل")
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
            _s.value = _s.value.copy(message = "أُغلق الحساب مع الاحتفاظ بالسجل", selectedParty = null, partyStats = null)
        } catch (e: LedgerException) { _s.value = _s.value.copy(message = e.message) }
    }

    fun loadAging() = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        _s.value = _s.value.copy(aging = repo.aging(shop.id, "CUSTOMER"))
    }

    fun loadLate() = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        _s.value = _s.value.copy(late = repo.lateCustomers(shop.id))
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

    fun exportExcel() {
        val ctx = getApplication<Application>()
        try {
            val f = com.daftari.ledger.export.ExcelReports.writePeriodExcel(ctx, _s.value)
            _s.value = _s.value.copy(shareFile = f, message = "تم إنشاء Excel")
        } catch (e: Exception) {
            _s.value = _s.value.copy(message = "فشل إنشاء Excel: ${e.message}")
        }
    }

    fun backupNow() = viewModelScope.launch {
        val app = getApplication() as DaftariApp
        val f = app.backup.exportJson()
        _s.value = _s.value.copy(shareFile = f, backups = app.backup.listBackups(), message = "نسخة احتياطية جاهزة")
    }

    fun refreshBackups() {
        val app = getApplication() as DaftariApp
        _s.value = _s.value.copy(backups = app.backup.listBackups())
    }

    fun restoreBackup(f: java.io.File, password: String? = null) = viewModelScope.launch {
        try {
            val app = getApplication() as DaftariApp
            if (f.name.endsWith(".enc")) app.backup.restoreEncrypted(f, password.orEmpty())
            else app.backup.restoreFrom(f)
            _s.value = _s.value.copy(message = "تمت الاستعادة. أغلق التطبيق وافتحه من جديد.")
        } catch (e: Exception) {
            _s.value = _s.value.copy(message = "فشل الاستعادة: ${e.message}")
        }
    }

    fun backupEncrypted(password: String) = viewModelScope.launch {
        if (password.isBlank()) {
            _s.value = _s.value.copy(message = "أدخل كلمة مرور للنسخة المشفرة"); return@launch
        }
        try {
            val app = getApplication() as DaftariApp
            val f = app.backup.exportEncrypted(password)
            _s.value = _s.value.copy(shareFile = f, backups = app.backup.listBackups(), message = "نسخة مشفرة جاهزة")
        } catch (e: Exception) {
            _s.value = _s.value.copy(message = "فشل إنشاء النسخة: ${e.message}")
        }
    }

    fun openParty(p: PartyEntity) {
        _s.value = _s.value.copy(selectedParty = p, partyStats = null)
        loadPartyStats(p)
    }

    fun closePartyDialog() {
        _s.value = _s.value.copy(selectedParty = null, partyStats = null)
    }

    private fun loadPartyStats(p: PartyEntity) {
        viewModelScope.launch {
            val docs = repo.documents.listParty(p.id)
            fun sum(t: DocType) = docs.filter { it.type == t.name }.sumOf { it.amountMinor }
            val st = PartyStats(
                sales = sum(DocType.SALE),
                purchases = sum(DocType.PURCHASE),
                collections = sum(DocType.COLLECT),
                payments = sum(DocType.PAY),
                docs = docs.sortedByDescending { it.occurredAt }
            )
            _s.value = _s.value.copy(partyStats = st)
        }
    }

    fun shareStatement(p: PartyEntity) = viewModelScope.launch {
        val docs = repo.documents.listParty(p.id)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder()
        sb.append("كشف حساب — ${p.name}\n")
        sb.append("الرصيد: ${Money(p.cachedBalanceMinor).format()}\n\n")
        sb.append("التاريخ | النوع | المبلغ\n")
        docs.sortedByDescending { it.occurredAt }.take(50).forEach { d ->
            sb.append("${fmt.format(Date(d.occurredAt))} | ${docTypeArabic(d.type)} | ${Money(d.amountMinor).format()}\n")
        }
        _s.value = _s.value.copy(shareText = sb.toString())
    }

    fun consumeShareText() { _s.value = _s.value.copy(shareText = null) }

    fun exportCsv() = viewModelScope.launch {
        val shop = _s.value.shop ?: return@launch
        try {
            val docs = repo.documents.listPeriod(shop.id, 0L, Long.MAX_VALUE)
            val byId = repo.parties.listAll(shop.id).associateBy { it.id }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val sb = StringBuilder("name,kind,amount,type,date,notes\n")
            docs.sortedByDescending { it.occurredAt }.forEach { d ->
                val p = d.partyId?.let { byId[it] }
                sb.append(
                    listOf(
                        p?.name ?: "", p?.kind ?: "", Money(d.amountMinor).toBigDecimal().toPlainString(),
                        d.type, fmt.format(Date(d.occurredAt)), d.notes
                    ).joinToString(",") { csvCell(it) }
                ).append("\n")
            }
            val f = java.io.File(getApplication<Application>().cacheDir, "daftari-export.csv")
            f.writeText(sb.toString())
            _s.value = _s.value.copy(shareFile = f, message = "تم تصدير CSV كامل")
        } catch (e: Exception) {
            _s.value = _s.value.copy(message = "فشل التصدير: ${e.message}")
        }
    }

    private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private fun docTypeArabic(t: String) = when (t) {
        "SALE" -> "بيع"; "PURCHASE" -> "شراء"; "EXPENSE" -> "مصروف"; "INCOME" -> "إيراد"
        "COLLECT" -> "تحصيل"; "PAY" -> "سداد"; "TRANSFER" -> "تحويل"; "OPENING" -> "افتتاحي"
        else -> t
    }

    val repoPublic get() = repo
}
