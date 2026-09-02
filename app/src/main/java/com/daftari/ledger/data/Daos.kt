package com.daftari.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops WHERE archived = 0 ORDER BY name")
    fun observeActive(): Flow<List<ShopEntity>>
    @Query("SELECT * FROM shops WHERE archived = 0 ORDER BY name")
    suspend fun listActive(): List<ShopEntity>
    @Query("SELECT * FROM shops WHERE id = :id")
    suspend fun get(id: Long): ShopEntity?
    @Insert suspend fun insert(s: ShopEntity): Long
    @Update suspend fun update(s: ShopEntity)
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL ORDER BY name")
    fun observe(shopId: Long, kind: String): Flow<List<PartyEntity>>
    /** بحث FTS سريع؛ يُبنى الاستعلام ومعاملاته في [LedgerRepository.searchParties]. */
    @RawQuery
    suspend fun searchFts(query: SupportSQLiteQuery): List<PartyEntity>
    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: Long): PartyEntity?
    @Insert suspend fun insert(p: PartyEntity): Long
    @Update suspend fun update(p: PartyEntity)
    @Query("SELECT COUNT(*) FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL")
    suspend fun count(shopId: Long, kind: String): Int
    @Query("SELECT COALESCE(SUM(CASE WHEN cachedBalanceMinor > 0 THEN cachedBalanceMinor ELSE 0 END),0) FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL")
    suspend fun sumPositiveBalance(shopId: Long, kind: String): Long
    @Query("SELECT COALESCE(SUM(CASE WHEN cachedBalanceMinor < 0 THEN 0 - cachedBalanceMinor ELSE 0 END),0) FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL")
    suspend fun sumNegativeBalanceAbs(shopId: Long, kind: String): Long
    @Query("SELECT * FROM parties WHERE shopId = :shopId AND deletedAt IS NULL")
    suspend fun listAll(shopId: Long): List<PartyEntity>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE shopId = :shopId")
    suspend fun list(shopId: Long): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE shopId = :shopId AND code = :code LIMIT 1")
    suspend fun byCode(shopId: Long, code: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE shopId = :shopId AND isCashLike = 1")
    suspend fun cashLike(shopId: Long): List<AccountEntity>
    @Insert suspend fun insert(a: AccountEntity): Long
    @Insert suspend fun insertAll(a: List<AccountEntity>)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE shopId = :shopId AND deletedAt IS NULL ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(shopId: Long, limit: Int, offset: Int): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE shopId = :shopId AND deletedAt IS NULL AND occurredAt BETWEEN :from AND :to ORDER BY occurredAt DESC")
    fun observePeriod(shopId: Long, from: Long, to: Long): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE shopId = :shopId AND deletedAt IS NULL AND occurredAt BETWEEN :from AND :to")
    suspend fun listPeriod(shopId: Long, from: Long, to: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE shopId = :shopId AND deletedAt IS NULL AND type IN ('SALE','EXPENSE') AND occurredAt BETWEEN :from AND :to ORDER BY occurredAt DESC, id DESC")
    suspend fun listSalesBookPeriod(shopId: Long, from: Long, to: Long): List<DocumentEntity>

    @Query(
        """
        SELECT d.* FROM documents d
        LEFT JOIN parties p ON p.id = d.partyId
        LEFT JOIN categories c ON c.id = d.categoryId
        WHERE d.shopId = :shopId
          AND d.deletedAt IS NULL
          AND d.type IN ('SALE','EXPENSE')
          AND d.occurredAt BETWEEN :from AND :to
          AND (:entryType IS NULL OR d.type = :entryType)
          AND (:paymentMethod IS NULL OR d.paymentMethod = :paymentMethod)
          AND (:categoryId IS NULL OR d.categoryId = :categoryId)
          AND (
              :query = '' OR d.notes LIKE '%' || :query || '%' OR
              d.docNumber LIKE '%' || :query || '%' OR
              COALESCE(p.name, '') LIKE '%' || :query || '%' OR
              COALESCE(c.name, '') LIKE '%' || :query || '%' OR
              (:amountMinor IS NOT NULL AND d.amountMinor = :amountMinor)
          )
        ORDER BY d.occurredAt DESC, d.id DESC
        LIMIT :limit
        """
    )
    suspend fun searchSalesBook(
        shopId: Long,
        from: Long,
        to: Long,
        query: String,
        amountMinor: Long?,
        entryType: String?,
        paymentMethod: String?,
        categoryId: Long?,
        limit: Int = 500
    ): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE partyId = :partyId AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeParty(partyId: Long): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?
    @Insert suspend fun insert(d: DocumentEntity): Long
    @Update suspend fun update(d: DocumentEntity)
    @Query("SELECT COUNT(*) FROM documents WHERE shopId = :shopId AND docNumber = :num AND deletedAt IS NULL AND (:partyId IS NULL OR partyId = :partyId) AND id != :exceptId")
    suspend fun countDocNumber(shopId: Long, num: String, partyId: Long?, exceptId: Long): Int
    @Query("""
        SELECT * FROM documents WHERE shopId = :shopId AND deletedAt IS NULL AND (
          notes LIKE '%' || :q || '%' OR docNumber LIKE '%' || :q || '%' 
        ) ORDER BY occurredAt DESC LIMIT 100
    """)
    suspend fun search(shopId: Long, q: String): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE partyId = :partyId AND deletedAt IS NULL ORDER BY occurredAt ASC")
    suspend fun listParty(partyId: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE partyId = :partyId AND deletedAt IS NULL ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun recentParty(partyId: Long, limit: Int): List<DocumentEntity>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'SALE' THEN amountMinor ELSE 0 END), 0) AS sales,
            COALESCE(SUM(CASE WHEN type = 'PURCHASE' THEN amountMinor ELSE 0 END), 0) AS purchases,
            COALESCE(SUM(CASE WHEN type = 'COLLECT' THEN amountMinor ELSE 0 END), 0) AS collections,
            COALESCE(SUM(CASE WHEN type = 'PAY' THEN amountMinor ELSE 0 END), 0) AS payments
        FROM documents
        WHERE partyId = :partyId AND deletedAt IS NULL
        """
    )
    suspend fun partyStats(partyId: Long): PartyStatsAggregate

    @Query(
        """
        SELECT p.*,
               d.amountMinor AS invoiceAmountMinor,
               d.occurredAt AS invoiceOccurredAt
        FROM parties p
        LEFT JOIN documents d
          ON d.partyId = p.id
         AND d.deletedAt IS NULL
         AND d.type IN ('SALE', 'OPENING')
        WHERE p.shopId = :shopId
          AND p.kind = :kind
          AND p.deletedAt IS NULL
          AND p.cachedBalanceMinor > 0
        ORDER BY p.id, d.occurredAt
        """
    )
    suspend fun agingDocuments(shopId: Long, kind: String): List<AgingDocumentRow>

    @Query(
        """
        SELECT p.*, MIN(d.dueAt) AS lastDate
        FROM parties p
        JOIN documents d
          ON d.partyId = p.id
         AND d.deletedAt IS NULL
         AND d.type = 'SALE'
         AND d.paymentMethod = 'CREDIT'
         AND d.dueAt IS NOT NULL
         AND d.dueAt <= :now
        WHERE p.shopId = :shopId
          AND p.kind = 'CUSTOMER'
          AND p.deletedAt IS NULL
          AND p.cachedBalanceMinor > 0
        GROUP BY p.id
        """
    )
    suspend fun overdueCustomers(shopId: Long, now: Long): List<PartyLastActivityRow>

    @Query(
        """
        SELECT d.*,
               COALESCE(SUM(j.debitMinor - j.creditMinor), 0) AS netDebitDelta
        FROM documents d
        JOIN journal_lines j ON j.documentId = d.id AND j.partyId = :partyId
        WHERE d.partyId = :partyId AND d.deletedAt IS NULL
        GROUP BY d.id
        ORDER BY d.occurredAt, d.id
        """
    )
    suspend fun statementRows(partyId: Long): List<PartyStatementRow>

    @Query(
        """
        SELECT p.id AS partyId, p.name AS partyName,
               COUNT(d.id) AS documentCount,
               COALESCE(SUM(d.amountMinor), 0) AS totalMinor,
               MIN(d.dueAt) AS oldestDueAt
        FROM parties p
        JOIN documents d ON d.partyId = p.id
        WHERE d.deletedAt IS NULL
          AND d.type = 'SALE'
          AND d.paymentMethod = 'CREDIT'
          AND d.dueAt IS NOT NULL
          AND d.dueAt <= :now
          AND p.shopId = :shopId
          AND d.shopId = :shopId
          AND p.deletedAt IS NULL
          AND p.cachedBalanceMinor > 0
        GROUP BY p.id
        ORDER BY oldestDueAt
        """
    )
    suspend fun overdueParties(shopId: Long, now: Long): List<OverduePartyRow>

    @Query(
        """
        SELECT d.categoryId AS categoryId,
               COALESCE(c.name, :uncategorized) AS categoryName,
               COALESCE(SUM(d.amountMinor), 0) AS totalMinor
        FROM documents d
        LEFT JOIN categories c ON c.id = d.categoryId
        WHERE d.shopId = :shopId
          AND d.deletedAt IS NULL
          AND d.type = :type
          AND d.occurredAt BETWEEN :from AND :to
          AND (:employeeId IS NULL OR d.employeeId = :employeeId)
        GROUP BY d.categoryId, c.name
        ORDER BY totalMinor DESC
        """
    )
    suspend fun totalsByCategory(
        shopId: Long,
        type: String,
        from: Long,
        to: Long,
        uncategorized: String,
        employeeId: Long? = null
    ): List<CategoryTotal>

    @Query("""
        SELECT
          COALESCE(SUM(CASE WHEN type = 'SALE' AND paymentMethod = 'CASH' THEN amountMinor ELSE 0 END), 0) AS cashSalesMinor,
          COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND paymentMethod = 'CASH' THEN amountMinor ELSE 0 END), 0) AS cashOutflowsMinor
        FROM documents WHERE shiftId = :shiftId AND deletedAt IS NULL
    """)
    suspend fun shiftCashSummary(shiftId: Long): ShiftCashSummary
}

@Dao
interface JournalDao {
    @Insert suspend fun insertAll(lines: List<JournalLineEntity>)
    @Query("DELETE FROM journal_lines WHERE documentId = :docId")
    suspend fun deleteForDoc(docId: Long)
    @Query("SELECT * FROM journal_lines WHERE documentId = :docId")
    suspend fun forDoc(docId: Long): List<JournalLineEntity>
    @Query("""
        SELECT COALESCE(SUM(debitMinor),0) - COALESCE(SUM(creditMinor),0) FROM journal_lines
        WHERE partyId = :partyId AND documentId IN (SELECT id FROM documents WHERE deletedAt IS NULL)
    """)
    suspend fun partyNetDebit(partyId: Long): Long
    @Query("""
        SELECT * FROM journal_lines WHERE accountId = :accountId
        AND documentId IN (SELECT id FROM documents WHERE shopId = :shopId AND deletedAt IS NULL)
        ORDER BY id
    """)
    suspend fun ledger(shopId: Long, accountId: Long): List<JournalLineEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE shopId = :shopId AND archived = 0 ORDER BY kind, name")
    fun observe(shopId: Long): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE shopId = :shopId AND archived = 0 ORDER BY kind, name")
    suspend fun list(shopId: Long): List<CategoryEntity>
    @Insert suspend fun insert(category: CategoryEntity): Long
    @Update suspend fun update(category: CategoryEntity)
}

@Dao
interface EmployeeDao {
    @Query("""
        SELECT e.* FROM employees e
        JOIN employee_shops es ON es.employeeId = e.id
        WHERE es.shopId = :shopId AND es.active = 1
        ORDER BY CASE e.status WHEN 'ACTIVE' THEN 0 WHEN 'LEAVE' THEN 1 ELSE 2 END, e.name
    """)
    fun observeForShop(shopId: Long): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun get(id: Long): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY name LIMIT 100")
    suspend fun search(query: String): List<EmployeeEntity>

    @Query("SELECT COUNT(*) FROM employees WHERE username = :username AND username != '' AND id != :exceptId")
    suspend fun countUsername(username: String, exceptId: Long = -1): Int

    @Insert suspend fun insert(employee: EmployeeEntity): Long
    @Update suspend fun update(employee: EmployeeEntity)

    @Query("""
        SELECT e.id AS employeeId, e.name AS employeeName, e.jobTitle, e.role, e.status,
               COALESCE(SUM(d.amountMinor), 0) AS salesMinor,
               COUNT(d.id) AS transactionCount,
               COALESCE(SUM(CASE WHEN d.paymentMethod = 'CASH' THEN d.amountMinor ELSE 0 END), 0) AS cashMinor,
               COALESCE(SUM(CASE WHEN d.paymentMethod = 'BANK' THEN d.amountMinor ELSE 0 END), 0) AS bankMinor,
               COALESCE(SUM(CASE WHEN d.paymentMethod = 'CARD' THEN d.amountMinor ELSE 0 END), 0) AS cardMinor,
               COALESCE(SUM(CASE WHEN d.paymentMethod = 'WALLET' THEN d.amountMinor ELSE 0 END), 0) AS walletMinor,
               COALESCE(SUM(CASE WHEN d.paymentMethod = 'CREDIT' THEN d.amountMinor ELSE 0 END), 0) AS creditMinor
        FROM employees e
        JOIN employee_shops es ON es.employeeId = e.id AND es.shopId = :shopId AND es.active = 1
        LEFT JOIN documents d ON d.employeeId = e.id AND d.shopId = :shopId
             AND d.type = 'SALE' AND d.deletedAt IS NULL AND d.occurredAt BETWEEN :from AND :to
        GROUP BY e.id
        ORDER BY salesMinor DESC, transactionCount DESC, e.name
    """)
    suspend fun performance(shopId: Long, from: Long, to: Long): List<EmployeePerformanceRow>

    @Query("SELECT * FROM documents WHERE employeeId = :employeeId AND type = 'SALE' AND deletedAt IS NULL AND occurredAt BETWEEN :from AND :to ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun sales(employeeId: Long, from: Long, to: Long, limit: Int = 500): List<DocumentEntity>
}

@Dao
interface EmployeeShopDao {
    @Insert suspend fun insert(link: EmployeeShopEntity): Long
    @Update suspend fun update(link: EmployeeShopEntity)
    @Query("SELECT * FROM employee_shops WHERE employeeId = :employeeId ORDER BY assignedAt")
    suspend fun listForEmployee(employeeId: Long): List<EmployeeShopEntity>
    @Query("SELECT * FROM employee_shops WHERE employeeId = :employeeId AND shopId = :shopId AND active = 1 ORDER BY assignedAt DESC LIMIT 1")
    suspend fun getActive(employeeId: Long, shopId: Long): EmployeeShopEntity?
}

@Dao
interface EmployeeShiftDao {
    @Insert suspend fun insert(shift: EmployeeShiftEntity): Long
    @Update suspend fun update(shift: EmployeeShiftEntity)
    @Query("SELECT * FROM employee_shifts WHERE id = :id")
    suspend fun get(id: Long): EmployeeShiftEntity?
    @Query("SELECT * FROM employee_shifts WHERE shopId = :shopId AND employeeId = :employeeId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun openShift(shopId: Long, employeeId: Long): EmployeeShiftEntity?
    @Query("SELECT * FROM employee_shifts WHERE shopId = :shopId AND openedAt BETWEEN :from AND :to ORDER BY openedAt DESC")
    suspend fun listPeriod(shopId: Long, from: Long, to: Long): List<EmployeeShiftEntity>
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(a: AuditLogEntity)
    @Query("SELECT * FROM audit_logs ORDER BY at DESC LIMIT 200")
    fun observe(): Flow<List<AuditLogEntity>>
    @Query("SELECT * FROM audit_logs WHERE actorEmployeeId = :employeeId ORDER BY at DESC LIMIT :limit")
    suspend fun byActor(employeeId: Long, limit: Int = 200): List<AuditLogEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): SettingsEntity?
    @Insert suspend fun insert(s: SettingsEntity)
    @Update suspend fun update(s: SettingsEntity)
}

@Dao
interface ClosingDao {
    @Insert suspend fun insert(c: DailyClosingEntity): Long
    @Query("SELECT * FROM daily_closings WHERE shopId = :shopId ORDER BY dayStart DESC LIMIT 30")
    fun observe(shopId: Long): Flow<List<DailyClosingEntity>>
    @Query("SELECT * FROM daily_closings WHERE shopId = :shopId AND dayStart = :day LIMIT 1")
    suspend fun byDay(shopId: Long, day: Long): DailyClosingEntity?
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE shopId = :shopId AND archived = 0 ORDER BY name")
    fun observe(shopId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE shopId = :shopId AND archived = 0 ORDER BY name")
    suspend fun list(shopId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE shopId = :shopId AND sku = :sku AND sku != '' AND id != :exceptId")
    suspend fun countSku(shopId: Long, sku: String, exceptId: Long = -1): Int

    @Query("SELECT COUNT(*) FROM items WHERE shopId = :shopId AND archived = 0 AND trackStock = 1 AND qtyMilli <= reorderQtyMilli")
    suspend fun lowStockCount(shopId: Long): Int

    @Insert suspend fun insert(item: ItemEntity): Long
    @Update suspend fun update(item: ItemEntity)
}

@Dao
interface DocumentLineDao {
    @Insert suspend fun insertAll(lines: List<DocumentLineEntity>)
    @Query("DELETE FROM document_lines WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)
    @Query("SELECT * FROM document_lines WHERE documentId = :documentId ORDER BY id")
    suspend fun forDocument(documentId: Long): List<DocumentLineEntity>
}

@Dao
interface DailyBookDao {
    @Query("SELECT * FROM daily_books WHERE shopId = :shopId AND dayStart = :dayStart LIMIT 1")
    suspend fun get(shopId: Long, dayStart: Long): DailyBookEntity?

    @Query("SELECT * FROM daily_books WHERE shopId = :shopId AND dayStart BETWEEN :from AND :to ORDER BY dayStart")
    suspend fun listPeriod(shopId: Long, from: Long, to: Long): List<DailyBookEntity>

    @Upsert
    suspend fun upsert(book: DailyBookEntity): Long
}

/** عملات دفتر الحسابات — رسمية أو من إنشاء المستخدم. */
@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies ORDER BY id")
    suspend fun list(): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE id = :id")
    suspend fun get(id: Long): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): CurrencyEntity?

    @Query("SELECT COUNT(*) FROM currencies WHERE code = :code AND id != :exceptId")
    suspend fun countCode(code: String, exceptId: Long): Int

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun count(): Int

    /** هل استُخدمت العملة في عمليات فعلية؟ يمنع الأرشفة المفاجئة لعملة مستعملة. */
    @Query("SELECT COUNT(*) FROM book_entries WHERE currencyId = :id AND deletedAt IS NULL")
    suspend fun usageCount(id: Long): Int

    @Insert suspend fun insert(currency: CurrencyEntity): Long
    @Update suspend fun update(currency: CurrencyEntity)
}

@Dao
interface BookPersonDao {
    @Query("SELECT * FROM book_persons WHERE shopId = :shopId AND archived = 0 ORDER BY name")
    fun observe(shopId: Long): Flow<List<BookPersonEntity>>

    @Query("SELECT * FROM book_persons WHERE shopId = :shopId AND archived = 0 ORDER BY name")
    suspend fun list(shopId: Long): List<BookPersonEntity>

    @Query("SELECT * FROM book_persons WHERE id = :id")
    suspend fun get(id: Long): BookPersonEntity?

    @Insert suspend fun insert(person: BookPersonEntity): Long
    @Update suspend fun update(person: BookPersonEntity)
}

/**
 * عمليات دفتر الحسابات. الأرصدة تُجمع في SQL لكل (شخص، عملة) ولا تُخزَّن مطلقًا،
 * فلا يمكن أن ينحرف رصيد مخزّن عن عملياته.
 */
@Dao
interface BookEntryDao {
    @Query(
        """
        SELECT e.personId AS personId,
               e.currencyId AS currencyId,
               COALESCE(SUM(CASE WHEN e.side = 'LE' THEN e.amountMinor ELSE 0 END), 0) AS creditMinor,
               COALESCE(SUM(CASE WHEN e.side = 'DEBT' THEN e.amountMinor ELSE 0 END), 0) AS debtMinor,
               COALESCE(SUM(CASE WHEN e.kind = 'SETTLEMENT' THEN e.amountMinor ELSE 0 END), 0) AS settledMinor
        FROM book_entries e
        JOIN book_persons p ON p.id = e.personId
        WHERE e.deletedAt IS NULL AND p.shopId = :shopId
        GROUP BY e.personId, e.currencyId
        """
    )
    fun observeBalances(shopId: Long): Flow<List<BookBalanceRow>>

    /** صافي شخص في عملة: موجب = عليه، سالب = له. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN side = 'DEBT' THEN amountMinor ELSE 0 - amountMinor END), 0)
        FROM book_entries
        WHERE personId = :personId AND currencyId = :currencyId AND deletedAt IS NULL
        """
    )
    suspend fun netOf(personId: Long, currencyId: Long): Long

    /** الصافي دون عملية محددة؛ يُستخدم عند تحرير عملية قديمة. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN side = 'DEBT' THEN amountMinor ELSE 0 - amountMinor END), 0)
        FROM book_entries
        WHERE personId = :personId AND currencyId = :currencyId AND deletedAt IS NULL AND id != :exceptId
        """
    )
    suspend fun netOfExcluding(personId: Long, currencyId: Long, exceptId: Long): Long

    /** سجل شخص كاملًا بالترتيب الزمني التصاعدي (أساس الرصيد الجاري). */
    @Query("SELECT * FROM book_entries WHERE personId = :personId AND deletedAt IS NULL ORDER BY occurredAt ASC, id ASC")
    fun observeForPerson(personId: Long): Flow<List<BookEntryEntity>>

    @Query("SELECT * FROM book_entries WHERE personId = :personId AND deletedAt IS NULL ORDER BY occurredAt ASC, id ASC")
    suspend fun listForPerson(personId: Long): List<BookEntryEntity>

    @Query("SELECT * FROM book_entries WHERE id = :id")
    suspend fun get(id: Long): BookEntryEntity?

    @Query("SELECT personId AS personId, MAX(occurredAt) AS lastAt FROM book_entries WHERE deletedAt IS NULL GROUP BY personId")
    fun observeLastActivity(): Flow<List<BookActivityRow>>

    @Insert suspend fun insert(entry: BookEntryEntity): Long
    @Update suspend fun update(entry: BookEntryEntity)
}


