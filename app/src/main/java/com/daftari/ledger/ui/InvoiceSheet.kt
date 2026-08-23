package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.daftari.ledger.R
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.InventoryMath
import com.daftari.ledger.domain.Money

@Composable
internal fun InvoiceSheet(state: UiState, onEvent: (UiEvent) -> Unit) {
    val sale = state.inventory.invoiceType == DocType.SALE
    val digits = state.shop?.fractionDigits ?: 2
    var partyQuery by remember { mutableStateOf("") }
    var partyId by remember { mutableStateOf<Long?>(null) }
    var credit by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var documentNumber by remember { mutableStateOf(state.nextDocumentNumber.toString()) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf<InvoiceLineDraft>()) }
    val parties = if (sale) state.customers else state.suppliers
    val total = lines.sumOf { line ->
        val q = InventoryMath.parseQty(line.qty) ?: 0L
        val p = Money.fromMajor(line.unitPrice, digits)?.minor ?: 0L
        runCatching { InventoryMath.lineTotal(q, p) }.getOrDefault(0L)
    }

    AlertDialog(
        onDismissRequest = { onEvent(UiEvent.SetInvoiceSheet(false)) },
        title = { Text(stringResource(if (sale) R.string.new_sale_invoice else R.string.new_purchase_invoice)) },
        text = {
            Column {
                Text(stringResource(R.string.invoice_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    partyQuery,
                    { partyQuery = it; partyId = null },
                    label = { Text(stringResource(if (sale) R.string.customer else R.string.supplier)) },
                    modifier = Modifier.fillMaxWidth()
                )
                parties.filter { partyQuery.isBlank() || it.name.contains(partyQuery, true) }.take(4).forEach { party ->
                    TextButton(onClick = { partyId = party.id; partyQuery = party.name }) { Text(party.name) }
                }
                Row {
                    Text(stringResource(R.string.credit), modifier = Modifier.weight(1f))
                    Switch(credit, { credit = it })
                }
                Text(stringResource(R.string.add_invoice_line), style = MaterialTheme.typography.labelMedium)
                state.inventory.items.take(8).forEach { item ->
                    FilterChip(
                        selected = selectedItemId == item.id,
                        onClick = {
                            selectedItemId = item.id
                            price = Money(if (sale) item.sellPriceMinor else item.costPriceMinor, digits).toBigDecimal().toPlainString()
                        },
                        label = { Text(item.name) }
                    )
                }
                OutlinedTextField(qty, { qty = it }, label = { Text(stringResource(R.string.item_qty)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.item_sell_price)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                TextButton(
                    enabled = selectedItemId != null && qty.isNotBlank() && price.isNotBlank(),
                    onClick = {
                        val item = state.inventory.items.firstOrNull { it.id == selectedItemId } ?: return@TextButton
                        lines = lines + InvoiceLineDraft(item.id, item.name, qty, price)
                        qty = "1"
                    }
                ) { Text(stringResource(R.string.add_line)) }
                lines.forEachIndexed { index, line ->
                    Text("${line.itemName} × ${line.qty} — ${line.unitPrice}")
                    TextButton(onClick = { lines = lines.toMutableList().also { it.removeAt(index) } }) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
                Text(stringResource(R.string.invoice_total, displayMoney(total)), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(documentNumber, { documentNumber = it }, label = { Text(stringResource(R.string.document_number_optional)) })
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_optional)) })
                if (credit && partyId == null && partyQuery.isBlank()) {
                    Text(stringResource(R.string.party_required_for_document), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val canSave = lines.isNotEmpty() && (!credit || partyId != null || partyQuery.isNotBlank())
            Button(
                enabled = canSave,
                onClick = {
                    onEvent(
                        UiEvent.SaveInvoice(
                            InvoiceDraft(
                                type = state.inventory.invoiceType,
                                partyId = partyId,
                                newPartyName = if (partyId == null) partyQuery.trim().takeIf { it.isNotBlank() } else null,
                                credit = credit,
                                notes = notes,
                                documentNumber = documentNumber,
                                dueAt = if (credit && sale) System.currentTimeMillis() + 30L * 86_400_000L else null,
                                lines = lines
                            )
                        )
                    )
                }
            ) { Text(stringResource(R.string.save_invoice)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(UiEvent.SetInvoiceSheet(false)) }) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
