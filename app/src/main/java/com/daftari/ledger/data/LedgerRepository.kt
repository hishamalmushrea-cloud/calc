package com.daftari.ledger.data

import androidx.room.withTransaction
import com.daftari.ledger.domain.AccountType
import com.daftari.ledger.domain.AgingFifo
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import kotlinx.coroutines.flow.Flow

class LedgerException(msg: String) : Exception(msg)

/**
 * أرقام الحسابات النظامية التي تُنشأ مع كل محل.
 */
object AccountCodes {
    const val CASH = "1000"
    const val BANK = "1010"
    const val AR = "1100"
    const val AP = "2000"
    const val EQUITY = "3000"
    const val SALES = "4000"
    const val OTHER_INCOME = "4100"
    const val PURCHASES = "5000"
    const val EXPENSES = "5100"
}

/**
 * بناء سطور قيد اليومية كدالة صرفية قابلة للاختبار بدون قاعدة بيانات.
 *
 * كل المبالغ بالوحدة الصغرى (Long). تضمن الدالة تساوي المدين والدائن.
 *
 * @param cashId معرف الحساب النقدي الذي تمر به العملية.
 * @param transferDestId حساب الوجهة في حالة التحويل (وإلا يُرمى خطأ على TRANSFER).
 */
object JournalLineBuilder {

    data class AccountRefs(
        val cashId: Long,
        val salesId: Long,
        val purchasesId: Long,
        val expensesId: Long,
        val incomeId: Long,
        val arId: Long,
        val apId: Long,
        val destId: Long? = null
    )

    /**
     * @return قائمة بسطور القيد (مدين/دائن).
     * @throws LedgerException إذا كانت مدخلات العملية ناقصة أو غير منطقية.
     */
    fun build(
        docId: Long,
        type: DocType,
        amountMinor: Long,
        partyId: Long?,
        credit: Boolean,
        notes: String,
        refs: AccountRefs
    ): List<JournalLineEntity> {
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val lines = when (type) {
            DocType.SALE -> if (credit && partyId != null) listOf(
                JournalLineEntity(documentId = docId, accountId = refs.arId, partyId = partyId, debitMinor = amountMinor, memo = "بيع آجل"),
                JournalLineEntity(documentId = docId, accountId = refs.salesId, creditMinor = amountMinor)
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = refs.cashId, debitMinor = amountMinor, memo = "بيع نقدي"),
                JournalLineEntity(documentId = docId, accountId = refs.salesId, creditMinor = amountMinor)
            )

            DocType.PURCHASE -> if (credit && partyId != null) listOf(
                JournalLineEntity(documentId = docId, accountId = refs.purchasesId, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = refs.apId, partyId = partyId, creditMinor = amountMinor, memo = "شراء آجل")
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = refs.purchasesId, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = refs.cashId, creditMinor = amountMinor)
            )

            DocType.EXPENSE -> listOf(
                JournalLineEntity(documentId = docId, accountId = refs.expensesId, debitMinor = amountMinor, memo = notes),
                JournalLineEntity(documentId = docId, accountId = refs.cashId, creditMinor = amountMinor)
            )

            DocType.INCOME -> listOf(
                JournalLineEntity(documentId = docId, accountId = refs.cashId, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = refs.incomeId, creditMinor = amountMinor)
            )

            DocType.COLLECT -> {
                if (partyId == null) throw LedgerException("اختر العميل")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = refs.cashId, debitMinor = amountMinor),
                    JournalLineEntity(documentId = docId, accountId = refs.arId, partyId = partyId, creditMinor = amountMinor, memo = "تحصيل")
                )
            }

            DocType.PAY -> {
                if (partyId == null) throw LedgerException("اختر المورد")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = refs.apId, partyId = partyId, debitMinor = amountMinor, memo = "سداد"),
                    JournalLineEntity(documentId = docId, accountId = refs.cashId, creditMinor = amountMinor)
                )
            }

            DocType.TRANSFER -> {
                val destId = refs.destId ?: throw LedgerException("اختر حساب الوجهة")
                if (destId == refs.cashId) throw LedgerException("لا يمكن التحويل لنفس الحساب")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = destId, debitMinor = amountMinor, memo = "تحويل وارد"),
                    JournalLineEntity(documentId = docId, accountId = refs.cashId, creditMinor = amountMinor, memo = "تحويل صادر")
                )
            }

            else -> throw LedgerException("نوع العملية غير مدعوم من هذه الشاشة")
        }
        val dr = lines.sumOf { it.debitMinor }
        val cr = lines.sumOf { it.creditMinor }
        if (dr != cr) throw LedgerException("القيد غير متوازن")
        return lines
    }
}

class LedgerRepository(private val db: AppDb) {
    val shops = db.shops()
    val parties = db.parties()
    val accounts = db.accounts()
    val documents = db.documents()
    val journal = db.journal()
    val audit = db.audit()
    val settings = db.settings()

    suspend fun ensureSettings() {
        if (settings.get() == null) settings.insert(SettingsEntity())
    }

    suspend fun createShop(name: String, currency: String = "SAR"): Long = db.withTransaction {
        val id = shops.insert(ShopEntity(name = name.trim(), currencyCode = currency))
        seedAccounts(id)
        audit.insert(AuditLogEntity(action = "CREATE", entity = "shop", entityId = id, detail = name))
        id
    }

    private suspend fun seedAccounts(shopId: Long) {
        val seed = listOf(
            AccountEntity(shopId = shopId, code = AccountCodes.CASH, name = "الصندوق", type = AccountType.ASSET.name, isCashLike = true),
            AccountEntity(shopId = shopId, code = AccountCodes.BANK, name = "البنك", type = AccountType.ASSET.name, isCashLike = true),
            AccountEntity(shopId = shopId, code = AccountCodes.AR, name = "العملاء (ذمم مدينة)", type = AccountType.ASSET.name),
            AccountEntity(shopId = shopId, code = AccountCodes.AP, name = "الموردون (ذمم دائنة)", type = AccountType.LIABILITY.name),
            AccountEntity(shopId = shopId, code = AccountCodes.EQUITY, name = "رأس المال / افتتاحي", type = AccountType.EQUITY.name),
            AccountEntity(shopId = shopId, code = AccountCodes.SALES, name = "المبيعات", type = AccountType.INCOME.name),
            AccountEntity(shopId = shopId, code = AccountCodes.OTHER_INCOME, name = "إيرادات أخرى", type = AccountType.INCOME.name),
            AccountEntity(shopId = shopId, code = AccountCodes.PURCHASES, name = "المشتريات", type = AccountType.EXPENSE.name),
            AccountEntity(shopId = shopId, code = AccountCodes.EXPENSES, name = "المصروفات", type = AccountType.EXPENSE.name),
        )
        accounts.insertAll(seed)
    }

    suspend fun addParty(
        shopId: Long, kind: PartyKind, name: String, phone: String = "",
        openingMinor: Long = 0, notes: String = "",
        category: String = "عادي", creditLimitMinor: Long = 0
    ): Long = db.withTransaction {
        if (name.isBlank()) throw LedgerException("أدخل اسم الحساب")
        val id = parties.insert(
            PartyEntity(
                shopId = shopId, kind = kind.name, name = name.trim(),
                phone = phone.trim(), notes = notes, openingMinor = openingMinor,
                cachedBalanceMinor = openingMinor,
                category = category.trim().ifBlank { "عادي" },
                creditLimitMinor = creditLimitMinor.coerceAtLeast(0)
            )
        )
        if (openingMinor != 0L) {
            postOpening(shopId, id, kind, openingMinor)
        }
        audit.insert(AuditLogEntity(action = "CREATE", entity = "party", entityId = id, detail = name))
        id
    }

    suspend fun updatePartyExtra(partyId: Long, category: String, creditLimitMinor: Long) {
        val p = parties.get(partyId) ?: throw LedgerException("الحساب غير موجود")
        parties.update(
            p.copy(
                category = category.trim().ifBlank { "عادي" },
                creditLimitMinor = creditLimitMinor.coerceAtLeast(0)
            )
        )
        audit.insert(AuditLogEntity(action = "UPDATE", entity = "party", entityId = partyId, detail = "تصنيف/حد"))
    }

    private suspend fun postOpening(shopId: Long, partyId: Long, kind: PartyKind, amount: Long) {
        val ar = accounts.byCode(shopId, AccountCodes.AR)!!
        val ap = accounts.byCode(shopId, AccountCodes.AP)!!
        val eq = accounts.byCode(shopId, AccountCodes.EQUITY)!!
        val abs = kotlin.math.abs(amount)
        val docId = documents.insert(
            DocumentEntity(
                shopId = shopId, type = DocType.OPENING.name, partyId = partyId,
                amountMinor = abs, occurredAt = System.currentTimeMillis(), notes = "رصيد افتتاحي"
            )
        )
        val lines = if (kind == PartyKind.CUSTOMER) {
            // عميل مدين لك: مدين ذمم مدينة / دائن رأس مال
            if (amount >= 0) listOf(
                JournalLineEntity(documentId = docId, accountId = ar.id, partyId = partyId, debitMinor = abs),
                JournalLineEntity(documentId = docId, accountId = eq.id, creditMinor = abs)
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = eq.id, debitMinor = abs),
                JournalLineEntity(documentId = docId, accountId = ar.id, partyId = partyId, creditMinor = abs)
            )
        } else {
            // مورد عليك: دائن ذمم دائنة
            if (amount >= 0) listOf(
                JournalLineEntity(documentId = docId, accountId = eq.id, debitMinor = abs),
                JournalLineEntity(documentId = docId, accountId = ap.id, partyId = partyId, creditMinor = abs)
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = ap.id, partyId = partyId, debitMinor = abs),
                JournalLineEntity(documentId = docId, accountId = eq.id, creditMinor = abs)
            )
        }
        checkBalanced(lines)
        journal.insertAll(lines)
        refreshPartyBalance(partyId)
    }

    /** يجمع معرفات الحسابات المطلوبة لنوع العملية، مع رسائل خطأ واضحة. */
    private suspend fun refsFor(
        shopId: Long, type: DocType, cashCode: String, transferToCode: String?
    ): JournalLineBuilder.AccountRefs {
        val cash = accounts.byCode(shopId, cashCode) ?: throw LedgerException("حساب نقدي غير موجود")
        fun must(code: String) = accounts.byCode(shopId, code)
            ?: throw LedgerException("حساب النظام $code غير موجود — المحل غير مُهيَّأ بشكل صحيح")
        val dest = if (type == DocType.TRANSFER) {
            val code = transferToCode ?: AccountCodes.BANK
            accounts.byCode(shopId, code) ?: throw LedgerException("حساب التحويل غير موجود")
        } else null
        return JournalLineBuilder.AccountRefs(
            cashId = cash.id,
            salesId = must(AccountCodes.SALES).id,
            purchasesId = must(AccountCodes.PURCHASES).id,
            expensesId = must(AccountCodes.EXPENSES).id,
            incomeId = must(AccountCodes.OTHER_INCOME).id,
            arId = must(AccountCodes.AR).id,
            apId = must(AccountCodes.AP).id,
            destId = dest?.id
        )
    }

    private fun checkBalanced(lines: List<JournalLineEntity>) {
        val dr = lines.sumOf { it.debitMinor }
        val cr = lines.sumOf { it.creditMinor }
        if (dr != cr) throw LedgerException("القيد غير متوازن")
    }

    suspend fun postDocument(
        shopId: Long,
        type: DocType,
        amountMinor: Long,
        occurredAt: Long,
        partyId: Long? = null,
        cashCode: String = AccountCodes.CASH,
        docNumber: String = "",
        notes: String = "",
        paymentMethod: String = "CASH",
        transferToCode: String? = null
    ): Long = db.withTransaction {
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val st = settings.get()
        if (st?.fiscalEnabled == true) {
            val a = st.fiscalStart; val b = st.fiscalEnd
            if (a != null && b != null && (occurredAt < a || occurredAt > b)) {
                throw LedgerException("التاريخ خارج السنة المالية")
            }
        }
        if (docNumber.isNotBlank() && st?.uniqueDocPerParty != false) {
            val c = documents.countDocNumber(shopId, docNumber, partyId, -1)
            if (c > 0) throw LedgerException("رقم المستند مستخدم مسبقًا")
        }
        // نجمع المعرفات ونبني القيد قبل أي إدراج حتى لا يبقى مستند بلا قيود.
        val refs = refsFor(shopId, type, cashCode, transferToCode)
        val credit = paymentMethod == "CREDIT"
        val docId = documents.insert(
            DocumentEntity(
                shopId = shopId, type = type.name, partyId = partyId, cashAccountId = refs.cashId,
                amountMinor = amountMinor, occurredAt = occurredAt, docNumber = docNumber,
                notes = notes, paymentMethod = paymentMethod
            )
        )
        val lines = JournalLineBuilder.build(docId, type, amountMinor, partyId, credit, notes, refs)
        journal.insertAll(lines)
        partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "CREATE", entity = "document", entityId = docId, detail = type.name))
        docId
    }

    suspend fun updateDocument(
        id: Long, amountMinor: Long, occurredAt: Long, notes: String,
        docNumber: String, paymentMethod: String
    ) = db.withTransaction {
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val d = documents.get(id) ?: throw LedgerException("العملية غير موجودة")
        if (d.deletedAt != null) throw LedgerException("لا يمكن تعديل عملية مؤرشفة")
        val type = runCatching { DocType.valueOf(d.type) }.getOrNull()
            ?: throw LedgerException("نوع العملية غير معروف")
        if (docNumber.isNotBlank()) {
            val c = documents.countDocNumber(d.shopId, docNumber, d.partyId, id)
            if (c > 0) throw LedgerException("رقم المستند مستخدم مسبقًا")
        }
        val refs = refsFor(d.shopId, type, AccountCodes.CASH, null)
        val lines = JournalLineBuilder.build(id, type, amountMinor, d.partyId, paymentMethod == "CREDIT", notes, refs)
        journal.deleteForDoc(id)
        journal.insertAll(lines)
        documents.update(
            d.copy(
                amountMinor = amountMinor, occurredAt = occurredAt, notes = notes,
                docNumber = docNumber, paymentMethod = paymentMethod,
                updatedAt = System.currentTimeMillis()
            )
        )
        d.partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "UPDATE", entity = "document", entityId = id, detail = type.name))
        Unit
    }

    suspend fun softDeleteDocument(id: Long) = db.withTransaction {
        val d = documents.get(id) ?: return@withTransaction
        if (d.deletedAt != null) return@withTransaction // مؤرشفة مسبقًا
        documents.update(d.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        d.partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "DELETE", entity = "document", entityId = id))
        Unit
    }

    suspend fun refreshPartyBalance(partyId: Long) {
        val p = parties.get(partyId) ?: return
        val net = journal.partyNetDebit(partyId)
        // عميل: صافي مدين = لك. مورد: صافي دائن = عليك.
        val cached = if (p.kind == PartyKind.CUSTOMER.name) net else -net
        parties.update(p.copy(cachedBalanceMinor = cached))
    }

    data class PeriodTotals(
        val sales: Long, val purchases: Long, val expenses: Long, val income: Long,
        val collections: Long, val payments: Long, val cashIn: Long, val cashOut: Long
    ) {
        val estimatedProfit get() = sales + income - expenses - purchases
        val cashNet get() = cashIn - cashOut
    }

    suspend fun totals(shopId: Long, from: Long, to: Long): PeriodTotals {
        val docs = documents.listPeriod(shopId, from, to)
        fun sum(t: DocType) = docs.filter { it.type == t.name }.sumOf { it.amountMinor }
        val sales = sum(DocType.SALE)
        val purchases = sum(DocType.PURCHASE)
        val expenses = sum(DocType.EXPENSE)
        val income = sum(DocType.INCOME)
        val col = sum(DocType.COLLECT)
        val pay = sum(DocType.PAY)
        // نقد: بيع نقدي + تحصيل + إيراد مقابل مصروف + شراء نقدي + سداد
        val cashSales = docs.filter { it.type == DocType.SALE.name && it.paymentMethod != "CREDIT" }.sumOf { it.amountMinor }
        val cashPurch = docs.filter { it.type == DocType.PURCHASE.name && it.paymentMethod != "CREDIT" }.sumOf { it.amountMinor }
        val cashIn = cashSales + col + income
        val cashOut = cashPurch + expenses + pay
        return PeriodTotals(sales, purchases, expenses, income, col, pay, cashIn, cashOut)
    }

    suspend fun youAreOwed(shopId: Long): Long = parties.sumBalance(shopId, "CUSTOMER")
    suspend fun youOwe(shopId: Long): Long = parties.sumBalance(shopId, "SUPPLIER")

    fun observeCustomers(shopId: Long): Flow<List<PartyEntity>> = parties.observe(shopId, "CUSTOMER")
    fun observeSuppliers(shopId: Long): Flow<List<PartyEntity>> = parties.observe(shopId, "SUPPLIER")

    val closings = db.closings()

    suspend fun closeDay(shopId: Long, cashActualMinor: Long, notes: String): Long = db.withTransaction {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        if (closings.byDay(shopId, start) != null) throw LedgerException("اليوم مغلق مسبقًا")
        val t = totals(shopId, start, end)
        val expected = t.cashNet
        val id = closings.insert(
            DailyClosingEntity(
                shopId = shopId, dayStart = start,
                salesMinor = t.sales, expensesMinor = t.expenses,
                cashExpectedMinor = expected, cashActualMinor = cashActualMinor,
                differenceMinor = cashActualMinor - expected, notes = notes
            )
        )
        audit.insert(AuditLogEntity(action = "CLOSE_DAY", entity = "closing", entityId = id))
        id
    }

    /**
     * أرشفة عمليات الطرف وإنشاء رصيد افتتاحي جديد بنفس القيمة دون حذف تاريخي نهائي.
     * كل العملية تتم في معاملة واحدة؛ أي فشل يُرجع كل شيء كما كان.
     */
    suspend fun closePartyAccount(partyId: Long) = db.withTransaction {
        val p = parties.get(partyId) ?: throw LedgerException("الحساب غير موجود")
        // نعيد حساب الرصيد من القيود مباشرة (لا نثق بالكاش) قبل الأرشفة.
        refreshPartyBalance(partyId)
        val fresh = parties.get(partyId) ?: throw LedgerException("الحساب غير موجود")
        val bal = fresh.cachedBalanceMinor
        val kind = PartyKind.valueOf(fresh.kind)
        val now = System.currentTimeMillis()
        documents.listParty(partyId).forEach { d ->
            documents.update(d.copy(deletedAt = now, notes = d.notes + " [قبل الإغلاق]"))
        }
        parties.update(fresh.copy(openingMinor = bal))
        if (bal != 0L) {
            postOpening(fresh.shopId, partyId, kind, bal)
        } else {
            refreshPartyBalance(partyId)
        }
        audit.insert(AuditLogEntity(action = "CLOSE_ACCOUNT", entity = "party", entityId = partyId, detail = "رصيد $bal"))
        Unit
    }

    suspend fun aging(shopId: Long, kind: String): List<AgingRow> {
        val now = System.currentTimeMillis()
        return parties.listAll(shopId).filter { it.kind == kind && it.cachedBalanceMinor > 0 }.map { p ->
            val invoices = documents.listParty(p.id)
                .filter { it.type == DocType.SALE.name || it.type == DocType.OPENING.name }
                .map { AgingFifo.Invoice(it.amountMinor, it.occurredAt) }
            val bk = AgingFifo.allocate(p.cachedBalanceMinor, invoices, now)
            AgingRow(p, bk.b0, bk.b31, bk.b61, bk.b90)
        }
    }

    data class LateRow(
        val party: PartyEntity,
        val balanceMinor: Long,
        val lastDate: Long?,
        val daysLate: Int
    )

    /** عملاء برصيد مستحق عليهم، مرتبين بعدد أيام التأخير (من آخر بيع/تحصيل). */
    suspend fun lateCustomers(shopId: Long): List<LateRow> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return parties.listAll(shopId)
            .filter { it.kind == "CUSTOMER" && it.cachedBalanceMinor > 0 }
            .map { p ->
                val docs = documents.listParty(p.id)
                    .filter { it.type == DocType.SALE.name || it.type == DocType.COLLECT.name }
                val last = docs.maxOfOrNull { it.occurredAt }
                val days = last?.let { ((now - it) / day).toInt().coerceAtLeast(0) } ?: 0
                LateRow(p, p.cachedBalanceMinor, last, days)
            }
            .sortedByDescending { it.daysLate }
    }

    data class CsvCommit(val created: Int, val skipped: Int)

    fun parseCsv(text: String): List<CsvPreviewRow> = CsvParser.parse(text)

    suspend fun importCsv(shopId: Long, rows: List<CsvPreviewRow>): CsvCommit = db.withTransaction {
        var ok = 0; var skip = 0
        rows.forEach { r ->
            if (r.error != null) { skip++; return@forEach }
            val existing = parties.search(shopId, r.name).firstOrNull { it.name == r.name }
            val kind = if (r.kind.contains("مورد") || r.kind.equals("SUPPLIER", true)) PartyKind.SUPPLIER else PartyKind.CUSTOMER
            val pid = existing?.id ?: addParty(shopId, kind, r.name)
            val money = com.daftari.ledger.domain.Money.fromMajor(r.amount)
            if (money != null && money.minor > 0) {
                val t = runCatching { DocType.valueOf(r.type.uppercase()) }.getOrDefault(DocType.SALE)
                postDocument(shopId, t, money.minor, System.currentTimeMillis(), pid)
            }
            ok++
        }
        CsvCommit(ok, skip)
    }

    suspend fun setPin(pin: String?) {
        val st = settings.get() ?: SettingsEntity()
        val hash = pin?.let { com.daftari.ledger.security.PinHasher.hash(it) }
        val n = st.copy(pinHash = hash)
        if (settings.get() == null) settings.insert(n) else settings.update(n)
    }

    suspend fun pinOk(pin: String): Boolean {
        val h = settings.get()?.pinHash ?: return true
        return com.daftari.ledger.security.PinHasher.verify(pin, h)
    }

    suspend fun setAutoBackup(on: Boolean) {
        val st = settings.get() ?: return
        settings.update(st.copy(autoBackupEnabled = on))
    }

    suspend fun setBiometric(on: Boolean) {
        val st = settings.get() ?: return
        settings.update(st.copy(biometricUnlock = on))
    }
}
