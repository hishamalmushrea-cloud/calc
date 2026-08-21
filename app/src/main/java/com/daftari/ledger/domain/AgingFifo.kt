package com.daftari.ledger.domain

/**
 * توزيع الرصيد المستحق على فئات أعمار الديون بافتراض FIFO
 * (التحصيلات تخصم من أقدم فاتورة أولًا) — دالة صرفة قابلة للاختبار.
 */
object AgingFifo {
    data class Invoice(val amountMinor: Long, val occurredAt: Long)
    data class Buckets(val b0: Long, val b31: Long, val b61: Long, val b90: Long)

    fun allocate(balance: Long, invoices: List<Invoice>, now: Long): Buckets {
        val day = 86_400_000L
        var remaining = balance.coerceAtLeast(0)
        var b0 = 0L
        var b31 = 0L
        var b61 = 0L
        var b90 = 0L
        for (inv in invoices.sortedBy { it.occurredAt }) {
            if (remaining <= 0) break
            val unpaid = minOf(inv.amountMinor, remaining)
            remaining -= unpaid
            val age = ((now - inv.occurredAt) / day).coerceAtLeast(0)
            when {
                age <= 30 -> b0 += unpaid
                age <= 60 -> b31 += unpaid
                age <= 90 -> b61 += unpaid
                else -> b90 += unpaid
            }
        }
        if (remaining > 0) b90 += remaining // احتياط إذا تجاوز الرصيد مجموع الفواتير
        return Buckets(b0, b31, b61, b90)
    }
}
