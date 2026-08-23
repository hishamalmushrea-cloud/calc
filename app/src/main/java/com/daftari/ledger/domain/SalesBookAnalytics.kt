package com.daftari.ledger.domain

import com.daftari.ledger.data.DailyBookSummary
import com.daftari.ledger.data.PaymentTotal
import com.daftari.ledger.data.SalesBookPeriodSummary

/** حسابات دفتر البيع الصرفية؛ لا تعتمد على Android أو قاعدة البيانات. */
object SalesBookAnalytics {
    fun summarize(days: List<DailyBookSummary>): SalesBookPeriodSummary {
        val active = days.filter { it.transactionCount > 0 }
        val sales = days.sumOf { it.salesMinor }
        val outflows = days.sumOf { it.outflowsMinor }
        val payments = days.flatMap { it.payments }
            .groupBy { it.method }
            .map { (method, rows) ->
                PaymentTotal(method, rows.sumOf { it.amountMinor }, rows.sumOf { it.count })
            }
            .sortedByDescending { it.amountMinor }
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
            paymentTotals = payments
        )
    }
}
