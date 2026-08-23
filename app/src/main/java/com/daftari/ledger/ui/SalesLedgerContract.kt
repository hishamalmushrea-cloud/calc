package com.daftari.ledger.ui

import com.daftari.ledger.data.CategoryTotal
import com.daftari.ledger.data.DailyBookSummary
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.EmployeePerformanceRow
import com.daftari.ledger.data.SalesBookPeriodSummary

enum class SalesBookView { WEEK, CALENDAR, ANALYTICS, SEARCH }
enum class SalesBookRange { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, CUSTOM }

data class SalesLedgerState(
    val selectedDayStart: Long? = null,
    val visibleFrom: Long = 0,
    val visibleTo: Long = 0,
    val view: SalesBookView = SalesBookView.WEEK,
    val range: SalesBookRange = SalesBookRange.THIS_WEEK,
    val days: List<DailyBookSummary> = emptyList(),
    val entries: List<DocumentEntity> = emptyList(),
    val dayEmployeePerformance: List<EmployeePerformanceRow> = emptyList(),
    val periodSummary: SalesBookPeriodSummary? = null,
    val previousPeriodSummary: SalesBookPeriodSummary? = null,
    val outflowCategories: List<CategoryTotal> = emptyList(),
    val query: String = "",
    val entryType: String? = null,
    val paymentMethod: String? = null,
    val categoryId: Long? = null,
    val loading: Boolean = false
)

data class SalesEntryDraft(
    val type: String,
    val amount: String,
    val occurredAt: Long,
    val categoryId: Long? = null,
    val paymentMethod: String = "CASH",
    val partyId: Long? = null,
    val employeeId: Long? = null,
    val newPartyName: String? = null,
    val notes: String = "",
    val documentNumber: String = "",
    val dueAt: Long? = null
)
