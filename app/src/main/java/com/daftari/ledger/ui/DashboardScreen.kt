package com.daftari.ledger.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.domain.DocType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardScreen(
    state: UiState,
    onEvent: (UiEvent) -> Unit,
    padding: PaddingValues,
    onQuick: (DocType) -> Unit
) {
    val totals = state.totals
    val lateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val rangeFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var showRangePicker by remember { mutableStateOf(false) }
    val periodLabels = mapOf(
        Period.TODAY to stringResource(R.string.period_today),
        Period.YESTERDAY to stringResource(R.string.period_yesterday),
        Period.WEEK to stringResource(R.string.period_week),
        Period.MONTH to stringResource(R.string.period_month),
        Period.YEAR to stringResource(R.string.period_year),
        Period.CUSTOM to stringResource(R.string.period_custom)
    )

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { period ->
                FilterChip(
                    selected = state.period == period,
                    onClick = {
                        if (period == Period.CUSTOM && (state.customFrom == null || state.customTo == null)) {
                            showRangePicker = true
                        } else onEvent(UiEvent.SetPeriod(period))
                    },
                    label = {
                        val from = state.customFrom
                        val to = state.customTo
                        if (period == Period.CUSTOM && from != null && to != null) {
                            Text("${rangeFormat.format(Date(from))} → ${rangeFormat.format(Date(to))}")
                        } else Text(periodLabels.getValue(period))
                    }
                )
            }
        }
        if (showRangePicker) {
            RangePicker(
                initialFrom = state.customFrom,
                initialTo = state.customTo,
                onDismiss = { showRangePicker = false },
                onConfirm = { from, to ->
                    onEvent(UiEvent.SetCustomRange(from, to))
                    showRangePicker = false
                }
            )
        }
        if (state.docs.isEmpty() && state.customers.isEmpty() && state.suppliers.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.dashboard_empty), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.dashboard_empty_hint), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onQuick(DocType.SALE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.quick_sale)) }
            Button(onClick = { onQuick(DocType.COLLECT) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.doc_type_collect)) }
            Button(onClick = { onQuick(DocType.EXPENSE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.doc_type_expense)) }
        }
        Spacer(Modifier.height(8.dp))
        HeroMetric(
            stringResource(R.string.cash_net_title),
            totals.cashNet,
            totals.cashNet >= 0,
            stringResource(R.string.cash_net_subtitle)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMetric(stringResource(R.string.owed_to_you), state.owedToYou, true, Modifier.weight(1f))
            SplitMetric(stringResource(R.string.you_owe), state.youOwe, false, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMetric(stringResource(R.string.customer_advances), state.customerAdvances, false, Modifier.weight(1f))
            SplitMetric(stringResource(R.string.supplier_credits), state.supplierCredits, true, Modifier.weight(1f))
        }
        Text(stringResource(R.string.dashboard_balances_hint), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        ComparisonCard(state.totals.sales, state.prevTotals.sales)
        if (state.agingAlert > 0) {
            Text(
                pluralStringResource(R.plurals.aging_alert, state.agingAlert, state.agingAlert),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.late.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.top_late_customers), fontWeight = FontWeight.Bold)
            state.late.take(3).forEachIndexed { index, row -> LateCard(row, index, lateFormat) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.period_indicators), fontWeight = FontWeight.Bold)
        Metric(stringResource(R.string.sales), totals.sales, true)
        Metric(stringResource(R.string.expenses), totals.expenses, false)
        Metric(stringResource(R.string.estimated_profit), totals.estimatedProfit, totals.estimatedProfit >= 0)
        Text(stringResource(R.string.profit_disclaimer), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.operations_analysis), fontWeight = FontWeight.Bold)
        DoughnutChart(
            listOf(
                stringResource(R.string.sales) to totals.sales,
                stringResource(R.string.expenses) to totals.expenses,
                stringResource(R.string.collections) to totals.collections,
                stringResource(R.string.payments) to totals.payments
            )
        )

        val salesDocs = state.docs.filter { it.type == "SALE" }.sortedBy { it.occurredAt }
        if (salesDocs.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.recent_sales_movement), fontWeight = FontWeight.Bold)
            val dayFormat = remember { SimpleDateFormat("dd/MM", Locale.US) }
            SmoothLineChart(
                salesDocs.groupBy { dayFormat.format(Date(it.occurredAt)) }
                    .map { it.key to it.value.sumOf { document -> document.amountMinor } }
                    .takeLast(6)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val startState = rememberDatePickerState(initialSelectedDateMillis = initialFrom ?: today)
    val endState = rememberDatePickerState(initialSelectedDateMillis = initialTo ?: System.currentTimeMillis())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_range_title)) },
        text = {
            Column {
                Text(stringResource(R.string.range_from), style = MaterialTheme.typography.labelMedium)
                DatePicker(state = startState)
                Text(stringResource(R.string.range_to), style = MaterialTheme.typography.labelMedium)
                DatePicker(state = endState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val from = startState.selectedDateMillis ?: return@TextButton
                val to = endState.selectedDateMillis ?: return@TextButton
                val (start, end) = if (from <= to) from to to else to to from
                onConfirm(combineWithCurrentTime(start), endOfDay(combineWithCurrentTime(end)))
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

internal fun combineWithCurrentTime(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun endOfDay(localMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = localMillis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis
