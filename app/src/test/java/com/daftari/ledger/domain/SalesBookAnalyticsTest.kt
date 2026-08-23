package com.daftari.ledger.domain

import com.daftari.ledger.data.DailyBookSummary
import com.daftari.ledger.data.PaymentTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesBookAnalyticsTest {
    @Test
    fun summaryUsesLiquidPaymentsForCashMovementAndGrossSalesForSalesTotal() {
        val first = day(
            start = 1,
            sales = 15_000,
            outflows = 2_000,
            liquidSales = 10_000,
            payments = listOf(PaymentTotal("CASH", 10_000, 2), PaymentTotal("CREDIT", 5_000, 1))
        )
        val second = day(
            start = 2,
            sales = 8_000,
            outflows = 1_000,
            liquidSales = 8_000,
            payments = listOf(PaymentTotal("CASH", 8_000, 1))
        )

        val result = SalesBookAnalytics.summarize(listOf(first, second))

        assertEquals(23_000L, result.salesMinor)
        assertEquals(3_000L, result.outflowsMinor)
        assertEquals(15_000L, result.netCashMovementMinor)
        assertEquals(11_500L, result.dailyAverageSalesMinor)
        assertEquals(1L, result.bestDay?.dayStart)
        assertEquals(2L, result.weakestDay?.dayStart)
        assertEquals(18_000L, result.paymentTotals.first { it.method == "CASH" }.amountMinor)
    }

    @Test
    fun emptyPeriodIsSafe() {
        val result = SalesBookAnalytics.summarize(emptyList())
        assertEquals(0L, result.salesMinor)
        assertEquals(0, result.activeDays)
        assertEquals(0L, result.dailyAverageSalesMinor)
        assertNull(result.bestDay)
        assertNull(result.weakestDay)
    }

    @Test
    fun daysWithOnlyOutflowsCountAsActiveWithoutInventingProfit() {
        val result = SalesBookAnalytics.summarize(
            listOf(day(4, sales = 0, outflows = 2_500, liquidSales = 0, outflowCount = 2))
        )
        assertEquals(1, result.activeDays)
        assertEquals(-2_500L, result.netCashMovementMinor)
        assertEquals(0L, result.dailyAverageSalesMinor)
    }

    private fun day(
        start: Long,
        sales: Long,
        outflows: Long,
        liquidSales: Long,
        saleCount: Int = if (sales == 0L) 0 else 1,
        outflowCount: Int = if (outflows == 0L) 0 else 1,
        payments: List<PaymentTotal> = emptyList()
    ) = DailyBookSummary(
        dayStart = start,
        salesMinor = sales,
        outflowsMinor = outflows,
        cashSalesMinor = liquidSales,
        cashOutflowsMinor = outflows,
        saleCount = saleCount,
        outflowCount = outflowCount,
        notes = "",
        status = "HAS_RECORDS",
        closedAt = null,
        payments = payments
    )
}
