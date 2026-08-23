package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun ReportsScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val totals = state.totals
    val lateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    LaunchedEffect(state.shop?.id) { onEvent(UiEvent.LoadInsights) }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.period_report), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(4.dp))
        Metric(stringResource(R.string.sales), totals.sales, true)
        Metric(stringResource(R.string.purchases), totals.purchases, false)
        Metric(stringResource(R.string.expenses), totals.expenses, false)
        Metric(stringResource(R.string.collections), totals.collections, true)
        Metric(stringResource(R.string.payments), totals.payments, false)
        Metric(stringResource(R.string.cash_net), totals.cashNet, totals.cashNet >= 0)
        Metric(stringResource(R.string.estimated_profit), totals.estimatedProfit, totals.estimatedProfit >= 0)
        Spacer(Modifier.padding(4.dp))
        Text(stringResource(R.string.income_expense_analysis), fontWeight = FontWeight.Bold)
        DoughnutChart(
            listOf(
                stringResource(R.string.sales) to totals.sales,
                stringResource(R.string.expenses) to totals.expenses,
                stringResource(R.string.purchases) to totals.purchases
            )
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEvent(UiEvent.ExportPdf) }, modifier = Modifier.weight(1f)) { Text("PDF") }
            Button(onClick = { onEvent(UiEvent.ExportExcel) }, modifier = Modifier.weight(1f)) { Text("Excel") }
        }
        Spacer(Modifier.padding(4.dp))
        Text(stringResource(R.string.customer_debt_aging), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.aging_buckets), style = MaterialTheme.typography.bodySmall)
        if (state.aging.isEmpty()) Text(stringResource(R.string.no_due_debts), style = MaterialTheme.typography.bodySmall)
        state.aging.forEach { AgingCard(it) }
        Spacer(Modifier.padding(4.dp))
        Text(stringResource(R.string.category_report), fontWeight = FontWeight.Bold)
        if (state.categoryTotals.isEmpty()) {
            Text(stringResource(R.string.no_category_data), style = MaterialTheme.typography.bodySmall)
        }
        state.categoryTotals.forEach { total ->
            Metric(total.categoryName, total.totalMinor, false)
        }
        Spacer(Modifier.padding(4.dp))
        Text(stringResource(R.string.late_customers), fontWeight = FontWeight.Bold)
        if (state.late.isEmpty()) Text(stringResource(R.string.no_late_customers), style = MaterialTheme.typography.bodySmall)
        state.late.forEachIndexed { index, row -> LateCard(row, index, lateFormat) }
    }
}
