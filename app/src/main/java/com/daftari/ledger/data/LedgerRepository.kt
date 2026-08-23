package com.daftari.ledger.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.daftari.ledger.domain.AccountType
import com.daftari.ledger.domain.AgingFifo
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val categories = db.categories()
    val audit = db.audit()
    val settings = db.settings()
    val dailyBooks = db.dailyBooks()

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
        category: String = DEFAULT_PARTY_CATEGORY, creditLimitMinor: Long = 0
    ): Long = db.withTransaction {
        if (name.isBlank()) throw LedgerException("أدخل اسم الحساب")
        val id = parties.insert(
            PartyEntity(
                shopId = shopId, kind = kind.name, name = name.trim(),
                phone = phone.trim(), notes = notes, openingMinor = openingMinor,
                cachedBalanceMinor = openingMinor,
                category = category.trim().ifBlank { DEFAULT_PARTY_CATEGORY },
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
                category = category.trim().ifBlank { DEFAULT_PARTY_CATEGORY },
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
        suspend fun must(code: String) = accounts.byCode(shopId, code)
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
        transferToCode: String? = null,
        dueAt: Long? = null,
        categoryId: Long? = null
    ): Long = db.withTransaction {
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        if (type == DocType.SALE || type == DocType.EXPENSE) ensureSalesDayOpen(shopId, occurredAt)
        val st = settings.get()
        if (st?.fiscalEnabled == true) {
            val a = st.fiscalStart; val b = st.fiscalEnd
            if (a != null && b != null && (occurredAt < a || occurredAt > b)) {
                throw LedgerException("التاريخ خارج السنة المالية")
            }
        }
        val shop = shops.get(shopId) ?: throw LedgerException("المحل غير موجود")
        val finalDocumentNumber = if (docNumber.isBlank() && type != DocType.OPENING) {
            shop.nextDocumentNumber.toString()
        } else docNumber.trim()
        if (finalDocumentNumber.isNotBlank() && st?.uniqueDocPerParty != false) {
            val count = documents.countDocNumber(shopId, finalDocumentNumber, partyId, -1)
            if (count > 0) throw LedgerException("رقم المستند مستخدم مسبقًا")
        }
        // نجمع المعرفات ونبني القيد قبل أي إدراج حتى لا يبقى مستند بلا قيود.
        val refs = refsFor(shopId, type, cashCode, transferToCode)
        val credit = paymentMethod == "CREDIT"
        val docId = documents.insert(
            DocumentEntity(
                shopId = shopId, type = type.name, partyId = partyId, cashAccountId = refs.cashId,
                amountMinor = amountMinor,
                occurredAt = occurredAt,
                dueAt = dueAt.takeIf { paymentMethod == "CREDIT" && type == DocType.SALE },
                categoryId = categoryId.takeIf { type == DocType.EXPENSE || type == DocType.INCOME },
                docNumber = finalDocumentNumber,
                notes = notes,
                paymentMethod = paymentMethod
            )
        )
        if (finalDocumentNumber.isNotBlank() && type != DocType.OPENING) {
            val numeric = finalDocumentNumber.toLongOrNull()
            val next = maxOf(shop.nextDocumentNumber + if (docNumber.isBlank()) 1 else 0, (numeric ?: 0L) + 1)
            if (next != shop.nextDocumentNumber) shops.update(shop.copy(nextDocumentNumber = next))
        }
        val lines = JournalLineBuilder.build(docId, type, amountMinor, partyId, credit, notes, refs)
        journal.insertAll(lines)
        partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "CREATE", entity = "document", entityId = docId, detail = type.name))
        docId
    }

    suspend fun updateDocument(
        id: Long, amountMinor: Long, occurredAt: Long, notes: String,
        docNumber: String, paymentMethod: String, dueAt: Long? = null,
        categoryId: Long? = null, cashCode: String = AccountCodes.CASH
    ) = db.withTransaction {
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val d = documents.get(id) ?: throw LedgerException("العملية غير موجودة")
        if (d.deletedAt != null) throw LedgerException("لا يمكن تعديل عملية مؤرشفة")
        val type = runCatching { DocType.valueOf(d.type) }.getOrNull()
            ?: throw LedgerException("نوع العملية غير معروف")
        if (type == DocType.SALE || type == DocType.EXPENSE) {
            ensureSalesDayOpen(d.shopId, d.occurredAt)
            ensureSalesDayOpen(d.shopId, occurredAt)
        }
        if (docNumber.isNotBlank()) {
            val c = documents.countDocNumber(d.shopId, docNumber, d.partyId, id)
            if (c > 0) throw LedgerException("رقم المستند مستخدم مسبقًا")
        }
        val refs = refsFor(d.shopId, type, cashCode, null)
        val lines = JournalLineBuilder.build(id, type, amountMinor, d.partyId, paymentMethod == "CREDIT", notes, refs)
        journal.deleteForDoc(id)
        journal.insertAll(lines)
        documents.update(
            d.copy(
                amountMinor = amountMinor,
                occurredAt = occurredAt,
                dueAt = dueAt.takeIf { paymentMethod == "CREDIT" && type == DocType.SALE },
                categoryId = categoryId.takeIf { type == DocType.EXPENSE || type == DocType.INCOME },
                cashAccountId = refs.cashId,
                notes = notes,
                docNumber = docNumber,
                paymentMethod = paymentMethod,
                updatedAt = System.currentTimeMillis()
            )
        )
        d.partyId?.let { refreshPartyBalance(it) }
        audit.insert(AuditLogEntity(action = "UPDATE", entity = "document", entityId = id, detail = type.name))
        Unit
    }

    suspend fun softDeleteDocument(id: Long) {
        db.withTransaction {
            val d = documents.get(id)
            if (d != null && d.deletedAt == null) {
                if (d.type == DocType.SALE.name || d.type == DocType.EXPENSE.name) {
                    ensureSalesDayOpen(d.shopId, d.occurredAt)
                }
                documents.update(
                    d.copy(
                        deletedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                d.partyId?.let { refreshPartyBalance(it) }
                audit.insert(AuditLogEntity(action = "DELETE", entity = "document", entityId = id))
            }
        }
    }

    suspend fun restoreDocument(id: Long) = db.withTransaction {
        val document = documents.get(id) ?: return@withTransaction
        if (document.deletedAt != null) {
            documents.update(document.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
            document.partyId?.let { refreshPartyBalance(it) }
            audit.insert(AuditLogEntity(action = "RESTORE", entity = "document", entityId = id))
        }
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
    fun observeCategories(shopId: Long): Flow<List<CategoryEntity>> = categories.observe(shopId)

    suspend fun addCategory(shopId: Long, kind: String, name: String): Long {
        if (name.isBlank()) throw LedgerException("أدخل اسم التصنيف")
        return categories.insert(CategoryEntity(shopId = shopId, kind = kind, name = name.trim()))
    }

    suspend fun totalsByCategory(
        shopId: Long,
        type: DocType,
        from: Long,
        to: Long,
        uncategorized: String
    ): List<CategoryTotal> = documents.totalsByCategory(shopId, type.name, from, to, uncategorized)

    suspend fun updateShopCurrency(shopId: Long, currencyCode: String) {
        val shop = shops.get(shopId) ?: throw LedgerException("المحل غير موجود")
        val normalized = currencyCode.trim().uppercase()
        runCatching { java.util.Currency.getInstance(normalized) }
            .getOrElse { throw LedgerException("رمز العملة غير صالح") }
        shops.update(shop.copy(currencyCode = normalized))
    }

    /** بحث بادئة FTS4؛ لا يستخدم LIKE الذي يبدأ بعلامة % ولا يفقد الفهرس. */
    suspend fun searchParties(shopId: Long, input: String): List<PartyEntity> {
        val match = input.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { token ->
                val escaped = token.replace("\"", "\"\"")
                "\"$escaped\"*"
            }
        if (match.isBlank()) return parties.listAll(shopId).take(50)
        return parties.searchFts(
            SimpleSQLiteQuery(
                """
                SELECT p.* FROM parties p
                JOIN parties_fts f ON CAST(f.partyId AS INTEGER) = p.id
                WHERE parties_fts MATCH ?
                  AND p.shopId = ?
                  AND p.deletedAt IS NULL
                ORDER BY p.name
                LIMIT 50
                """.trimIndent(),
                arrayOf(match, shopId)
            )
        )
    }

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

    /**
     * يجلب كل فواتير الأطراف المؤهلة باستعلام JOIN واحد ثم يطبق FIFO في الذاكرة.
     * بذلك نحافظ على دقة التخصيص المحاسبي من دون نمط N+1.
     */
    suspend fun aging(shopId: Long, kind: String): List<AgingRow> {
        val now = System.currentTimeMillis()
        return documents.agingDocuments(shopId, kind)
            .groupBy { it.party.id }
            .values
            .map { rows ->
                val party = rows.first().party
                val invoices = rows.mapNotNull { row ->
                    val amount = row.invoiceAmountMinor ?: return@mapNotNull null
                    val occurredAt = row.invoiceOccurredAt ?: return@mapNotNull null
                    AgingFifo.Invoice(amount, occurredAt)
                }
                val bucket = AgingFifo.allocate(party.cachedBalanceMinor, invoices, now)
                AgingRow(party, bucket.b0, bucket.b31, bucket.b61, bucket.b90)
            }
    }

    data class LateRow(
        val party: PartyEntity,
        val balanceMinor: Long,
        val lastDate: Long?,
        val daysLate: Int
    )

    /** عملاء برصيد مستحق عليهم، باستعلام JOIN/GROUP BY واحد لآخر حركة. */
    suspend fun lateCustomers(shopId: Long): List<LateRow> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return documents.overdueCustomers(shopId, now)
            .map { row ->
                val days = row.lastDate?.let { ((now - it) / day).toInt().coerceAtLeast(0) } ?: 0
                LateRow(row.party, row.party.cachedBalanceMinor, row.lastDate, days)
            }
            .sortedByDescending { it.daysLate }
    }

    /** إحصاءات الطرف بتجميع SQL، مع قائمة حديثة محدودة للعرض. */
    suspend fun partyStats(partyId: Long, recentLimit: Int = 50): Pair<PartyStatsAggregate, List<DocumentEntity>> =
        documents.partyStats(partyId) to documents.recentParty(partyId, recentLimit)

    suspend fun statement(party: PartyEntity): List<StatementLine> {
        var running = 0L
        return documents.statementRows(party.id).map { row ->
            val delta = if (party.kind == PartyKind.CUSTOMER.name) row.netDebitDelta else -row.netDebitDelta
            running = Math.addExact(running, delta)
            StatementLine(row.document, delta, running)
        }
    }

    suspend fun overdueParties(now: Long = System.currentTimeMillis()): List<OverduePartyRow> =
        documents.overdueParties(now)

    suspend fun salesBookDays(shopId: Long, from: Long, to: Long): List<DailyBookSummary> {
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val docsByDay = documents.listSalesBookPeriod(shopId, from, to).groupBy {
            startOfDay(it.occurredAt, zone)
        }
        val books = dailyBooks.listPeriod(shopId, startOfDay(from, zone), endOfDay(to, zone))
            .associateBy { it.dayStart }
        val result = mutableListOf<DailyBookSummary>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val docs = docsByDay[day].orEmpty()
            val sales = docs.filter { it.type == DocType.SALE.name }
            val outflows = docs.filter { it.type == DocType.EXPENSE.name }
            val book = books[day]
            val payments = sales.groupBy { it.paymentMethod }.map { (method, entries) ->
                PaymentTotal(method, entries.sumOf { it.amountMinor }, entries.size)
            }.sortedByDescending { it.amountMinor }
            val status = when {
                book?.status == "CLOSED" -> "CLOSED"
                book?.reopenedAt != null -> "REOPENED"
                docs.isNotEmpty() || !book?.notes.isNullOrBlank() -> "HAS_RECORDS"
                else -> "EMPTY"
            }
            result += DailyBookSummary(
                dayStart = day,
                salesMinor = sales.sumOf { it.amountMinor },
                outflowsMinor = outflows.sumOf { it.amountMinor },
                cashSalesMinor = sales.filter { it.paymentMethod != "CREDIT" }.sumOf { it.amountMinor },
                cashOutflowsMinor = outflows.filter { it.paymentMethod != "CREDIT" }.sumOf { it.amountMinor },
                saleCount = sales.size,
                outflowCount = outflows.size,
                notes = book?.notes.orEmpty(),
                status = status,
                closedAt = book?.closedAt,
                payments = payments
            )
            date = date.plusDays(1)
        }
        return result
    }

    suspend fun salesBookPeriodSummary(shopId: Long, from: Long, to: Long): SalesBookPeriodSummary {
        val days = salesBookDays(shopId, from, to)
        val active = days.filter { it.transactionCount > 0 }
        val sales = days.sumOf { it.salesMinor }
        val outflows = days.sumOf { it.outflowsMinor }
        val paymentTotals = days.flatMap { it.payments }.groupBy { it.method }.map { (method, rows) ->
            PaymentTotal(method, rows.sumOf { it.amountMinor }, rows.sumOf { it.count })
        }.sortedByDescending { it.amountMinor }
        return SalesBookPeriodSummary(
            salesMinor = sales,
            outflowsMinor = outflows,
            netCashMovementMinor = days.sumOf { it.netCashMovementMinor },
            saleCount = days.sumOf { it.saleCount },
            outflowCount = days.sumOf { it.outflowCount },
            activeDays = active.size,
            dailyAverageSalesMinor = if (active.isEmpty()) 0 else sales / active.size,
            bestDay = active.maxByOrNull { it.salesMinor },
            weakestDay = active.minByOrNull { it.salesMinor },
            paymentTotals = paymentTotals
        )
    }

    suspend fun salesBookEntries(shopId: Long, dayStart: Long): List<DocumentEntity> =
        documents.listSalesBookPeriod(shopId, dayStart, endOfDay(dayStart)).sortedByDescending { it.occurredAt }

    suspend fun searchSalesBook(
        shopId: Long,
        from: Long,
        to: Long,
        query: String,
        entryType: String?,
        paymentMethod: String?,
        categoryId: Long?
    ): List<DocumentEntity> = documents.searchSalesBook(
        shopId = shopId,
        from = from,
        to = to,
        query = query.trim(),
        amountMinor = com.daftari.ledger.domain.Money.fromMajor(query)?.minor,
        entryType = entryType,
        paymentMethod = paymentMethod,
        categoryId = categoryId
    )

    suspend fun postSalesBookEntry(shopId: Long, input: SalesBookEntryInput): Long {
        ensureSalesDayOpen(shopId, input.occurredAt)
        val type = runCatching { DocType.valueOf(input.type) }.getOrNull()
        if (type != DocType.SALE && type != DocType.EXPENSE) throw LedgerException("نوع إدخال الدفتر غير صالح")
        if (input.amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val method = input.paymentMethod.uppercase()
        if (type == DocType.EXPENSE && method == "CREDIT") {
            throw LedgerException("المخرج الآجل يُسجل كشراء من شاشة العمليات")
        }
        var partyId = input.partyId
        if (type == DocType.SALE && method == "CREDIT" && partyId == null) {
            val name = input.newPartyName?.trim().orEmpty()
            if (name.isBlank()) throw LedgerException("اختر العميل للبيع الآجل")
            partyId = addParty(shopId, PartyKind.CUSTOMER, name)
        }
        val cashCode = if (method == "CASH") AccountCodes.CASH else AccountCodes.BANK
        val id = postDocument(
            shopId = shopId,
            type = type,
            amountMinor = input.amountMinor,
            occurredAt = input.occurredAt,
            partyId = partyId,
            cashCode = cashCode,
            docNumber = input.documentNumber,
            notes = input.notes,
            paymentMethod = method,
            dueAt = input.dueAt,
            categoryId = input.categoryId
        )
        touchSalesDay(shopId, input.occurredAt)
        return id
    }

    suspend fun updateSalesBookEntry(id: Long, input: SalesBookEntryInput) {
        val current = documents.get(id) ?: throw LedgerException("العملية غير موجودة")
        ensureSalesDayOpen(current.shopId, current.occurredAt)
        ensureSalesDayOpen(current.shopId, input.occurredAt)
        val method = input.paymentMethod.uppercase()
        if (current.type == DocType.EXPENSE.name && method == "CREDIT") {
            throw LedgerException("المخرج الآجل يُسجل كشراء من شاشة العمليات")
        }
        updateDocument(
            id = id,
            amountMinor = input.amountMinor,
            occurredAt = input.occurredAt,
            notes = input.notes,
            docNumber = input.documentNumber,
            paymentMethod = method,
            dueAt = input.dueAt,
            categoryId = input.categoryId,
            cashCode = if (method == "CASH") AccountCodes.CASH else AccountCodes.BANK
        )
        touchSalesDay(current.shopId, current.occurredAt)
        touchSalesDay(current.shopId, input.occurredAt)
    }

    suspend fun archiveSalesBookEntry(id: Long) {
        val current = documents.get(id) ?: return
        ensureSalesDayOpen(current.shopId, current.occurredAt)
        softDeleteDocument(id)
        touchSalesDay(current.shopId, current.occurredAt)
    }

    suspend fun duplicateSalesBookEntry(id: Long, occurredAt: Long): Long {
        val source = documents.get(id) ?: throw LedgerException("العملية غير موجودة")
        return postSalesBookEntry(
            source.shopId,
            SalesBookEntryInput(
                type = source.type,
                amountMinor = source.amountMinor,
                occurredAt = occurredAt,
                categoryId = source.categoryId,
                paymentMethod = source.paymentMethod,
                partyId = source.partyId,
                notes = source.notes,
                dueAt = source.dueAt?.let { occurredAt + (it - source.occurredAt).coerceAtLeast(0) }
            )
        )
    }

    suspend fun saveSalesDayNotes(shopId: Long, dayStart: Long, notes: String) {
        val current = dailyBooks.get(shopId, dayStart)
        if (current?.status == "CLOSED") throw LedgerException("أعد فتح اليوم قبل تعديل ملاحظاته")
        dailyBooks.upsert(
            (current ?: DailyBookEntity(shopId = shopId, dayStart = dayStart)).copy(
                notes = notes,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun closeSalesDay(shopId: Long, dayStart: Long, notes: String? = null) {
        val current = dailyBooks.get(shopId, dayStart)
        val now = System.currentTimeMillis()
        dailyBooks.upsert(
            (current ?: DailyBookEntity(shopId = shopId, dayStart = dayStart)).copy(
                status = "CLOSED",
                notes = notes ?: current?.notes.orEmpty(),
                closedAt = now,
                updatedAt = now
            )
        )
        audit.insert(AuditLogEntity(action = "CLOSE_SALES_DAY", entity = "daily_book", entityId = current?.id, detail = dayStart.toString()))
    }

    suspend fun reopenSalesDay(shopId: Long, dayStart: Long) {
        val current = dailyBooks.get(shopId, dayStart) ?: DailyBookEntity(shopId = shopId, dayStart = dayStart)
        val now = System.currentTimeMillis()
        dailyBooks.upsert(current.copy(status = "OPEN", reopenedAt = now, updatedAt = now))
        audit.insert(AuditLogEntity(action = "REOPEN_SALES_DAY", entity = "daily_book", entityId = current.id.takeIf { it != 0L }, detail = dayStart.toString()))
    }

    private suspend fun ensureSalesDayOpen(shopId: Long, time: Long) {
        val day = startOfDay(time, ZoneId.systemDefault())
        if (dailyBooks.get(shopId, day)?.status == "CLOSED") {
            throw LedgerException("اليوم مغلق؛ استخدم «تعديل اليوم» أولًا")
        }
    }

    private suspend fun touchSalesDay(shopId: Long, time: Long) {
        val day = startOfDay(time, ZoneId.systemDefault())
        val current = dailyBooks.get(shopId, day)
        if (current == null) dailyBooks.upsert(DailyBookEntity(shopId = shopId, dayStart = day))
        else dailyBooks.upsert(current.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun startOfDay(time: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfDay(time: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    data class CsvCommit(val created: Int, val skipped: Int)

    fun parseCsv(text: String): List<CsvPreviewRow> = CsvParser.parse(text)

    suspend fun importCsv(shopId: Long, rows: List<CsvPreviewRow>): CsvCommit = db.withTransaction {
        var ok = 0; var skip = 0
        rows.forEach { r ->
            if (r.error != null) { skip++; return@forEach }
            val existing = searchParties(shopId, r.name).firstOrNull { it.name == r.name }
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

    suspend fun setPrivacyMode(hidden: Boolean) {
        val st = settings.get() ?: return
        settings.update(st.copy(hideBalances = hidden))
    }

    suspend fun setLatinDigits(enabled: Boolean) {
        val st = settings.get() ?: return
        settings.update(st.copy(latinDigits = enabled))
    }

    suspend fun updatePinProtection(failedAttempts: Int, lockedUntil: Long) {
        val st = settings.get() ?: return
        settings.update(st.copy(failedPinAttempts = failedAttempts, pinLockedUntil = lockedUntil))
    }
}
