package com.daftari.ledger.ui

import java.util.Calendar

internal object PeriodRanges {
    fun current(
        period: Period,
        customFrom: Long?,
        customTo: Long?,
        now: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        fun startOfDay(value: Calendar) = value.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when (period) {
            Period.TODAY -> startOfDay(calendar) to now
            Period.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val start = startOfDay(calendar)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                start to startOfDay(calendar) - 1
            }
            Period.WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                startOfDay(calendar) to now
            }
            Period.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                startOfDay(calendar) to now
            }
            Period.YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                startOfDay(calendar) to now
            }
            Period.CUSTOM -> (customFrom ?: startOfDay(Calendar.getInstance())) to (customTo ?: now)
        }
    }

    fun previous(current: Pair<Long, Long>): Pair<Long, Long> {
        val (from, to) = current
        val length = (to - from).coerceAtLeast(1L)
        return (from - length) to (from - 1)
    }
}
