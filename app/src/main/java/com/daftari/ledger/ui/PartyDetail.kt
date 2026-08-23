package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.data.DEFAULT_PARTY_CATEGORY
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind

@Composable
internal fun PartyDetail(
    state: UiState,
    onEvent: (UiEvent) -> Unit,
    onDismiss: () -> Unit,
    onQuick: (DocType) -> Unit
) {
    val party = state.selectedParty ?: return
    val stats = state.partyStats
    var editing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(party.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.labelMedium)
                Text(
                    displayMoney(party.cachedBalanceMinor),
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = partyBalanceColor(party)
                )
                if (party.category.isNotBlank() && party.category != DEFAULT_PARTY_CATEGORY) {
                    Text(stringResource(R.string.category_value, party.category), style = MaterialTheme.typography.bodySmall)
                }
                if (party.creditLimitMinor > 0) {
                    Text(
                        stringResource(R.string.credit_limit_value, displayMoney(party.creditLimitMinor)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (party.kind == PartyKind.CUSTOMER.name && party.cachedBalanceMinor >= party.creditLimitMinor) {
                        Text(stringResource(R.string.credit_limit_alert), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (party.phone.isNotBlank()) {
                    Text(stringResource(R.string.phone_value, party.phone), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { onEvent(UiEvent.CallPhone(party.phone)) }) {
                            Text(stringResource(R.string.action_call))
                        }
                        TextButton(onClick = { onEvent(UiEvent.OpenWhatsApp(party.phone)) }) {
                            Text(stringResource(R.string.action_whatsapp))
                        }
                    }
                }
                if (stats == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.loading))
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sales_value, displayMoney(stats.sales)))
                    Text(stringResource(R.string.collections_value, displayMoney(stats.collections)))
                    Text(stringResource(R.string.collection_rate, stats.collectionRate))
                    if (party.kind == PartyKind.SUPPLIER.name) {
                        Text(stringResource(R.string.purchases_value, displayMoney(stats.purchases)))
                        Text(stringResource(R.string.payments_value, displayMoney(stats.payments)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.recent_operations), fontWeight = FontWeight.Bold)
                    if (stats.statementLines.isEmpty()) Text(stringResource(R.string.no_operations_yet))
                    stats.statementLines.takeLast(5).asReversed().forEach { line ->
                        Text(
                            stringResource(
                                R.string.statement_line_running,
                                documentTypeLabel(line.document.type),
                                displayMoney(line.document.amountMinor),
                                displayMoney(line.runningBalanceMinor)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.quick_action), fontWeight = FontWeight.Bold)
                Row {
                    if (party.kind == PartyKind.CUSTOMER.name) {
                        TextButton(onClick = { onQuick(DocType.SALE) }) { Text(stringResource(R.string.doc_type_sale)) }
                        TextButton(onClick = { onQuick(DocType.COLLECT) }) { Text(stringResource(R.string.doc_type_collect)) }
                    } else {
                        TextButton(onClick = { onQuick(DocType.PURCHASE) }) { Text(stringResource(R.string.doc_type_purchase)) }
                        TextButton(onClick = { onQuick(DocType.PAY) }) { Text(stringResource(R.string.doc_type_pay)) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(UiEvent.ShareStatement(party)) }) {
                Text(stringResource(R.string.action_share_statement))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = { onEvent(UiEvent.CloseParty(party.id)); onDismiss() }) {
                    Text(stringResource(R.string.close_account))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    )
    if (editing) PartyEditDialog(party, onEvent) { editing = false }
}

@Composable
private fun PartyEditDialog(party: PartyEntity, onEvent: (UiEvent) -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(party.category) }
    var limit by remember {
        mutableStateOf(if (party.creditLimitMinor == 0L) "" else Money(party.creditLimitMinor).toBigDecimal().toPlainString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_party_title, party.name)) },
        text = {
            Column {
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category)) })
                OutlinedTextField(limit, { limit = it }, label = { Text(stringResource(R.string.credit_limit)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                onEvent(UiEvent.UpdateParty(party.id, category, limit))
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
