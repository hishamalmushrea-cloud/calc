package com.daftari.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
    @Query("SELECT * FROM parties WHERE shopId = :shopId AND deletedAt IS NULL AND name LIKE '%' || :q || '%' ORDER BY name LIMIT 50")
    suspend fun search(shopId: Long, q: String): List<PartyEntity>
    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: Long): PartyEntity?
    @Insert suspend fun insert(p: PartyEntity): Long
    @Update suspend fun update(p: PartyEntity)
    @Query("SELECT COUNT(*) FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL")
    suspend fun count(shopId: Long, kind: String): Int
    @Query("SELECT COALESCE(SUM(cachedBalanceMinor),0) FROM parties WHERE shopId = :shopId AND kind = :kind AND deletedAt IS NULL")
    suspend fun sumBalance(shopId: Long, kind: String): Long
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
interface AuditDao {
    @Insert suspend fun insert(a: AuditLogEntity)
    @Query("SELECT * FROM audit_logs ORDER BY at DESC LIMIT 200")
    fun observe(): Flow<List<AuditLogEntity>>
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


