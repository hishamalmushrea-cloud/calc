package com.daftari.ledger.data

import androidx.room.withTransaction
import com.daftari.ledger.domain.AccountsBookMath
import com.daftari.ledger.domain.BookEntryCore
import com.daftari.ledger.domain.BookEntryKind
import com.daftari.ledger.domain.BookSide
import com.daftari.ledger.domain.StaffPermission
import kotlinx.coroutines.flow.Flow

/**
 * مستودع «دفتر الحسابات»: أشخاص وعمليات (له / عليه / تسديد) بعملات يختارها المستخدم.
 *
 * قواعد ثابتة:
 * - الأرصدة تُجمع من العمليات ولا تُخزَّن أبدًا، وتُحسب لكل (شخص، عملة) على حدة.
 * - المبالغ موجبة بالوحدة الصغرى، والاتجاه الفعلي محفوظ في `BookEntryEntity.side`.
 * - الحذف أرشفة ناعمة (`deletedAt`) فيبقى السجل قابلًا للتدقيق والاسترجاع.
 */
class AccountsBookRepository(private val db: AppDb) {

    private val staffAccess = StaffRepository(db)
    val currencies = db.currencies()
    val persons = db.bookPersons()
    val entries = db.bookEntries()
    private val audit = db.audit()
    private val settings = db.settings()

    fun observePersons(shopId: Long): Flow<List<BookPersonEntity>> = persons.observe(shopId)
    fun observeBalances(shopId: Long): Flow<List<BookBalanceRow>> = entries.observeBalances(shopId)
    fun observeCurrencies(): Flow<List<CurrencyEntity>> = currencies.observeActive()
    fun observeEntries(personId: Long): Flow<List<BookEntryEntity>> = entries.observeForPerson(personId)
    fun observeLastActivity(): Flow<List<BookActivityRow>> = entries.observeLastActivity()

    suspend fun listCurrencies(): List<CurrencyEntity> = currencies.list()
    suspend fun listPersons(shopId: Long): List<BookPersonEntity> = persons.list(shopId)
    suspend fun getPerson(id: Long): BookPersonEntity? = persons.get(id)
    suspend fun getEntry(id: Long): BookEntryEntity? = entries.get(id)

    /**
     * يضمن وجود عملات بداية حتى لو فُقدت (استعادة نسخة قديمة مثلًا).
     * الإدخال يتجاوز الموجود ولا يُحيي عملة مؤرشفة.
     */
    suspend fun ensureSeeded() {
        if (currencies.count() > 0) return
        CurrencySeeds.DEFAULTS.forEach { seed ->
            if (currencies.byCode(seed.code) == null) {
                currencies.insert(
                    CurrencyEntity(
                        code = seed.code,
                        name = seed.name,
                        symbol = seed.symbol,
                        fractionDigits = seed.fractionDigits
                    )
                )
            }
        }
    }

    /** العملة المختارة مسبقًا في نافذة تسجيل العملية. */
    suspend fun defaultCurrency(shopCurrencyCode: String?): CurrencyEntity? {
        val active = currencies.list().filterNot { it.archived }
        if (active.isEmpty()) return null
        settings.get()?.defaultCurrencyId?.let { id -> active.firstOrNull { it.id == id }?.let { return it } }
        shopCurrencyCode?.let { code -> active.firstOrNull { it.code.equals(code, ignoreCase = true) }?.let { return it } }
        return active.firstOrNull { it.code == CurrencySeeds.LOCAL_CODE } ?: active.first()
    }

    // ------------------------------------------------------------------ العملات

    suspend fun addCurrency(
        name: String,
        symbol: String = "",
        fractionDigits: Int = DEFAULT_FRACTION_DIGITS,
        code: String = "",
        actorEmployeeId: Long? = null
    ): Long = db.withTransaction {
        requireActor(actorEmployeeId, StaffPermission.MANAGE_SETTINGS)
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw LedgerException("أدخل اسم العملة")
        val id = currencies.insert(
            CurrencyEntity(
                code = uniqueCode(code),
                name = cleanName,
                symbol = symbol.trim(),
                fractionDigits = fractionDigits.coerceIn(0, MAX_FRACTION_DIGITS)
            )
        )
        audit.insert(
            AuditLogEntity(action = "CREATE", entity = "currency", entityId = id, detail = cleanName, actorEmployeeId = actorEmployeeId)
        )
        id
    }

    suspend fun updateCurrency(
        id: Long,
        name: String,
        symbol: String,
        fractionDigits: Int,
        actorEmployeeId: Long? = null
    ) {
        requireActor(actorEmployeeId, StaffPermission.MANAGE_SETTINGS)
        val currency = currencies.get(id) ?: throw LedgerException("العملة غير موجودة")
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw LedgerException("أدخل اسم العملة")
        currencies.update(
            currency.copy(
                name = cleanName,
                symbol = symbol.trim(),
                fractionDigits = fractionDigits.coerceIn(0, MAX_FRACTION_DIGITS)
            )
        )
        audit.insert(
            AuditLogEntity(action = "UPDATE", entity = "currency", entityId = id, detail = cleanName, actorEmployeeId = actorEmployeeId)
        )
    }

    /**
     * أرشفة عملة: تختفي من الاختيار ولا تُمس عملياتها القديمة، فتبقى الأرصدة
     * القديمة معروضة بعملتها الصحيحة.
     */
    suspend fun archiveCurrency(id: Long, actorEmployeeId: Long? = null) {
        requireActor(actorEmployeeId, StaffPermission.MANAGE_SETTINGS)
        val currency = currencies.get(id) ?: throw LedgerException("العملة غير موجودة")
        currencies.update(currency.copy(archived = true))
        audit.insert(
            AuditLogEntity(action = "ARCHIVE", entity = "currency", entityId = id, detail = currency.name, actorEmployeeId = actorEmployeeId)
        )
    }

    suspend fun setDefaultCurrency(id: Long, actorEmployeeId: Long? = null) {
        requireActor(actorEmployeeId, StaffPermission.MANAGE_SETTINGS)
        val currency = currencies.get(id) ?: throw LedgerException("العملة غير موجودة")
        val current = settings.get()
        if (current == null) settings.insert(SettingsEntity(defaultCurrencyId = currency.id))
        else settings.update(current.copy(defaultCurrencyId = currency.id))
        audit.insert(
            AuditLogEntity(action = "UPDATE", entity = "settings", entityId = 1, detail = "defaultCurrency=${currency.code}", actorEmployeeId = actorEmployeeId)
        )
    }

    // ------------------------------------------------------------------ الأشخاص

    suspend fun addPerson(
        shopId: Long,
        name: String,
        phone: String = "",
        notes: String = "",
        currencyId: Long? = null,
        opening: BookOpeningBalance? = null,
        actorEmployeeId: Long? = null
    ): Long = db.withTransaction {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw LedgerException("أدخل اسم الشخص")
        val id = persons.insert(
            BookPersonEntity(
                shopId = shopId,
                name = cleanName,
                phone = phone.trim(),
                notes = notes.trim(),
                currencyId = requireUsableCurrency(currencyId)
            )
        )
        if (opening != null && opening.amountMinor > 0) {
            insertEntry(
                personId = id,
                currencyId = opening.currencyId,
                kind = kindOf(opening.kind),
                amountMinor = opening.amountMinor,
                occurredAt = System.currentTimeMillis(),
                details = "",
                opening = true,
                actorEmployeeId = actorEmployeeId
            )
        }
        audit.insert(
            AuditLogEntity(action = "CREATE", entity = "book_person", entityId = id, detail = cleanName, actorEmployeeId = actorEmployeeId)
        )
        id
    }

    suspend fun updatePerson(
        id: Long,
        name: String,
        phone: String,
        notes: String,
        currencyId: Long? = null,
        actorEmployeeId: Long? = null
    ) {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        val person = persons.get(id) ?: throw LedgerException("الشخص غير موجود")
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw LedgerException("أدخل اسم الشخص")
        persons.update(
            person.copy(
                name = cleanName,
                phone = phone.trim(),
                notes = notes.trim(),
                currencyId = requireUsableCurrency(currencyId),
                updatedAt = System.currentTimeMillis()
            )
        )
        audit.insert(
            AuditLogEntity(action = "UPDATE", entity = "book_person", entityId = id, detail = cleanName, actorEmployeeId = actorEmployeeId)
        )
    }

    /** أرشفة شخص: يخفى من القائمة وتبقى عملياته محفوظة. */
    suspend fun archivePerson(id: Long, actorEmployeeId: Long? = null) {
        requireAny(actorEmployeeId, StaffPermission.MANAGE_ACCOUNTS)
        val person = persons.get(id) ?: throw LedgerException("الشخص غير موجود")
        persons.update(person.copy(archived = true, updatedAt = System.currentTimeMillis()))
        audit.insert(
            AuditLogEntity(action = "ARCHIVE", entity = "book_person", entityId = id, detail = person.name, actorEmployeeId = actorEmployeeId)
        )
    }

    // ------------------------------------------------------------------ العمليات

    /**
     * تسجيل عملية جديدة. اتجاه التسديد يُحسب من الصافي الحالي ويُحفظ مع العملية
     * حتى لا يتغير معناها لاحقًا.
     */
    suspend fun addEntry(
        personId: Long,
        currencyId: Long,
        kind: BookEntryKind,
        amountMinor: Long,
        occurredAt: Long = System.currentTimeMillis(),
        details: String = "",
        actorEmployeeId: Long? = null
    ): Long = db.withTransaction {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        insertEntry(personId, currencyId, kind, amountMinor, occurredAt, details, opening = false, actorEmployeeId = actorEmployeeId)
    }

    /**
     * تحرير عملية: يُعاد حساب الجانب من صافي بقية العمليات (دون العملية المحرَّرة).
     * العمليات الأحدث لا يُعاد توجيهها تلقائيًا لأن اتجاهها محفوظ وقت تسجيلها.
     */
    suspend fun updateEntry(
        id: Long,
        kind: BookEntryKind,
        amountMinor: Long,
        occurredAt: Long,
        details: String,
        actorEmployeeId: Long? = null
    ) {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        val entry = entries.get(id) ?: throw LedgerException("العملية غير موجودة")
        if (entry.deletedAt != null) throw LedgerException("لا يمكن تعديل عملية محذوفة")
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        activeCurrency(entry.currencyId)
        val netWithout = entries.netOfExcluding(entry.personId, entry.currencyId, entry.id)
        entries.update(
            entry.copy(
                kind = kind.name,
                side = AccountsBookMath.sideOf(kind, netWithout).name,
                amountMinor = amountMinor,
                occurredAt = occurredAt,
                details = details.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        audit.insert(
            AuditLogEntity(action = "UPDATE", entity = "book_entry", entityId = id, detail = "${kind.name} $amountMinor", actorEmployeeId = actorEmployeeId)
        )
    }

    suspend fun deleteEntry(id: Long, actorEmployeeId: Long? = null) {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        val entry = entries.get(id) ?: throw LedgerException("العملية غير موجودة")
        entries.update(entry.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        audit.insert(
            AuditLogEntity(action = "DELETE", entity = "book_entry", entityId = id, detail = "${entry.kind} ${entry.amountMinor}", actorEmployeeId = actorEmployeeId)
        )
    }

    /**
     * استرجاع عملية محذوفة (زر «تراجع»). الحذف أرشفة ناعمة، فالاسترجاع يمسح
     * `deletedAt` ويعيد العملية إلى الرصيد الجاري كما كانت.
     */
    suspend fun restoreEntry(id: Long, actorEmployeeId: Long? = null) {
        requireAny(actorEmployeeId, StaffPermission.RECORD_SALE, StaffPermission.MANAGE_ACCOUNTS)
        val entry = entries.get(id) ?: throw LedgerException("العملية غير موجودة")
        if (entry.deletedAt == null) return
        db.withTransaction {
            entries.update(entry.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
            audit.insert(
                AuditLogEntity(
                    action = "UNDELETE",
                    entity = "book_entry",
                    entityId = id,
                    detail = "${entry.kind} ${entry.amountMinor}",
                    actorEmployeeId = actorEmployeeId
                )
            )
        }
    }

    /**
     * العملة التي تُقترح عند تسجيل عملية لهذا الشخص: عملته المعتادة إن كانت ما تزال
     * صالحة، وإلا العملة الافتراضية العامة (عملة الإعدادات ← عملة المحل ← «محلي»).
     */
    suspend fun currencyFor(person: BookPersonEntity, shopCurrencyCode: String?): CurrencyEntity? {
        person.currencyId?.let { id ->
            currencies.get(id)?.takeIf { !it.archived }?.let { return it }
        }
        return defaultCurrency(shopCurrencyCode)
    }

    /** يتحقق أن العملة المختارة موجودة وغير مؤرشفة؛ `null` مسموح ويعني «الافتراضية». */
    private suspend fun requireUsableCurrency(currencyId: Long?): Long? {
        if (currencyId == null) return null
        val currency = currencies.get(currencyId) ?: throw LedgerException("العملة غير موجودة")
        if (currency.archived) throw LedgerException("العملة مؤرشفة")
        return currency.id
    }

    /** سجل شخص كاملًا مرتبًا تصاعديًا (أساس الرصيد الجاري في الواجهة). */
    suspend fun statement(personId: Long): List<BookEntryEntity> = entries.listForPerson(personId)

    /** أرصدة شخص في كل عملة، محسوبة من عملياته. */
    suspend fun balancesFor(personId: Long): List<BookBalanceRow> {
        val byCurrency = entries.listForPerson(personId).groupBy { it.currencyId }
        return byCurrency.map { (currencyId, rows) ->
            val totals = AccountsBookMath.totals(
                rows.map { entry ->
                    BookEntryCore(
                        kind = kindOf(entry.kind),
                        side = sideOf(entry.side),
                        amountMinor = entry.amountMinor,
                        occurredAt = entry.occurredAt,
                        sequence = entry.id
                    )
                }
            )
            BookBalanceRow(
                personId = personId,
                currencyId = currencyId,
                creditMinor = totals.creditMinor,
                debtMinor = totals.debtMinor,
                settledMinor = totals.settledMinor
            )
        }.sortedBy { it.currencyId }
    }

    // ------------------------------------------------------------------ داخلي

    private suspend fun insertEntry(
        personId: Long,
        currencyId: Long,
        kind: BookEntryKind,
        amountMinor: Long,
        occurredAt: Long,
        details: String,
        opening: Boolean,
        actorEmployeeId: Long?
    ): Long {
        val person = persons.get(personId) ?: throw LedgerException("الشخص غير موجود")
        if (person.archived) throw LedgerException("لا يمكن التسجيل على شخص مؤرشف")
        val currency = activeCurrency(currencyId)
        if (amountMinor <= 0) throw LedgerException("أدخل مبلغًا أكبر من صفر")
        val netBefore = entries.netOf(personId, currencyId)
        val id = entries.insert(
            BookEntryEntity(
                personId = personId,
                currencyId = currency.id,
                kind = kind.name,
                side = AccountsBookMath.sideOf(kind, netBefore).name,
                amountMinor = amountMinor,
                occurredAt = occurredAt,
                details = details.trim(),
                opening = opening,
                createdByEmployeeId = actorEmployeeId
            )
        )
        audit.insert(
            AuditLogEntity(
                action = "CREATE",
                entity = "book_entry",
                entityId = id,
                detail = "${person.name} | ${kind.name} | $amountMinor | ${currency.code}",
                actorEmployeeId = actorEmployeeId
            )
        )
        return id
    }

    private suspend fun activeCurrency(id: Long): CurrencyEntity {
        val currency = currencies.get(id) ?: throw LedgerException("العملة غير موجودة")
        if (currency.archived) throw LedgerException("العملة مؤرشفة؛ اختر عملة أخرى")
        return currency
    }

    private fun kindOf(name: String): BookEntryKind =
        runCatching { BookEntryKind.valueOf(name) }.getOrElse { throw LedgerException("نوع العملية غير معروف") }

    private fun sideOf(name: String): BookSide =
        runCatching { BookSide.valueOf(name) }.getOrElse { throw LedgerException("اتجاه العملية غير معروف") }

    /** كود فريد: أحرف لاتينية وأرقام، ويُلحق رقم عند التكرار. */
    private suspend fun uniqueCode(requested: String): String {
        val base = requested.uppercase()
            .map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "CUR" }
            .take(MAX_CODE_LENGTH)
        var candidate = base
        var suffix = 2
        while (currencies.countCode(candidate, -1) > 0) {
            candidate = "${base.take(MAX_CODE_LENGTH - 4)}-$suffix"
            suffix++
            if (suffix > MAX_CODE_SUFFIX) throw LedgerException("تعذر إنشاء كود فريد للعملة")
        }
        return candidate
    }

    private suspend fun requireActor(actorId: Long?, permission: StaffPermission) {
        staffAccess.requirePermission(actorId, permission)
    }

    private suspend fun requireAny(actorId: Long?, vararg permissions: StaffPermission) {
        if (actorId == null) return
        for (permission in permissions) {
            if (staffAccess.can(actorId, permission)) return
        }
        throw LedgerException("ليست لديك صلاحية لهذه العملية")
    }

    private companion object {
        const val DEFAULT_FRACTION_DIGITS = 2
        const val MAX_FRACTION_DIGITS = 4
        const val MAX_CODE_LENGTH = 12
        const val MAX_CODE_SUFFIX = 500
    }
}
