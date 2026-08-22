package com.daftari.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.data.DEFAULT_PARTY_CATEGORY
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.PartyKind

@Composable
internal fun PartiesScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    var customersTab by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Row {
            FilterChip(customersTab, { customersTab = true }, label = { Text(stringResource(R.string.customers)) })
            Spacer(Modifier.width(8.dp))
            FilterChip(!customersTab, { customersTab = false }, label = { Text(stringResource(R.string.suppliers)) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.action_add)) }
        }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(R.string.search_name_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val source = if (customersTab) state.customers else state.suppliers
        val parties = if (query.isBlank()) source else source.filter {
            it.name.contains(query, true) || it.phone.contains(query, true)
        }
        if (parties.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (customersTab) R.string.no_customers else R.string.no_suppliers),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.add_first_account_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn {
            itemsIndexed(parties, key = { _, party -> party.id }) { index, party ->
                PartyCard(
                    party,
                    index,
                    onClick = { onEvent(UiEvent.OpenParty(party)) },
                    onCall = { onEvent(UiEvent.CallPhone(party.phone)) },
                    onWhatsApp = { onEvent(UiEvent.OpenWhatsApp(party.phone)) }
                )
            }
        }
    }
    if (showAdd) PartyDialog(customersTab, onEvent) { showAdd = false }
}

@Composable
private fun PartyCard(
    party: PartyEntity,
    index: Int,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val balanceColor = partyBalanceColor(party)
    val ratio = if (party.creditLimitMinor > 0) {
        (party.cachedBalanceMinor.toFloat() / party.creditLimitMinor).coerceIn(0f, 1.2f)
    } else 0f
    val limitColor = when {
        ratio >= 1f -> MaterialTheme.colorScheme.error
        ratio >= 0.8f -> Color(0xFFD4A84B)
        else -> MaterialTheme.colorScheme.primary
    }
    AnimatedCard(
        index = index,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pulseOnClick().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(balanceColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(party.name.take(1), fontWeight = FontWeight.Bold, color = balanceColor, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(party.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (party.category.isNotBlank() && party.category != DEFAULT_PARTY_CATEGORY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        Spacer(Modifier.width(4.dp))
                        Text(party.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (party.creditLimitMinor > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = ratio.coerceAtMost(1f),
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = limitColor,
                            trackColor = limitColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = limitColor)
                    }
                }
                if (party.phone.isNotBlank()) {
                    Text(party.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = onCall, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(R.string.action_call), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = onWhatsApp, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(R.string.action_whatsapp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(party.cachedBalanceMinor).format(), fontWeight = FontWeight.Bold, color = balanceColor, fontSize = 16.sp)
                Text(
                    stringResource(if (party.kind == PartyKind.CUSTOMER.name) R.string.party_you_are_owed else R.string.party_you_owe),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PartyDialog(customer: Boolean, onEvent: (UiEvent) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (customer) R.string.customer_new else R.string.supplier_new)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) })
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone_optional)) })
                OutlinedTextField(opening, { opening = it }, label = { Text(stringResource(R.string.opening_balance)) })
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category_optional)) })
                OutlinedTextField(limit, { limit = it }, label = { Text(stringResource(R.string.credit_limit_optional)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                onEvent(
                    UiEvent.AddParty(
                        if (customer) PartyKind.CUSTOMER else PartyKind.SUPPLIER,
                        name,
                        phone,
                        opening,
                        category,
                        limit
                    )
                )
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
