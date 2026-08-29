package com.daftari.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تنبيهات الديون: من يُنبَّه عنه، ومتى، وبأي ترتيب.
 *
 * الأوقات مثبّتة على لحظة واحدة حتى لا تتأثر النتيجة بزمن تشغيل الاختبار.
 */
class BookAlertsTest {

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_700_000_000_000L
    }

    @Test
    fun idleDaysCountsWholeDaysOnly() {
        assertEquals(0L, BookAlerts.idleDays(NOW, NOW))
        assertEquals(0L, BookAlerts.idleDays(NOW - DAY + 1L, NOW))
        assertEquals(29L, BookAlerts.idleDays(NOW - 29 * DAY, NOW))
        assertEquals(30L, BookAlerts.idleDays(NOW - 30 * DAY, NOW))
        // ٢٩ يومًا و٢٣ ساعة ليست ٣٠ يومًا.
        assertEquals(29L, BookAlerts.idleDays(NOW - 30 * DAY + 3_600_000L, NOW))
    }

    @Test
    fun idleDaysIsNullWhenActivityIsUnknown() {
        assertNull(BookAlerts.idleDays(null, NOW))
        assertNull(BookAlerts.idleDays(-1L, NOW))
        assertNull(BookAlerts.idleDays(NOW + DAY, NOW))
    }

    @Test
    fun settledAndCreditBalancesNeverAlert() {
        assertFalse(BookAlerts.isStale(0L, NOW - 400 * DAY, NOW))
        assertFalse(BookAlerts.isStale(-5_000L, NOW - 400 * DAY, NOW))
    }

    @Test
    fun recentDebtDoesNotAlert() {
        assertFalse(BookAlerts.isStale(5_000L, NOW - 10 * DAY, NOW))
        assertFalse(BookAlerts.isStale(5_000L, NOW - 29 * DAY, NOW))
    }

    @Test
    fun debtIdleThirtyDaysAlerts() {
        assertTrue(BookAlerts.isStale(5_000L, NOW - 30 * DAY, NOW))
        val alerts = BookAlerts.staleDebts(
            listOf(BookDebtor(7L, 3L, 5_000L, NOW - 30 * DAY)),
            NOW
        )
        assertEquals(1, alerts.size)
        assertEquals(7L, alerts[0].personId)
        assertEquals(3L, alerts[0].currencyId)
        assertEquals(5_000L, alerts[0].netMinor)
        assertEquals(30L, alerts[0].idleDays)
    }

    @Test
    fun missingActivityNeverAlerts() {
        assertFalse(BookAlerts.isStale(5_000L, null, NOW))
        assertEquals(0, BookAlerts.staleDebts(listOf(BookDebtor(7L, 3L, 5_000L, null)), NOW).size)
    }

    @Test
    fun thresholdBelowOneDisablesAlerts() {
        assertFalse(BookAlerts.isStale(5_000L, NOW - 400 * DAY, NOW, staleAfterDays = 0))
        assertFalse(BookAlerts.isStale(5_000L, NOW - 400 * DAY, NOW, staleAfterDays = -5))
        assertEquals(0, BookAlerts.staleDebts(listOf(BookDebtor(7L, 3L, 5_000L, NOW - 400 * DAY)), NOW, 0).size)
    }

    @Test
    fun customThresholdIsHonoured() {
        assertTrue(BookAlerts.isStale(5_000L, NOW - 7 * DAY, NOW, staleAfterDays = 7))
        assertFalse(BookAlerts.isStale(5_000L, NOW - 6 * DAY, NOW, staleAfterDays = 7))
    }

    @Test
    fun staleDebtsSkipEveryoneElse() {
        val debtors = listOf(
            BookDebtor(1L, 3L, 9_000L, NOW - 3 * DAY),      // دين حديث
            BookDebtor(2L, 3L, 0L, NOW - 200 * DAY),        // مسدَّد
            BookDebtor(3L, 3L, -4_000L, NOW - 200 * DAY),   // له عندك
            BookDebtor(4L, 3L, 1_000L, NOW - 60 * DAY)      // متوقف
        )
        val alerts = BookAlerts.staleDebts(debtors, NOW)
        assertEquals(listOf(4L), alerts.map { it.personId })
    }

    @Test
    fun staleDebtsAreSortedByIdleThenAmount() {
        val debtors = listOf(
            BookDebtor(1L, 3L, 9_000L, NOW - 40 * DAY),
            BookDebtor(2L, 3L, 1_000L, NOW - 90 * DAY),
            BookDebtor(3L, 3L, 7_000L, NOW - 40 * DAY)
        )
        // الأكثر توقفًا أولًا (2)، ثم الأكبر دينًا بين المتساويين (1 قبل 3).
        assertEquals(listOf(2L, 1L, 3L), BookAlerts.staleDebts(debtors, NOW).map { it.personId })
        assertEquals(
            listOf(90L, 40L, 40L),
            BookAlerts.staleDebts(debtors, NOW).map { it.idleDays }
        )
    }

    @Test
    fun theSamePersonCanAlertPerCurrency() {
        val debtors = listOf(
            BookDebtor(1L, 3L, 5_000L, NOW - 45 * DAY),
            BookDebtor(1L, 4L, 20_000L, NOW - 45 * DAY)
        )
        val alerts = BookAlerts.staleDebts(debtors, NOW)
        assertEquals(2, alerts.size)
        // تساوى التوقف فيُقدَّم الأكبر دينًا (عملة 4 بمبلغ 20,000).
        assertEquals(listOf(4L, 3L), alerts.map { it.currencyId })
    }
}
