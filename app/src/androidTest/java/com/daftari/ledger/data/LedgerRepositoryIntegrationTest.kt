package com.daftari.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.PartyKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبارات تكاملية لحالات البيانات المحاسبية الحساسة. تستخدم Room داخل الذاكرة
 * كي تختبر المعاملات والاستعلامات الحقيقية، وليس مجرد منشئ القيود الصرف.
 */
@RunWith(AndroidJUnit4::class)
class LedgerRepositoryIntegrationTest {
    private lateinit var db: AppDb
    private lateinit var repo: LedgerRepository
    private lateinit var shopId: Long

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        shopId = repo.createShop("محل الاختبار")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun softDeletedCreditSaleIsExcludedFromPartyBalance() = runBlocking {
        val partyId = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل")
        val documentId = repo.postDocument(
            shopId, DocType.SALE, 12_500L, System.currentTimeMillis(),
            partyId = partyId, paymentMethod = "CREDIT"
        )
        assertEquals(12_500L, repo.parties.get(partyId)?.cachedBalanceMinor)

        repo.softDeleteDocument(documentId)

        assertEquals(0L, repo.parties.get(partyId)?.cachedBalanceMinor)
        assertEquals(0, repo.documents.listParty(partyId).size)
        assertEquals(0L, repo.journal.partyNetDebit(partyId))
    }

    @Test
    fun closePartyArchivesHistoryAndKeepsBalanceAsOpeningEntry() = runBlocking {
        val partyId = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل")
        repo.postDocument(
            shopId, DocType.SALE, 7_500L, System.currentTimeMillis(),
            partyId = partyId, paymentMethod = "CREDIT"
        )

        repo.closePartyAccount(partyId)

        val party = requireNotNull(repo.parties.get(partyId))
        val remaining = repo.documents.listParty(partyId)
        assertEquals(7_500L, party.openingMinor)
        assertEquals(7_500L, party.cachedBalanceMinor)
        assertEquals(1, remaining.size)
        assertEquals(DocType.OPENING.name, remaining.single().type)
        assertEquals(7_500L, repo.journal.partyNetDebit(partyId))
    }

    @Test
    fun invalidPostRollsBackDocumentInsertion() = runBlocking {
        val before = repo.documents.listPeriod(shopId, 0L, Long.MAX_VALUE).size

        assertThrows(LedgerException::class.java) {
            runBlocking {
                repo.postDocument(
                    shopId, DocType.COLLECT, 1_000L, System.currentTimeMillis(), partyId = null
                )
            }
        }

        assertEquals(before, repo.documents.listPeriod(shopId, 0L, Long.MAX_VALUE).size)
    }
}
