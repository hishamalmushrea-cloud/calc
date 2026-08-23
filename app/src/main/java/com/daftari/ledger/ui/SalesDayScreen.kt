package com.daftari.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.domain.Money
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SalesDayScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues, dayStart: Long) {
    val summary = state.salesLedger.days.firstOrNull { it.dayStart == dayStart } ?: emptySalesDay(dayStart)
    val closed = summary.status == "CLOSED"
    var addType by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<DocumentEntity?>(null) }
    var pendingArchive by remember { mutableStateOf<DocumentEntity?>(null) }
    var showCloseReview by remember { mutableStateOf(false) }
    var notes by remember(summary.notes) { mutableStateOf(summary.notes) }

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(UiEvent.CloseSalesDayPage) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(dayStart)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (closed) stringResource(R.string.sales_day_closed_read_only) else stringResource(R.string.sales_day_open),
                    color = if (closed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SplitMetric(stringResource(R.string.sales), summary.salesMinor, true, Modifier.weight(1f))
            SplitMetric(stringResource(R.string.outflows), summary.outflowsMinor, false, Modifier.weight(1f))
        }
        HeroMetric(
            stringResource(R.string.net_cash_movement),
            summary.netCashMovementMinor,
            summary.netCashMovementMinor >= 0,
            stringResource(R.string.sales_book_cash_disclaimer)
        )
        Text(stringResource(R.string.transactions_count_value, summary.transactionCount), style = MaterialTheme.typography.bodyMedium)
        if (summary.payments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.payments.take(4).forEach { payment ->
                    Text("${paymentLabel(payment.method)}: ${displayMoney(payment.amountMinor)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        if (!closed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addType = "SALE" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.record_sale))
                }
                Button(onClick = { addType = "EXPENSE" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.record_outflow))
                }
            }
        } else {
            Button(onClick = { onEvent(UiEvent.ReopenSalesBookDay(dayStart)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_closed_day))
            }
        }
        Text(stringResource(R.string.daily_transactions), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (state.salesLedger.entries.isEmpty()) {
                item { Text(stringResource(R.string.no_operations_yet), modifier = Modifier.padding(20.dp)) }
            }
            items(state.salesLedger.entries, key = { it.id }) { entry ->
                SalesBookEntryRow(
                    entry = entry,
                    state = state,
                    onEvent = onEvent,
                    readOnly = closed,
                    onEdit = { editing = entry },
                    onArchive = { pendingArchive = entry }
                )
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.day_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !closed
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (!closed) {
                TextButton(onClick = { onEvent(UiEvent.SaveSalesDayNotes(dayStart, notes)) }) {
                    Text(stringResource(R.string.save_notes))
                }
                TextButton(onClick = { showCloseReview = true }) { Text(stringResource(R.string.close_sales_day)) }
            }
            TextButton(onClick = { onEvent(UiEvent.ShareSalesDay(dayStart, false)) }) { Text(stringResource(R.string.action_share)) }
            TextButton(onClick = { onEvent(UiEvent.ExportSalesPeriod(dayStart, endOfSalesDay(dayStart), "PDF")) }) { Text("PDF") }
            TextButton(onClick = { onEvent(UiEvent.ExportSalesPeriod(dayStart, endOfSalesDay(dayStart), "EXCEL")) }) { Text("Excel") }
            TextButton(onClick = { onEvent(UiEvent.ExportSalesPeriod(dayStart, endOfSalesDay(dayStart), "CSV")) }) { Text("CSV") }
        }
    }

    addType?.let { type ->
        SalesEntryDialog(state, dayStart, type, null, onDismiss = { addType = null }) { draft ->
            onEvent(UiEvent.SaveSalesEntry(draft)); addType = null
        }
    }
    editing?.let { entry ->
        SalesEntryDialog(state, dayStart, entry.type, entry, onDismiss = { editing = null }) { draft ->
            onEvent(UiEvent.UpdateSalesEntry(entry.id, draft)); editing = null
        }
    }
    pendingArchive?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.archive_confirm_body)) },
            confirmButton = {
                Button(onClick = { onEvent(UiEvent.ArchiveSalesEntry(entry.id)); pendingArchive = null }) {
                    Text(stringResource(R.string.action_archive))
                }
            },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showCloseReview) {
        SalesDayCloseDialog(summary, onDismiss = { showCloseReview = false }) {
            onEvent(UiEvent.SaveSalesDayNotes(dayStart, notes))
            onEvent(UiEvent.CloseSalesBookDay(dayStart))
            showCloseReview = false
        }
    }
}

@Composable
internal fun SalesBookEntryRow(
    entry: DocumentEntity,
    state: UiState,
    onEvent: (UiEvent) -> Unit,
    readOnly: Boolean,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null
) {
    val sale = entry.type == "SALE"
    val category = entry.categoryId?.let { id -> state.categories.firstOrNull { it.id == id }?.name }
    val party = entry.partyId?.let { id -> (state.customers + state.suppliers).firstOrNull { it.id == id }?.name }
    Card(Modifier.fillMaxWidth().clickable(enabled = !readOnly && onEdit != null) { onEdit?.invoke() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.occurredAt)) + "  " +
                        if (sale) stringResource(R.string.doc_type_sale) else stringResource(R.string.outflow),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    listOfNotNull(paymentLabel(entry.paymentMethod), category, party).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                (if (sale) "+" else "−") + displayMoney(entry.amountMinor),
                color = if (sale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            if (!readOnly) {
                Column {
                    TextButton(onClick = { onEvent(UiEvent.DuplicateSalesEntry(entry.id, System.currentTimeMillis())) }) {
                        Text(stringResource(R.string.action_duplicate))
                    }
                    onArchive?.let {
                        TextButton(onClick = it) { Text(stringResource(R.string.action_archive), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesDayCloseDialog(summary: DailyBookSummary, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.close_day_review)) },
        text = {
            Column {
                Text(stringResource(R.string.sales_total_value, displayMoney(summary.salesMinor)))
                Text(stringResource(R.string.outflows_total_value, displayMoney(summary.outflowsMinor)))
                Text(stringResource(R.string.cash_movement_value, displayMoney(summary.netCashMovementMinor)))
                Text(stringResource(R.string.sales_count_value, summary.saleCount))
                Text(stringResource(R.string.outflows_count_value, summary.outflowCount))
                summary.payments.forEach { Text("${paymentLabel(it.method)}: ${displayMoney(it.amountMinor)}") }
                if (summary.notes.isNotBlank()) Text(summary.notes, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.close_day_question), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.close_sales_day)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun emptySalesDay(dayStart: Long) = DailyBookSummary(dayStart, 0, 0, 0, 0, 0, 0, "", "EMPTY", null, emptyList())

private fun endOfSalesDay(dayStart: Long): Long = dayStart + 86_400_000L - 1
