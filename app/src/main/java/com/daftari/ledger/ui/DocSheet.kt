package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.daftari.ledger.R
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DocumentSheet(
    state: UiState,
    onEvent: (UiEvent) -> Unit,
    initialType: DocType,
    existing: DocumentEntity? = null,
    initialParty: PartyEntity? = null,
    onDismiss: () -> Unit
) {
    val existingType = existing?.type?.let { runCatching { DocType.valueOf(it) }.getOrNull() } ?: initialType
    var type by remember { mutableStateOf(existingType) }
    var amount by remember {
        mutableStateOf(existing?.let { Money(it.amountMinor, state.shop?.fractionDigits ?: 2).toBigDecimal().toPlainString() }.orEmpty())
    }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var documentNumber by remember {
        mutableStateOf(existing?.docNumber ?: state.nextDocumentNumber.toString())
    }
    var credit by remember { mutableStateOf(existing?.paymentMethod == "CREDIT") }
    var occurredAt by remember { mutableStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var dueAt by remember {
        mutableStateOf(existing?.dueAt ?: (occurredAt + DEFAULT_DUE_DAYS * DAY_MILLIS).takeIf { credit })
    }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var party by remember {
        mutableStateOf(
            initialParty ?: existing?.partyId?.let { id ->
                (state.customers + state.suppliers).firstOrNull { it.id == id }
            }
        )
    }
    var partyQuery by remember { mutableStateOf(party?.name.orEmpty()) }
    var showDate by remember { mutableStateOf(false) }
    var showDueDate by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_document else R.string.edit_document)) },
        text = {
            Column {
                if (existing == null) {
                    DocumentTypeChips(type) { type = it }
                } else {
                    Text(
                        stringResource(R.string.document_type_value, documentTypeLabel(type.name)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.amount)) })
                if (type in PARTY_DOCUMENT_TYPES) {
                    OutlinedTextField(partyQuery, { partyQuery = it; party = null }, label = { Text(stringResource(R.string.name)) })
                    val pool = if (type in CUSTOMER_DOCUMENT_TYPES) state.customers else state.suppliers
                    val suggestions = if (partyQuery.isBlank()) {
                        pool.sortedByDescending { kotlin.math.abs(it.cachedBalanceMinor) }.take(5)
                    } else {
                        pool.filter { it.name.contains(partyQuery, true) || it.phone.contains(partyQuery, true) }.take(5)
                    }
                    if (partyQuery.isBlank() && suggestions.isNotEmpty()) {
                        Text(stringResource(R.string.quick_party_suggestions), style = MaterialTheme.typography.labelSmall)
                    }
                    suggestions.forEach { candidate ->
                        TextButton(onClick = { party = candidate; partyQuery = candidate.name }) {
                            Text(candidate.name + candidate.phone.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty())
                        }
                    }
                    val exact = pool.firstOrNull { it.name.equals(partyQuery.trim(), ignoreCase = true) }
                    if (partyQuery.isNotBlank() && exact == null && party == null) {
                        Text(
                            stringResource(
                                if (type in CUSTOMER_DOCUMENT_TYPES) R.string.new_customer_on_save
                                else R.string.new_supplier_on_save
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (type == DocType.SALE || type == DocType.PURCHASE) {
                    Row {
                        Text(stringResource(R.string.credit), modifier = Modifier.weight(1f))
                        Switch(credit, { enabled ->
                            credit = enabled
                            if (enabled && type == DocType.SALE && dueAt == null) {
                                dueAt = occurredAt + DEFAULT_DUE_DAYS * DAY_MILLIS
                            }
                            if (!enabled) dueAt = null
                        })
                    }
                    if (type == DocType.SALE && credit) {
                        val visibleDueAt = dueAt ?: (occurredAt + DEFAULT_DUE_DAYS * DAY_MILLIS)
                        TextButton(onClick = { showDueDate = true }) {
                            Text(stringResource(R.string.due_date_value, dateFormat.format(Date(visibleDueAt))))
                        }
                    }
                }
                if (type == DocType.EXPENSE || type == DocType.INCOME) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelMedium)
                    val kind = if (type == DocType.EXPENSE) "EXPENSE" else "INCOME"
                    FlowRow {
                        state.categories.filter { it.kind == kind }.forEach { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = if (categoryId == category.id) null else category.id },
                                label = { Text(category.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    documentNumber,
                    { documentNumber = it },
                    label = { Text(stringResource(R.string.document_number_optional)) }
                )
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) })
                TextButton(onClick = { showDate = true }) {
                    Text(stringResource(R.string.date_value, dateFormat.format(Date(occurredAt))))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pool = if (type in CUSTOMER_DOCUMENT_TYPES) state.customers else state.suppliers
                val matched = pool.firstOrNull { it.name.equals(partyQuery.trim(), ignoreCase = true) }
                val finalPartyId = party?.id ?: matched?.id
                val newPartyName = if (type in PARTY_DOCUMENT_TYPES && finalPartyId == null) {
                    partyQuery.trim().takeIf(String::isNotBlank)
                } else null
                val finalDueAt = if (type == DocType.SALE && credit) dueAt ?: occurredAt + DEFAULT_DUE_DAYS * DAY_MILLIS else null
                if (existing == null) {
                    onEvent(
                        UiEvent.AddDocument(
                            DocumentDraft(
                                type = type,
                                amount = amount,
                                partyId = finalPartyId,
                                credit = credit,
                                notes = notes,
                                documentNumber = documentNumber,
                                newPartyName = newPartyName,
                                occurredAt = occurredAt,
                                dueAt = finalDueAt,
                                categoryId = categoryId
                            )
                        )
                    )
                } else {
                    onEvent(
                        UiEvent.UpdateDocument(
                            existing.id,
                            amount,
                            notes,
                            documentNumber,
                            credit,
                            occurredAt,
                            finalDueAt,
                            categoryId,
                            finalPartyId,
                            newPartyName
                        )
                    )
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { occurredAt = combineWithCurrentTime(it) }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = dateState) }
    }
    if (showDueDate) {
        val dueState = rememberDatePickerState(initialSelectedDateMillis = dueAt)
        DatePickerDialog(
            onDismissRequest = { showDueDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dueState.selectedDateMillis?.let { dueAt = combineWithCurrentTime(it) }
                    showDueDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDueDate = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = dueState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentTypeChips(selected: DocType, onSelect: (DocType) -> Unit) {
    val types = listOf(
        DocType.SALE to stringResource(R.string.doc_type_sale),
        DocType.COLLECT to stringResource(R.string.doc_type_collect),
        DocType.PURCHASE to stringResource(R.string.doc_type_purchase),
        DocType.PAY to stringResource(R.string.doc_type_pay),
        DocType.EXPENSE to stringResource(R.string.doc_type_expense),
        DocType.INCOME to stringResource(R.string.doc_type_income),
        DocType.TRANSFER to stringResource(R.string.doc_type_transfer)
    )
    FlowRow {
        types.forEach { (type, label) ->
            FilterChip(selected == type, { onSelect(type) }, label = { Text(label) })
        }
    }
}

private const val DEFAULT_DUE_DAYS = 30L
private const val DAY_MILLIS = 86_400_000L
private val CUSTOMER_DOCUMENT_TYPES = setOf(DocType.SALE, DocType.COLLECT)
private val PARTY_DOCUMENT_TYPES = setOf(DocType.SALE, DocType.COLLECT, DocType.PURCHASE, DocType.PAY)
