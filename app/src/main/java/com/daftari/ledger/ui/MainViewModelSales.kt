package com.daftari.ledger.ui

import androidx.lifecycle.viewModelScope
import com.daftari.ledger.R
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.SalesBookEntryInput
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.loadSalesLedger() {
    val shop = state.value.shop ?: return
    val current = state.value.salesLedger
    val (from, to) = if (current.visibleFrom == 0L || current.visibleTo == 0L) {
        salesRange(SalesBookRange.THIS_WEEK)
    } else current.visibleFrom to current.visibleTo
    viewModelScope.launch {
        val scopedEmployeeId = state.value.employees.currentEmployee?.id
            ?.takeUnless { state.value.can(StaffPermission.VIEW_ALL_SALES) }
        mutableState.update { it.copy(salesLedger = it.salesLedger.copy(loading = true, visibleFrom = from, visibleTo = to)) }
        val days = repo.salesBookDays(shop.id, from, to, scopedEmployeeId)
        val summary = repo.salesBookPeriodSummary(shop.id, from, to, scopedEmployeeId)
        val periodLength = (to - from + 1).coerceAtLeast(1)
        val previousSummary = repo.salesBookPeriodSummary(shop.id, from - periodLength, from - 1, scopedEmployeeId)
        val uncategorized = getApplication<android.app.Application>().getString(R.string.uncategorized)
        val outflowCategories = repo.totalsByCategory(
            shop.id,
            com.daftari.ledger.domain.DocType.EXPENSE,
            from,
            to,
            uncategorized,
            scopedEmployeeId
        )
        val selected = state.value.salesLedger.selectedDayStart
        val entries = selected?.let { repo.salesBookEntries(shop.id, it, scopedEmployeeId) }.orEmpty()
        val employeePerformance = selected?.let { day ->
            staff.performance(shop.id, day, endOfDay(day)).filter { scopedEmployeeId == null || it.employeeId == scopedEmployeeId }
        }.orEmpty()
        mutableState.update {
            it.copy(
                salesLedger = it.salesLedger.copy(
                    days = days,
                    entries = entries,
                    dayEmployeePerformance = employeePerformance,
                    periodSummary = summary,
                    previousPeriodSummary = previousSummary,
                    outflowCategories = outflowCategories,
                    visibleFrom = from,
                    visibleTo = to,
                    loading = false
                )
            )
        }
    }
}

internal fun MainViewModel.setSalesBookView(view: SalesBookView) {
    mutableState.update { it.copy(salesLedger = it.salesLedger.copy(view = view)) }
    if (view != SalesBookView.SEARCH) loadSalesLedger()
}

internal fun MainViewModel.setSalesBookRange(range: SalesBookRange) {
    val (from, to) = salesRange(range)
    mutableState.update {
        it.copy(salesLedger = it.salesLedger.copy(range = range, visibleFrom = from, visibleTo = to, selectedDayStart = null))
    }
    loadSalesLedger()
}

internal fun MainViewModel.setSalesBookCustomRange(from: Long, to: Long) {
    val start = minOf(from, to)
    val end = maxOf(from, to)
    mutableState.update {
        it.copy(
            salesLedger = it.salesLedger.copy(
                range = SalesBookRange.CUSTOM,
                visibleFrom = startOfDay(start),
                visibleTo = endOfDay(end),
                selectedDayStart = null
            )
        )
    }
    loadSalesLedger()
}

internal fun MainViewModel.selectSalesDay(dayStart: Long) {
    val shop = state.value.shop ?: return
    viewModelScope.launch {
        val scopedEmployeeId = state.value.employees.currentEmployee?.id
            ?.takeUnless { state.value.can(StaffPermission.VIEW_ALL_SALES) }
        val entries = repo.salesBookEntries(shop.id, dayStart, scopedEmployeeId)
        val employeePerformance = staff.performance(shop.id, dayStart, endOfDay(dayStart))
            .filter { scopedEmployeeId == null || it.employeeId == scopedEmployeeId }
        mutableState.update {
            it.copy(
                salesLedger = it.salesLedger.copy(
                    selectedDayStart = dayStart,
                    entries = entries,
                    dayEmployeePerformance = employeePerformance
                )
            )
        }
    }
}

internal fun MainViewModel.closeSalesDayPage() {
    mutableState.update {
        it.copy(salesLedger = it.salesLedger.copy(selectedDayStart = null, entries = emptyList(), dayEmployeePerformance = emptyList()))
    }
}

internal fun MainViewModel.saveSalesEntry(draft: SalesEntryDraft) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    val amount = Money.fromMajor(draft.amount) ?: return@launch message(R.string.msg_invalid_amount)
    try {
        repo.postSalesBookEntry(shop.id, draft.toInput(amount.minor), currentActorId())
        message(R.string.msg_sales_entry_saved)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.updateSalesEntry(id: Long, draft: SalesEntryDraft) = viewModelScope.launch {
    val amount = Money.fromMajor(draft.amount) ?: return@launch message(R.string.msg_invalid_amount)
    try {
        repo.updateSalesBookEntry(id, draft.toInput(amount.minor), currentActorId())
        message(R.string.msg_edited)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.archiveSalesEntry(id: Long) = viewModelScope.launch {
    try {
        repo.archiveSalesBookEntry(id, currentActorId())
        mutableState.update { it.copy(message = text(R.string.msg_archived), undoDocumentId = id) }
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.duplicateSalesEntry(id: Long, occurredAt: Long) = viewModelScope.launch {
    try {
        repo.duplicateSalesBookEntry(id, occurredAt, currentActorId())
        message(R.string.msg_sales_entry_duplicated)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.saveSalesDayNotes(dayStart: Long, notes: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        actorId?.let { staff.requirePermission(it, StaffPermission.MANAGE_SHIFTS) }
        repo.saveSalesDayNotes(shop.id, dayStart, notes, actorId)
        message(R.string.msg_day_notes_saved)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.closeSalesBookDay(dayStart: Long, notes: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        actorId?.let { staff.requirePermission(it, StaffPermission.MANAGE_SHIFTS) }
        repo.closeSalesDay(shop.id, dayStart, notes, actorId)
        message(R.string.msg_sales_day_closed)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.reopenSalesBookDay(dayStart: Long) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        actorId?.let { staff.requirePermission(it, StaffPermission.MANAGE_SHIFTS) }
        repo.reopenSalesDay(shop.id, dayStart, actorId)
        message(R.string.msg_sales_day_reopened)
        loadSalesLedger()
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.searchSalesBook(event: UiEvent.SearchSalesBook) {
    val shop = state.value.shop ?: return
    val ledger = state.value.salesLedger
    viewModelScope.launch {
        val scopedEmployeeId = state.value.employees.currentEmployee?.id
            ?.takeUnless { state.value.can(StaffPermission.VIEW_ALL_SALES) }
        val entries = repo.searchSalesBook(
            shop.id,
            ledger.visibleFrom,
            ledger.visibleTo,
            event.query,
            event.entryType,
            event.paymentMethod,
            event.categoryId,
            scopedEmployeeId
        )
        mutableState.update {
            it.copy(
                salesLedger = it.salesLedger.copy(
                    view = SalesBookView.SEARCH,
                    query = event.query,
                    entryType = event.entryType,
                    paymentMethod = event.paymentMethod,
                    categoryId = event.categoryId,
                    entries = entries
                )
            )
        }
    }
}

internal fun MainViewModel.shareSalesDay(dayStart: Long, detailed: Boolean) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    val scopedEmployeeId = state.value.employees.currentEmployee?.id
        ?.takeUnless { state.value.can(StaffPermission.VIEW_ALL_SALES) }
    val day = repo.salesBookDays(shop.id, dayStart, endOfDay(dayStart), scopedEmployeeId).first()
    val entries = repo.salesBookEntries(shop.id, dayStart, scopedEmployeeId)
    val app = getApplication<android.app.Application>()
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(Date(dayStart))
    val locale = Locale.getDefault()
    val text = buildString {
        append(app.getString(R.string.sales_book_share_title)).append('\n')
        append(date).append("\n\n")
        append(app.getString(R.string.sales_total_value, state.value.displayMoney(day.salesMinor, locale))).append('\n')
        append(app.getString(R.string.outflows_total_value, state.value.displayMoney(day.outflowsMinor, locale))).append('\n')
        append(app.getString(R.string.cash_movement_value, state.value.displayMoney(day.netCashMovementMinor, locale))).append('\n')
        append(app.getString(R.string.transactions_count_value, day.transactionCount)).append('\n')
        if (detailed) {
            append('\n')
            entries.sortedBy { it.occurredAt }.forEach { entry ->
                val sign = if (entry.type == "SALE") "+" else "-"
                append(java.text.SimpleDateFormat("HH:mm", locale).format(Date(entry.occurredAt)))
                    .append("  ").append(sign)
                    .append(state.value.displayMoney(entry.amountMinor, locale))
                    .append("  ").append(entry.notes).append('\n')
            }
        }
        if (day.notes.isNotBlank()) append("\n").append(day.notes)
    }
    mutableState.update { it.copy(shareText = text) }
}

internal fun MainViewModel.exportSalesPeriod(from: Long, to: Long, format: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        actorId?.let { staff.requirePermission(it, StaffPermission.VIEW_REPORTS) }
        val scopedEmployeeId = actorId?.takeUnless { state.value.can(StaffPermission.VIEW_ALL_SALES) }
        val docs = repo.documents.listSalesBookPeriod(shop.id, from, to)
            .filter { scopedEmployeeId == null || it.employeeId == scopedEmployeeId }
        val summary = repo.salesBookPeriodSummary(shop.id, from, to, scopedEmployeeId)
        val totals = LedgerRepository.PeriodTotals(
            sales = summary.salesMinor,
            purchases = 0,
            expenses = summary.outflowsMinor,
            income = 0,
            collections = 0,
            payments = 0,
            cashIn = summary.salesMinor,
            cashOut = summary.outflowsMinor
        )
        val exportState = state.value.copy(docs = docs, totals = totals)
        val file = when (format.uppercase()) {
            "PDF" -> services.exportPdf(exportState)
            "EXCEL" -> services.exportExcel(exportState)
            else -> writeSalesCsv(docs)
        }
        mutableState.update { it.copy(shareFile = file, message = text(R.string.msg_export_ready)) }
    } catch (error: Exception) {
        message(R.string.msg_export_failed, error.message.orEmpty())
    }
}

private fun MainViewModel.writeSalesCsv(entries: List<com.daftari.ledger.data.DocumentEntity>): File {
    val app = getApplication<android.app.Application>()
    val csv = buildString {
        append("date,type,amount,payment,notes,documentNumber\n")
        entries.sortedBy { it.occurredAt }.forEach { entry ->
            append(entry.occurredAt).append(',')
                .append(entry.type).append(',')
                .append(entry.amountMinor).append(',')
                .append(entry.paymentMethod).append(',')
                .append(csvCell(entry.notes)).append(',')
                .append(csvCell(entry.docNumber)).append('\n')
        }
    }
    return File(app.cacheDir, "daftari-sales-${System.currentTimeMillis()}.csv").apply { writeText(csv) }
}

private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

private fun SalesEntryDraft.toInput(amountMinor: Long) = SalesBookEntryInput(
    type = type,
    amountMinor = amountMinor,
    occurredAt = occurredAt,
    categoryId = categoryId,
    paymentMethod = paymentMethod,
    partyId = partyId,
    employeeId = employeeId,
    newPartyName = newPartyName,
    notes = notes,
    documentNumber = documentNumber,
    dueAt = dueAt
)

private fun salesRange(range: SalesBookRange): Pair<Long, Long> {
    val today = LocalDate.now()
    val startThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val (from, to) = when (range) {
        SalesBookRange.TODAY -> today to today
        SalesBookRange.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        SalesBookRange.THIS_WEEK -> startThisWeek to startThisWeek.plusDays(6)
        SalesBookRange.LAST_WEEK -> startThisWeek.minusWeeks(1) to startThisWeek.minusDays(1)
        SalesBookRange.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        SalesBookRange.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1) to
            today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())
        SalesBookRange.CUSTOM -> today to today
    }
    return from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
        to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
}

private fun startOfDay(time: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(time).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}

private fun endOfDay(time: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(time).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
}
