package com.daftari.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.domain.PartyKind
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesBookIntegrationTest {
    private lateinit var db: AppDb
    private lateinit var repo: LedgerRepository
    private var shopId = 0L
    private var dayStart = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDb::class.java
        ).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
        repo.ensureSettings()
        shopId = repo.createShop("دفتر الاختبار")
        dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @After fun tearDown() = db.close()

    @Test
    fun dailySummarySeparatesSalesOutflowsAndCreditFromCash() = runBlocking {
        repo.postSalesBookEntry(shopId, SalesBookEntryInput("SALE", 10_000, dayStart + 9_000, paymentMethod = "CASH"))
        val customer = repo.addParty(shopId, PartyKind.CUSTOMER, "عميل آجل")
        repo.postSalesBookEntry(shopId, SalesBookEntryInput("SALE", 5_000, dayStart + 10_000, paymentMethod = "CREDIT", partyId = customer))
        repo.postSalesBookEntry(shopId, SalesBookEntryInput("EXPENSE", 2_000, dayStart + 11_000, paymentMethod = "CASH"))

        val day = repo.salesBookDays(shopId, dayStart, dayStart + 86_399_999).single()
        assertEquals(15_000L, day.salesMinor)
        assertEquals(2_000L, day.outflowsMinor)
        assertEquals(8_000L, day.netCashMovementMinor)
        assertEquals(3, day.transactionCount)
    }

    @Test
    fun closedDayRequiresExplicitReopenBeforeChanges() = runBlocking {
        repo.closeSalesDay(shopId, dayStart)
        assertThrows(LedgerException::class.java) {
            runBlocking {
                repo.postSalesBookEntry(shopId, SalesBookEntryInput("SALE", 100, dayStart + 1000))
            }
        }
        repo.reopenSalesDay(shopId, dayStart)
        repo.postSalesBookEntry(shopId, SalesBookEntryInput("SALE", 100, dayStart + 1000))
        assertEquals(1, repo.salesBookEntries(shopId, dayStart).size)
    }

    @Test
    fun shopsAndSearchResultsRemainIsolated() = runBlocking {
        val otherShop = repo.createShop("فرع آخر")
        repo.postSalesBookEntry(shopId, SalesBookEntryInput("SALE", 500, dayStart + 1000, notes = "عطر"))
        repo.postSalesBookEntry(otherShop, SalesBookEntryInput("SALE", 700, dayStart + 2000, notes = "عطر"))

        val results = repo.searchSalesBook(shopId, dayStart, dayStart + 86_399_999, "عطر", null, null, null)
        assertEquals(1, results.size)
        assertEquals(shopId, results.single().shopId)
    }
}
