package com.daftari.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.data.DailyBookSummary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SalesLedgerScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val ledger = state.salesLedger
    LaunchedEffect(state.shop?.id) { onEvent(UiEvent.LoadSalesLedger) }
    ledger.selectedDayStart?.let {
        SalesDayScreen(state, onEvent, padding, it)
        return
    }
    var showCustomRange by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sales_book_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.sales_book_cash_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SalesBookView.entries.forEach { view ->
                FilterChip(
                    selected = ledger.view == view,
                    onClick = { onEvent(UiEvent.SetSalesBookView(view)) },
                    label = { Text(viewLabel(view)) },
                    leadingIcon = {
                        Icon(
                            when (view) {
                                SalesBookView.WEEK -> Icons.AutoMirrored.Filled.MenuBook
                                SalesBookView.CALENDAR -> Icons.Default.CalendarMonth
                                SalesBookView.ANALYTICS -> Icons.Default.Analytics
                                SalesBookView.SEARCH -> Icons.Default.Search
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SalesBookRange.entries.filter { it != SalesBookRange.CUSTOM }.forEach { range ->
                FilterChip(
                    selected = ledger.range == range,
                    onClick = { onEvent(UiEvent.SetSalesBookRange(range)) },
                    label = { Text(rangeLabel(range)) }
                )
            }
            FilterChip(
                selected = ledger.range == SalesBookRange.CUSTOM,
                onClick = { showCustomRange = true },
                label = { Text(stringResource(R.string.period_custom)) }
            )
        }
        Spacer(Modifier.height(6.dp))
        when (ledger.view) {
            SalesBookView.WEEK -> SalesDaysList(ledger.days, onEvent)
            SalesBookView.CALENDAR -> SalesCalendar(ledger.days, onEvent)
            SalesBookView.ANALYTICS -> SalesAnalytics(ledger)
            SalesBookView.SEARCH -> SalesSearch(state, onEvent)
        }
    }
    if (showCustomRange) {
        SalesCustomRangeDialog(
            onDismiss = { showCustomRange = false },
            onConfirm = { from, to ->
                onEvent(UiEvent.SetSalesBookCustomRange(from, to))
                showCustomRange = false
            }
        )
    }
}

@Composable
private fun SalesDaysList(days: List<DailyBookSummary>, onEvent: (UiEvent) -> Unit) {
    if (days.isEmpty()) {
        Text(stringResource(R.string.sales_book_no_days), modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(days.sortedByDescending { it.dayStart }, key = { it.dayStart }) { day ->
            SalesDayCard(day) { onEvent(UiEvent.SelectSalesDay(day.dayStart)) }
        }
    }
}

@Composable
private fun SalesCalendar(days: List<DailyBookSummary>, onEvent: (UiEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf(R.string.day_sat, R.string.day_sun, R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri)
                .forEach { Text(stringResource(it), style = MaterialTheme.typography.labelSmall) }
        }
        val firstOffset = days.firstOrNull()?.let { day ->
            java.util.Calendar.getInstance().apply { timeInMillis = day.dayStart }.get(java.util.Calendar.DAY_OF_WEEK) % 7
        } ?: 0
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
            items(firstOffset) { Spacer(Modifier.padding(3.dp)) }
            items(days, key = { it.dayStart }) { day ->
                Card(
                    Modifier.padding(3.dp).clickable { onEvent(UiEvent.SelectSalesDay(day.dayStart)) },
                    colors = CardDefaults.cardColors(
                        containerColor = when (day.status) {
                            "CLOSED" -> MaterialTheme.colorScheme.primaryContainer
                            "REOPENED" -> MaterialTheme.colorScheme.errorContainer
                            "HAS_RECORDS" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
                        }
                    )
                ) {
                    Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(day.dayStart)), fontWeight = FontWeight.Bold)
                        if (day.transactionCount > 0) Text(displayMoney(day.salesMinor), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesDayCard(day: DailyBookSummary, onClick: () -> Unit) {
    val date = DateFormat.getDateInstance(DateFormat.FULL).format(Date(day.dayStart))
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    when (day.status) {
                        "CLOSED" -> stringResource(R.string.sales_day_closed)
                        "REOPENED" -> stringResource(R.string.sales_day_reopened_alert)
                        "HAS_RECORDS" -> stringResource(R.string.sales_day_has_entries)
                        else -> stringResource(R.string.sales_day_empty)
                    },
                    color = when (day.status) {
                        "CLOSED" -> MaterialTheme.colorScheme.primary
                        "REOPENED" -> MaterialTheme.colorScheme.error
                        "HAS_RECORDS" -> Color(0xFF9A6B00)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (day.transactionCount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.sales_short, displayMoney(day.salesMinor)), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.outflows_short, displayMoney(day.outflowsMinor)), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.net_short, displayMoney(day.netCashMovementMinor)), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SalesAnalytics(ledger: SalesLedgerState) {
    val summary = ledger.periodSummary ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitMetric(stringResource(R.string.sales), summary.salesMinor, true, Modifier.weight(1f))
                SplitMetric(stringResource(R.string.outflows), summary.outflowsMinor, false, Modifier.weight(1f))
            }
            HeroMetric(
                stringResource(R.string.net_cash_movement),
                summary.netCashMovementMinor,
                summary.netCashMovementMinor >= 0,
                stringResource(R.string.sales_book_cash_disclaimer)
            )
            Metric(stringResource(R.string.daily_sales_average), summary.dailyAverageSalesMinor, true)
            ledger.previousPeriodSummary?.let { previous ->
                ComparisonCard(summary.salesMinor, previous.salesMinor)
            }
            Text(stringResource(R.string.active_days_value, summary.activeDays))
            summary.bestDay?.let {
                Text(stringResource(R.string.best_sales_day, DateFormat.getDateInstance().format(Date(it.dayStart)), displayMoney(it.salesMinor)))
            }
            summary.weakestDay?.let {
                Text(stringResource(R.string.weakest_sales_day, DateFormat.getDateInstance().format(Date(it.dayStart)), displayMoney(it.salesMinor)))
            }
            if (ledger.days.count { it.transactionCount > 0 } > 1) {
                SmoothLineChart(ledger.days.filter { it.transactionCount > 0 }.map {
                    SimpleDateFormat("dd/MM", Locale.US).format(Date(it.dayStart)) to it.salesMinor
                })
            }
            if (summary.paymentTotals.isNotEmpty()) {
                Text(stringResource(R.string.payment_distribution), fontWeight = FontWeight.Bold)
                DoughnutChart(summary.paymentTotals.map { paymentLabel(it.method) to it.amountMinor })
            }
            if (ledger.outflowCategories.isNotEmpty()) {
                Text(stringResource(R.string.outflow_category_distribution), fontWeight = FontWeight.Bold)
                DoughnutChart(ledger.outflowCategories.map { it.categoryName to it.totalMinor })
                Text(stringResource(R.string.top_outflow_category, ledger.outflowCategories.first().categoryName))
            }
            if (summary.outflowsMinor > summary.salesMinor && summary.outflowsMinor > 0) {
                Text(stringResource(R.string.smart_alert_outflows_high), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesSearch(state: UiState, onEvent: (UiEvent) -> Unit) {
    val ledger = state.salesLedger
    var query by remember(ledger.query) { mutableStateOf(ledger.query) }
    var type by remember(ledger.entryType) { mutableStateOf(ledger.entryType) }
    var payment by remember(ledger.paymentMethod) { mutableStateOf(ledger.paymentMethod) }
    var category by remember(ledger.categoryId) { mutableStateOf(ledger.categoryId) }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sales_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(type == null, { type = null }, label = { Text(stringResource(R.string.all)) })
            FilterChip(type == "SALE", { type = "SALE" }, label = { Text(stringResource(R.string.doc_type_sale)) })
            FilterChip(type == "EXPENSE", { type = "EXPENSE" }, label = { Text(stringResource(R.string.outflows)) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(null, "CASH", "BANK", "CARD", "WALLET", "CREDIT", "OTHER").forEach { method ->
                FilterChip(payment == method, { payment = method }, label = { Text(method?.let { paymentLabel(it) } ?: stringResource(R.string.all)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            state.categories.filter { it.kind == "EXPENSE" }.forEach { item ->
                FilterChip(category == item.id, { category = if (category == item.id) null else item.id }, label = { Text(item.name) })
            }
        }
        Button(
            onClick = { onEvent(UiEvent.SearchSalesBook(query, type, payment, category)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.search)) }
        LazyColumn {
            items(ledger.entries, key = { it.id }) { entry ->
                SalesBookEntryRow(entry, state, readOnly = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesCustomRangeDialog(onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    val from = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val to = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_range_title)) },
        text = {
            Column {
                Text(stringResource(R.string.range_from))
                DatePicker(from)
                Text(stringResource(R.string.range_to))
                DatePicker(to)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(from.selectedDateMillis ?: 0, to.selectedDateMillis ?: 0) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun paymentLabel(method: String): String = when (method) {
    "CASH" -> stringResource(R.string.payment_cash)
    "BANK" -> stringResource(R.string.payment_bank)
    "CARD" -> stringResource(R.string.payment_card)
    "WALLET" -> stringResource(R.string.payment_wallet)
    "CREDIT" -> stringResource(R.string.payment_credit)
    else -> stringResource(R.string.payment_other)
}

@Composable
private fun viewLabel(view: SalesBookView): String = when (view) {
    SalesBookView.WEEK -> stringResource(R.string.sales_view_book)
    SalesBookView.CALENDAR -> stringResource(R.string.sales_view_calendar)
    SalesBookView.ANALYTICS -> stringResource(R.string.sales_view_analytics)
    SalesBookView.SEARCH -> stringResource(R.string.search)
}

@Composable
private fun rangeLabel(range: SalesBookRange): String = when (range) {
    SalesBookRange.TODAY -> stringResource(R.string.period_today)
    SalesBookRange.YESTERDAY -> stringResource(R.string.period_yesterday)
    SalesBookRange.THIS_WEEK -> stringResource(R.string.this_week)
    SalesBookRange.LAST_WEEK -> stringResource(R.string.last_week)
    SalesBookRange.THIS_MONTH -> stringResource(R.string.this_month)
    SalesBookRange.LAST_MONTH -> stringResource(R.string.last_month)
    SalesBookRange.CUSTOM -> stringResource(R.string.period_custom)
}
