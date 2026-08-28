package com.daftari.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.domain.BookEntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبارات تكاملية لدفتر الحسابات على قاعدة بيانات حقيقية في الذاكرة:
 * الأرصدة لكل عملة، اتجاه التسديد، الرصيد الجاري، العملات المخصصة، والأرشفة الناعمة.
 */
@RunWith(AndroidJUnit4::class)
class AccountsBookIntegrationTest {

    private lateinit var db: AppDb
    private lateinit var book: AccountsBookRepository
    private lateinit var ledger: LedgerRepository
    private var shopId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java)
            .allowMainThreadQueries()
            .build()
        book = AccountsBookRepository(db)
        ledger = LedgerRepository(db)
        ledger.ensureSettings()
        shopId = ledger.createShop("محل الاختبار")
        // في التطبيق تُدرج البذور عبر RoomDatabase.Callback داخل AppDb.get،
        // وقاعدة الاختبار تُبنى مباشرة في الذاكرة فلا يمرّ عليها ذلك النداء.
        book.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedCurrenciesCoverTheRequestedOnes() = runBlocking {
        val codes = book.listCurrencies().map { it.code }

        assertTrue(codes.toString(), codes.containsAll(listOf("LOCAL", "YER", "SAR", "USD")))
        assertNotNull(book.defaultCurrency("SAR"))
        // العملة المحلية بلا رمز كما طلب المستخدم.
        assertEquals("", book.listCurrencies().first { it.code == "LOCAL" }.symbol)
    }

    /** يبني القاعدة بنفس طريقة التطبيق ليتأكد أن البذور تُدرج فعلًا عند أول فتح. */
    @Test
    fun appDatabaseSeedsCurrenciesOnFirstOpen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(APP_DB_NAME)
        try {
            val appDb = AppDb.get(context)
            val codes = runBlocking { appDb.currencies().list() }.map { it.code }

            assertTrue(codes.toString(), codes.containsAll(listOf("LOCAL", "YER", "SAR", "USD")))
        } finally {
            AppDb.invalidate(context)
            context.deleteDatabase(APP_DB_NAME)
        }
    }

    @Test
    fun balancesFollowTheUserExample() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "محمد")
        book.addEntry(person, local.id, BookEntryKind.LE, 2_000L)
        book.addEntry(person, local.id, BookEntryKind.DEBT, 5_000L)

        val balance = book.balancesFor(person).single()

        assertEquals(2_000L, balance.creditMinor)
        assertEquals(5_000L, balance.debtMinor)
        assertEquals(3_000L, balance.netMinor)
    }

    @Test
    fun settlementShrinksTheDebtSide() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "أحمد")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 5_000L)
        book.addEntry(person, local.id, BookEntryKind.SETTLEMENT, 2_000L)

        val balance = book.balancesFor(person).single()

        assertEquals(5_000L, balance.debtMinor)
        assertEquals(2_000L, balance.creditMinor)
        assertEquals(2_000L, balance.settledMinor)
        assertEquals(3_000L, balance.netMinor)
    }

    @Test
    fun settlementWhenYouOweHimReducesHisCredit() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "سالم")
        book.addEntry(person, local.id, BookEntryKind.LE, 4_000L)
        val settlement = book.addEntry(person, local.id, BookEntryKind.SETTLEMENT, 1_500L)

        val entry = book.getEntry(settlement)!!
        val balance = book.balancesFor(person).single()

        assertEquals("عليه", "DEBT", entry.side)
        assertEquals(-2_500L, balance.netMinor)
    }

    @Test
    fun openingBalanceIsStoredAsAnEntry() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(
            shopId,
            "خالد",
            opening = BookOpeningBalance(local.id, BookEntryKind.DEBT.name, 1_000L)
        )

        val entries = book.statement(person)

        assertEquals(1, entries.size)
        assertTrue(entries.single().opening)
        assertEquals(1_000L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun balancesAreTrackedPerCurrency() = runBlocking {
        val riyal = book.listCurrencies().first { it.code == "SAR" }
        val dollar = book.addCurrency("دولار", "$", 2, "USD2")
        val person = book.addPerson(shopId, "ياسر")
        book.addEntry(person, riyal.id, BookEntryKind.DEBT, 300L)
        book.addEntry(person, dollar, BookEntryKind.LE, 100L)

        val balances = book.balancesFor(person).associateBy { it.currencyId }

        assertEquals(2, balances.size)
        assertEquals(300L, balances.getValue(riyal.id).netMinor)
        assertEquals(-100L, balances.getValue(dollar).netMinor)
    }

    @Test
    fun customCurrencyWithoutSymbolIsSupported() = runBlocking {
        val id = book.addCurrency("محلي ٢", "", 0, "")
        val currency = db.currencies().get(id)!!

        assertEquals("", currency.symbol)
        assertEquals(0, currency.fractionDigits)
        assertTrue(currency.code.isNotBlank())
    }

    @Test
    fun duplicateCurrencyCodeGetsUniqueSuffix() = runBlocking {
        book.addCurrency("يمني", "﷼", 2, "YER")
        val second = db.currencies().get(book.addCurrency("يمني قديم", "﷼", 2, "YER"))!!

        assertFalse(second.code == "YER")
        assertTrue(second.code.startsWith("YER"))
    }

    @Test
    fun editingAnEntryRecomputesItsSide() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "ماجد")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 5_000L)
        val entry = book.addEntry(person, local.id, BookEntryKind.SETTLEMENT, 1_000L)

        book.updateEntry(entry, BookEntryKind.DEBT, 1_000L, System.currentTimeMillis(), "")

        val updated = book.getEntry(entry)!!
        assertEquals("DEBT", updated.side)
        assertEquals(6_000L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun deletingAnEntryIsSoftAndExcludedFromTotals() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "فهد")
        val first = book.addEntry(person, local.id, BookEntryKind.DEBT, 5_000L)
        book.addEntry(person, local.id, BookEntryKind.DEBT, 2_000L)

        book.deleteEntry(first)

        val stored = book.getEntry(first)!!
        assertNotNull(stored.deletedAt)
        assertEquals(2_000L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun zeroAmountIsRejected() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "بدر")

        val result = runCatching { book.addEntry(person, local.id, BookEntryKind.DEBT, 0L) }

        assertTrue(result.exceptionOrNull() is LedgerException)
    }

    @Test
    fun archivedPersonRejectsNewEntriesButKeepsHistory() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "تركي")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 900L)

        book.archivePerson(person)

        val result = runCatching { book.addEntry(person, local.id, BookEntryKind.DEBT, 100L) }
        assertTrue(result.exceptionOrNull() is LedgerException)
        assertTrue(book.listPersons(shopId).none { it.id == person })
        assertEquals(900L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun archivedCurrencyStaysReadableButCannotBeUsed() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "ناصر")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 500L)

        book.archiveCurrency(local.id)

        val result = runCatching { book.addEntry(person, local.id, BookEntryKind.DEBT, 500L) }
        assertTrue(result.exceptionOrNull() is LedgerException)
        // الأرصدة القديمة تبقى محسوبة بعملتها.
        assertEquals(500L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun personsAndBalancesFlowsAreShopScoped() = runBlocking {
        val otherShop = ledger.createShop("محل آخر")
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "مشعل")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 700L)

        val persons = book.observePersons(shopId).first()
        val otherPersons = book.observePersons(otherShop).first()
        val balances = book.observeBalances(shopId).first()
        val activity = book.observeLastActivity().first().firstOrNull { it.personId == person }

        assertEquals(listOf(person), persons.map { it.id })
        assertTrue(otherPersons.isEmpty())
        assertEquals(1, balances.size)
        assertNotNull(activity?.lastAt)
    }

    @Test
    fun statementIsOrderedAscendingWithRunningBalance() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "عمر")
        val now = System.currentTimeMillis()
        book.addEntry(person, local.id, BookEntryKind.DEBT, 1_000L, now - 20_000L)
        book.addEntry(person, local.id, BookEntryKind.SETTLEMENT, 400L, now - 10_000L)
        book.addEntry(person, local.id, BookEntryKind.DEBT, 200L, now)

        val rows = book.statement(person)

        assertEquals(listOf(1_000L, 400L, 200L), rows.map { it.amountMinor })
        assertEquals(listOf("DEBT", "SETTLEMENT", "DEBT"), rows.map { it.kind })
        assertEquals(800L, book.balancesFor(person).single().netMinor)
    }

    @Test
    fun databaseHealthCheckPassesForTheBook() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "زياد")
        book.addEntry(person, local.id, BookEntryKind.DEBT, 100L)

        val report = DatabaseHealthCheck.inspect(db)
        val bookIssues = report.issues.filter { it.code.startsWith("book_") || it.code.startsWith("orphan_book") || it.code == "no_currencies" }

        assertTrue(bookIssues.joinToString { it.code }, bookIssues.isEmpty())
    }

    @Test
    fun databaseHealthCheckDetectsCorruptedBookEntry() = runBlocking {
        val local = book.defaultCurrency("LOCAL")!!
        val person = book.addPerson(shopId, "وليد")
        val now = System.currentTimeMillis()
        // قيم kind/side غير معروفة تحاكي تلف بيانات مستعاد من نسخة قديمة.
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO book_entries(
                personId, currencyId, kind, side, amountMinor, occurredAt,
                details, opening, createdAt, updatedAt
            ) VALUES (?, ?, 'UNKNOWN', 'SIDEWAYS', 100, ?, '', 0, ?, ?)
            """.trimIndent(),
            arrayOf(person, local.id, now, now, now)
        )

        val report = DatabaseHealthCheck.inspect(db)

        assertTrue(report.issues.any { it.code == "book_entry_bad_side" })
        assertFalse(report.ok)
    }

    @Test
    fun defaultCurrencyCanBeChanged() = runBlocking {
        val dollar = book.listCurrencies().first { it.code == "USD" }

        book.setDefaultCurrency(dollar.id)

        assertEquals(dollar.id, book.defaultCurrency("SAR")?.id)
    }

    @Test
    fun addPersonRequiresAName() = runBlocking {
        val result = runCatching { book.addPerson(shopId, "   ") }

        assertTrue(result.exceptionOrNull() is LedgerException)
    }

    private companion object {
        private const val APP_DB_NAME = "daftari.db"
    }
}
