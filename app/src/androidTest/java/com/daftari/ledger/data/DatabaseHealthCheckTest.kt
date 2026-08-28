package com.daftari.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseHealthCheckTest {
    private lateinit var db: AppDb
    private lateinit var repo: LedgerRepository
    private var shopId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDb::class.java
        ).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
        repo.ensureSettings()
        shopId = repo.createShop("فحص السلامة")
        // فحص سلامة v9 يتوقع وجود عملات، وهي في التطبيق تُدرج عند فتح القاعدة.
        AccountsBookRepository(db).ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun healthyDatabasePasses() = runBlocking {
        val customer = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل")
        repo.postDocument(shopId, DocType.SALE, 1_000, System.currentTimeMillis(), partyId = customer, paymentMethod = "CREDIT")

        val report = DatabaseHealthCheck.inspect(db)

        assertTrue(report.issues.joinToString { it.code }, report.ok)
    }

    @Test
    fun detectsCreditSaleWithoutCustomer() {
        insertRawDocument(type = "SALE", paymentMethod = "CREDIT", partyId = null)

        val report = DatabaseHealthCheck.inspect(db)

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "credit_sale_without_customer" })
    }

    @Test
    fun detectsUnbalancedDocument() = runBlocking {
        val customer = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل")
        val documentId = repo.postDocument(shopId, DocType.SALE, 1_000, System.currentTimeMillis(), partyId = customer, paymentMethod = "CREDIT")
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM journal_lines WHERE documentId = ? AND id = (SELECT id FROM journal_lines WHERE documentId = ? LIMIT 1)",
            arrayOf(documentId, documentId)
        )

        val report = DatabaseHealthCheck.inspect(db)

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "unbalanced_documents" })
    }

    @Test
    fun detectsCachedBalanceMismatch() = runBlocking {
        val customer = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل")
        repo.postDocument(shopId, DocType.SALE, 1_000, System.currentTimeMillis(), partyId = customer, paymentMethod = "CREDIT")
        db.openHelper.writableDatabase.execSQL("UPDATE parties SET cachedBalanceMinor = 0 WHERE id = ?", arrayOf(customer))

        val report = DatabaseHealthCheck.inspect(db)

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "cached_balance_mismatch" })
    }

    @Test
    fun detectsWrongPartyKind() = runBlocking {
        val supplier = repo.addParty(shopId, PartyKind.SUPPLIER, "مورد")
        insertRawDocument(type = "SALE", paymentMethod = "CREDIT", partyId = supplier)

        val report = DatabaseHealthCheck.inspect(db)

        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "wrong_party_kind" })
    }

    private fun insertRawDocument(type: String, paymentMethod: String, partyId: Long?) {
        val now = System.currentTimeMillis()
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO documents(
                shopId, type, partyId, amountMinor, occurredAt, docNumber, notes,
                paymentMethod, createdAt, updatedAt
            ) VALUES (?, ?, ?, 1000, ?, '', '', ?, ?, ?)
            """.trimIndent(),
            arrayOf(shopId, type, partyId, now, paymentMethod, now, now)
        )
    }
}
