package com.daftari.ledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.daftari.ledger.R
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.domain.Money
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SalesEntryDialog(
    state: UiState,
    dayStart: Long,
    type: String,
    existing: DocumentEntity?,
    onDismiss: () -> Unit,
    onSave: (SalesEntryDraft) -> Unit
) {
    val sale = type == "SALE"
    var amount by remember { mutableStateOf(existing?.let { Money(it.amountMinor).toBigDecimal().toPlainString() }.orEmpty()) }
    var payment by remember { mutableStateOf(existing?.paymentMethod ?: "CASH") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var documentNumber by remember { mutableStateOf(existing?.docNumber ?: state.nextDocumentNumber.toString()) }
    var partyId by remember { mutableStateOf(existing?.partyId) }
    var partyQuery by remember {
        mutableStateOf(existing?.partyId?.let { id -> state.customers.firstOrNull { it.id == id }?.name }.orEmpty())
    }
    val initialTime = Calendar.getInstance().apply { timeInMillis = existing?.occurredAt ?: System.currentTimeMillis() }
    var occurredAt by remember { mutableStateOf(existing?.occurredAt ?: mergeDayAndTime(dayStart, initialTime.get(Calendar.HOUR_OF_DAY), initialTime.get(Calendar.MINUTE))) }
    var dueAt by remember { mutableStateOf(existing?.dueAt) }
    var showTime by remember { mutableStateOf(false) }
    var showDueDate by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(existing != null) }
    var submitted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (sale) R.string.record_sale else R.string.record_outflow)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    val methods = if (sale) PAYMENT_METHODS else PAYMENT_METHODS.filter { it != "CREDIT" }
                    methods.forEach { method ->
                        FilterChip(
                            selected = payment == method,
                            onClick = {
                                payment = method
                                if (method == "CREDIT" && dueAt == null) dueAt = occurredAt + 30L * 86_400_000L
                                if (method != "CREDIT") dueAt = null
                            },
                            label = { Text(paymentLabel(method)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_optional)) },
                    minLines = 2
                )
                TextButton(onClick = { details = !details }) {
                    Text(stringResource(if (details) R.string.fewer_details else R.string.more_details))
                }
                if (details) {
                    val categoryKind = if (sale) "INCOME" else "EXPENSE"
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelMedium)
                    FlowRow {
                        state.categories.filter { it.kind == categoryKind }.forEach { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = if (categoryId == category.id) null else category.id },
                                label = { Text(category.name) }
                            )
                        }
                    }
                    if (sale && payment == "CREDIT") {
                        OutlinedTextField(
                            value = partyQuery,
                            onValueChange = { partyQuery = it; partyId = null },
                            label = { Text(stringResource(R.string.customer)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.customers.filter {
                            partyQuery.isBlank() || it.name.contains(partyQuery, true) || it.phone.contains(partyQuery, true)
                        }.take(5).forEach { customer ->
                            TextButton(onClick = { partyId = customer.id; partyQuery = customer.name }) { Text(customer.name) }
                        }
                        TextButton(onClick = { showDueDate = true }) {
                            Text(
                                stringResource(
                                    R.string.due_date_value,
                                    java.text.DateFormat.getDateInstance().format(java.util.Date(dueAt ?: occurredAt + 30L * 86_400_000L))
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = documentNumber,
                        onValueChange = { documentNumber = it },
                        label = { Text(stringResource(R.string.document_number_optional)) },
                        singleLine = true
                    )
                    TextButton(onClick = { showTime = true }) {
                        Text(stringResource(R.string.entry_time_value, java.text.SimpleDateFormat("HH:mm").format(java.util.Date(occurredAt))))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitted && amount.isNotBlank(),
                onClick = {
                    submitted = true
                    onSave(
                        SalesEntryDraft(
                            type = type,
                            amount = amount,
                            occurredAt = occurredAt,
                            categoryId = categoryId,
                            paymentMethod = payment,
                            partyId = partyId,
                            newPartyName = if (payment == "CREDIT" && partyId == null) partyQuery.trim().takeIf { it.isNotBlank() } else null,
                            notes = notes,
                            documentNumber = documentNumber,
                            dueAt = if (payment == "CREDIT") dueAt ?: occurredAt + 30L * 86_400_000L else null
                        )
                    )
                }
            ) { Text(stringResource(if (sale) R.string.save_sale else R.string.save_outflow)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showTime) {
        val calendar = remember(occurredAt) { Calendar.getInstance().apply { timeInMillis = occurredAt } }
        val picker = rememberTimePickerState(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.select_time)) },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    occurredAt = mergeDayAndTime(dayStart, picker.hour, picker.minute)
                    showTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showDueDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = dueAt ?: occurredAt + 30L * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDueDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dueAt = picker.selectedDateMillis
                    showDueDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDueDate = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(picker) }
    }
}

private fun mergeDayAndTime(dayStart: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayStart
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private val PAYMENT_METHODS = listOf("CASH", "BANK", "CARD", "WALLET", "CREDIT", "OTHER")
