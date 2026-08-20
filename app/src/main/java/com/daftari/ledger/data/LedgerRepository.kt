package com.daftari.ledger.data

import androidx.room.withTransaction
import com.daftari.ledger.domain.AccountType
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import kotlinx.coroutines.flow.Flow

class LedgerException(msg: String) : Exception(msg)

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

    suspend fun createShop(name: String, currency: String = "SAR"): Long {
        val id = shops.insert(ShopEntity(name = name.trim(), currencyCode = currency))
        seedAccounts(id)
        audit.insert(AuditLogEntity(action = "CREATE", entity = "shop", entityId = id, detail = name))
        return id
    }

    private suspend fun seedAccounts(shopId: Long) {
        val seed = listOf(
            AccountEntity(shopId = shopId, code = "1000", name = "الصندوق", type = AccountType.ASSET.name, isCashLike = true),
            AccountEntity(shopId = shopId, code = "1010", name = "البنك", type = AccountType.ASSET.name, isCashLike = true),
            AccountEntity(shopId = shopId, code = "1100", name = "العملاء (ذمم مدينة)", type = AccountType.ASSET.name),
            AccountEntity(shopId = shopId, code = "2000", name = "الموردون (ذمم دائنة)", type = AccountType.LIABILITY.name),
            AccountEntity(shopId = shopId, code = "3000", name = "رأس المال / افتتاحي", type = AccountType.EQUITY.name),
            AccountEntity(shopId = shopId, code = "4000", name = "المبيعات", type = AccountType.INCOME.name),
            AccountEntity(shopId = shopId, code = "4100", name = "إيرادات أخرى", type = AccountType.INCOME.name),
            AccountEntity(shopId = shopId, code = "5000", name = "المشتريات", type = AccountType.EXPENSE.name),
            AccountEntity(shopId = shopId, code = "5100", name = "المصروفات", type = AccountType.EXPENSE.name),
        )
        accounts.insertAll(seed)
    }

    suspend fun addParty(
        shopId: Long, kind: PartyKind, name: String, phone: String = "",
        openingMinor: Long = 0, notes: String = ""
    ): Long {
        if (name.isBlank()) throw LedgerException("أدخل اسم الحساب")
        val id = parties.insert(
            PartyEntity(
                shopId = shopId, kind = kind.name, name = name.trim(),
                phone = phone.trim(), notes = notes, openingMinor = openingMinor,
                cachedBalanceMinor = openingMinor
            )
        )
        if (openingMinor != 0L) {
            postOpening(shopId, id, kind, openingMinor)
        }
        audit.insert(AuditLogEntity(action = "CREATE", entity = "party", entityId = id, detail = name))
        return id
    }

    private suspend fun postOpening(shopId: Long, partyId: Long, kind: PartyKind, amount: Long) {
        val ar = accounts.byCode(shopId, "1100")!!
        val ap = accounts.byCode(shopId, "2000")!!
        val eq = accounts.byCode(shopId, "3000")!!
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
        journal.insertAll(lines)
        refreshPartyBalance(partyId)
    }

    suspend fun postDocument(
        shopId: Long,
        type: DocType,
        amountMinor: Long,
        occurredAt: Long,
        partyId: Long? = null,
        cashCode: String = "1000",
        docNumber: String = "",
        notes: String = "",
        paymentMethod: String = "CASH",
        transferToCode: String? = null
    ): Long {
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
        val cash = accounts.byCode(shopId, cashCode) ?: throw LedgerException("حساب نقدي غير موجود")
        val sales = accounts.byCode(shopId, "4000")!!
        val purch = accounts.byCode(shopId, "5000")!!
        val exp = accounts.byCode(shopId, "5100")!!
        val inc = accounts.byCode(shopId, "4100")!!
        val ar = accounts.byCode(shopId, "1100")!!
        val ap = accounts.byCode(shopId, "2000")!!

        val creditSale = paymentMethod == "CREDIT"
        val docId = documents.insert(
            DocumentEntity(
                shopId = shopId, type = type.name, partyId = partyId, cashAccountId = cash.id,
                amountMinor = amountMinor, occurredAt = occurredAt, docNumber = docNumber,
                notes = notes, paymentMethod = paymentMethod
            )
        )
        val lines = when (type) {
            DocType.SALE -> if (creditSale && partyId != null) listOf(
                JournalLineEntity(documentId = docId, accountId = ar.id, partyId = partyId, debitMinor = amountMinor, memo = "بيع آجل"),
                JournalLineEntity(documentId = docId, accountId = sales.id, creditMinor = amountMinor)
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = cash.id, debitMinor = amountMinor, memo = "بيع نقدي"),
                JournalLineEntity(documentId = docId, accountId = sales.id, creditMinor = amountMinor)
            )
            DocType.PURCHASE -> if (creditSale && partyId != null) listOf(
                JournalLineEntity(documentId = docId, accountId = purch.id, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = ap.id, partyId = partyId, creditMinor = amountMinor, memo = "شراء آجل")
            ) else listOf(
                JournalLineEntity(documentId = docId, accountId = purch.id, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = cash.id, creditMinor = amountMinor)
            )
            DocType.EXPENSE -> listOf(
                JournalLineEntity(documentId = docId, accountId = exp.id, debitMinor = amountMinor, memo = notes),
                JournalLineEntity(documentId = docId, accountId = cash.id, creditMinor = amountMinor)
            )
            DocType.INCOME -> listOf(
                JournalLineEntity(documentId = docId, accountId = cash.id, debitMinor = amountMinor),
                JournalLineEntity(documentId = docId, accountId = inc.id, creditMinor = amountMinor)
            )
            DocType.COLLECT -> {
                if (partyId == null) throw LedgerException("اختر العميل")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = cash.id, debitMinor = amountMinor),
                    JournalLineEntity(documentId = docId, accountId = ar.id, partyId = partyId, creditMinor = amountMinor, memo = "تحصيل")
                )
            }
            DocType.PAY -> {
                if (partyId == null) throw LedgerException("اختر المورد")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = ap.id, partyId = partyId, debitMinor = amountMinor, memo = "سداد"),
                    JournalLineEntity(documentId = docId, accountId = cash.id, creditMinor = amountMinor)
                )
            }
            DocType.TRANSFER -> {
                val destCode = transferToCode ?: "1010"
                val dest = accounts.byCode(shopId, destCode) ?: throw LedgerException("حساب التحويل غير موجود")
                if (dest.id == cash.id) throw LedgerException("لا يمكن التحويل لنفس الحساب")
                listOf(
                    JournalLineEntity(documentId = docId, accountId = dest.id, debitMinor = amountMinor, memo = "تحويل وارد"),
                    JournalLineEntity(documentId = docId, accountId = cash.id, creditMinor = amountMinor, memo = "تحويل صادر")
                )
            }
            else -> throw LedgerException("نوع العملية غير مدعوم من هذه الشاشة")
        }
        val dr = lines.sumOf { it.debitMinor }
        val cr = lines.sumOf { it.creditMinor }
        if (dr != cr) throw LedgerException("القيد غير متوازن")
        journal.insertAll(lines)
        partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "CREATE", entity = "document", entityId = docId, detail = type.name))
        return docId
    }

    suspend fun softDeleteDocument(id: Long) {
        val d = documents.get(id) ?: return
        documents.update(d.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        d.partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "DELETE", entity = "document", entityId = id))
    }

    suspend fun refreshPartyBalance(partyId: Long) {
        val p = parties.get(partyId) ?: return
        val net = journal.partyNetDebit(partyId)
        // عميل: صافي مدين = لك. مورد: صافي دائن = عليك = -net إن كان net مدين للحساب الدائن...
        // قيود المورد: دائن على حساب المورد => credit > debit => net debit سالب. نعرض عليه = -net للعميل العكس.
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
        // نقد: بيع نقدي + تحصيل + إيراد مقابل مصروف + شراء نقدي + سداد + تحويل صادر تقريبي عبر نوع الدفع
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

    suspend fun closeDay(shopId: Long, cashActualMinor: Long, notes: String): Long {
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
        return id
    }

    /**
     * أرشفة عمليات الطرف وإنشاء رصيد افتتاحي جديد بنفس القيمة دون حذف تاريخي نهائي.
     */
    suspend fun closePartyAccount(partyId: Long) {
        val p = parties.get(partyId) ?: throw LedgerException("الحساب غير موجود")
        val bal = p.cachedBalanceMinor
        val old = documents.listParty(partyId)
        old.forEach { d ->
            documents.update(d.copy(deletedAt = System.currentTimeMillis(), notes = d.notes + " [قبل الإغلاق]"))
        }
        parties.update(p.copy(openingMinor = bal, cachedBalanceMinor = 0))
        if (bal != 0L) {
            postOpening(p.shopId, partyId, PartyKind.valueOf(p.kind), bal)
        }
        audit.insert(AuditLogEntity(action = "CLOSE_ACCOUNT", entity = "party", entityId = partyId, detail = "رصيد ${bal}"))
    }

    suspend fun aging(shopId: Long, kind: String): List<AgingRow> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return parties.listAll(shopId).filter { it.kind == kind && it.cachedBalanceMinor > 0 }.map { p ->
            val docs = documents.listParty(p.id).filter {
                it.type == DocType.SALE.name || it.type == DocType.PURCHASE.name || it.type == DocType.OPENING.name
            }
            val oldest = docs.minByOrNull { it.occurredAt }?.occurredAt ?: p.createdAt
            val age = ((now - oldest) / day).toInt()
            val b = p.cachedBalanceMinor
            when {
                age <= 30 -> AgingRow(p, b, 0, 0, 0)
                age <= 60 -> AgingRow(p, 0, b, 0, 0)
                age <= 90 -> AgingRow(p, 0, 0, b, 0)
                else -> AgingRow(p, 0, 0, 0, b)
            }
        }
    }

    data class CsvCommit(val created: Int, val skipped: Int)

    fun parseCsv(text: String): List<CsvPreviewRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val start = if (lines.first().contains("name", true) || lines.first().contains("اسم")) 1 else 0
        return lines.drop(start).mapIndexed { i, line ->
            val p = line.split(',', ';', '\t').map { it.trim().trim('"') }
            val name = p.getOrNull(0).orEmpty()
            val kind = p.getOrNull(1).orEmpty().ifBlank { "CUSTOMER" }
            val amount = p.getOrNull(2).orEmpty()
            val type = p.getOrNull(3).orEmpty().ifBlank { "SALE" }
            val err = when {
                name.isBlank() -> "اسم فارغ"
                com.daftari.ledger.domain.Money.fromMajor(amount) == null && amount.isNotBlank() -> "مبلغ غير صالح"
                else -> null
            }
            CsvPreviewRow(start + i + 1, name, kind, amount, type, err)
        }
    }

    suspend fun importCsv(shopId: Long, rows: List<CsvPreviewRow>): CsvCommit {
        var ok = 0; var skip = 0
        db.withTransaction {
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
        }
        return CsvCommit(ok, skip)
    }

    suspend fun setPin(pin: String?) {
        val st = settings.get() ?: SettingsEntity()
        val hash = pin?.let { it.toByteArray().contentHashCode().toString() }
        val n = st.copy(pinHash = hash)
        if (settings.get() == null) settings.insert(n) else settings.update(n)
    }

    suspend fun pinOk(pin: String): Boolean {
        val h = settings.get()?.pinHash ?: return true
        return h == pin.toByteArray().contentHashCode().toString()
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
