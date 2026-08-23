package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.data.ItemEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.InventoryMath
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission

@Composable
internal fun InventoryScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    var editing by remember { mutableStateOf<ItemEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val canManage = state.can(StaffPermission.MANAGE_SETTINGS)
    LazyColumn(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEvent(UiEvent.CloseInventory) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.inventory_hint), style = MaterialTheme.typography.bodySmall)
            if (state.inventory.lowStockCount > 0) {
                Text(
                    stringResource(R.string.low_stock_alert, state.inventory.lowStockCount),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onEvent(UiEvent.SetInvoiceSheet(true, DocType.SALE)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_sale_invoice))
                }
                OutlinedButton(onClick = { onEvent(UiEvent.SetInvoiceSheet(true, DocType.PURCHASE)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_purchase_invoice))
                }
            }
            if (canManage) {
                Button(onClick = { adding = true; editing = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_item))
                }
            }
        }
        if (state.inventory.items.isEmpty()) {
            item { Text(stringResource(R.string.no_items), style = MaterialTheme.typography.bodyMedium) }
        }
        items(state.inventory.items, key = { it.id }) { item ->
            val low = item.trackStock && item.qtyMilli <= item.reorderQtyMilli
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    if (item.sku.isNotBlank()) Text(stringResource(R.string.item_sku_value, item.sku), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.item_price_value, displayMoney(item.sellPriceMinor)))
                    if (item.trackStock) {
                        Text(
                            stringResource(R.string.item_qty_value, InventoryMath.formatQty(item.qtyMilli), item.unit),
                            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(stringResource(R.string.item_service), style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        if (canManage) {
                            TextButton(onClick = { editing = item; adding = false }) { Text(stringResource(R.string.action_edit)) }
                            TextButton(onClick = { onEvent(UiEvent.ArchiveItem(item.id)) }) { Text(stringResource(R.string.action_archive)) }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        ItemEditorDialog(
            state = state,
            existing = editing,
            onDismiss = { adding = false; editing = null },
            onSave = {
                onEvent(UiEvent.SaveItem(it))
                adding = false
                editing = null
            }
        )
    }
    if (state.inventory.invoiceOpen) {
        InvoiceSheet(state, onEvent)
    }
}

@Composable
private fun ItemEditorDialog(
    state: UiState,
    existing: ItemEntity?,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit
) {
    val digits = state.shop?.fractionDigits ?: 2
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var sku by remember { mutableStateOf(existing?.sku.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: "قطعة") }
    var sell by remember { mutableStateOf(existing?.let { Money(it.sellPriceMinor, digits).toBigDecimal().toPlainString() }.orEmpty()) }
    var cost by remember { mutableStateOf(existing?.let { Money(it.costPriceMinor, digits).toBigDecimal().toPlainString() }.orEmpty()) }
    var qty by remember { mutableStateOf(existing?.let { InventoryMath.formatQty(it.qtyMilli) } ?: "0") }
    var reorder by remember { mutableStateOf(existing?.let { InventoryMath.formatQty(it.reorderQtyMilli) } ?: "0") }
    var track by remember { mutableStateOf(existing?.trackStock ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_item else R.string.edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.item_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sku, { sku = it }, label = { Text(stringResource(R.string.item_sku_optional)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text(stringResource(R.string.item_unit)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sell, { sell = it }, label = { Text(stringResource(R.string.item_sell_price)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cost, { cost = it }, label = { Text(stringResource(R.string.item_cost_price)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.item_track_stock), modifier = Modifier.weight(1f))
                    Switch(track, { track = it })
                }
                if (track) {
                    OutlinedTextField(qty, { qty = it }, label = { Text(stringResource(R.string.item_qty)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(reorder, { reorder = it }, label = { Text(stringResource(R.string.item_reorder_qty)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && sell.isNotBlank(),
                onClick = {
                    onSave(
                        ItemDraft(
                            id = existing?.id,
                            name = name,
                            sku = sku,
                            unit = unit,
                            sellPrice = sell,
                            costPrice = cost,
                            qty = if (track) qty else "0",
                            reorderQty = if (track) reorder else "0",
                            trackStock = track
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
