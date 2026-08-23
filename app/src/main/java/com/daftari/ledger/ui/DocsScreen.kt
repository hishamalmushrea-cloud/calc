package com.daftari.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocsScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    var editing by remember { mutableStateOf<DocumentEntity?>(null) }
    var pendingArchive by remember { mutableStateOf<DocumentEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val dayFormat = remember { SimpleDateFormat("EEEE dd MMMM", Locale.getDefault()) }
    val labels = mapOf(
        "SALE" to stringResource(R.string.doc_type_sale),
        "COLLECT" to stringResource(R.string.doc_type_collect),
        "PURCHASE" to stringResource(R.string.doc_type_purchase),
        "EXPENSE" to stringResource(R.string.doc_type_expense),
        "PAY" to stringResource(R.string.doc_type_pay)
    )
    val byType = if (typeFilter == null) state.docs else state.docs.filter { it.type == typeFilter }
    val filtered = if (query.isBlank()) byType else byType.filter { document ->
        document.notes.contains(query, true) ||
            document.docNumber.contains(query, true) ||
            labels[document.type].orEmpty().contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            FilterChip(typeFilter == null, { typeFilter = null }, label = { Text(stringResource(R.string.all)) })
            labels.forEach { (code, label) ->
                FilterChip(
                    selected = typeFilter == code,
                    onClick = { typeFilter = if (typeFilter == code) null else code },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_operations_period), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(if (query.isBlank()) R.string.add_operation_hint else R.string.no_matching_results),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            filtered.groupBy { dayFormat.format(Date(it.occurredAt)) }.forEach { (day, docs) ->
                item {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                itemsIndexed(docs, key = { _, document -> document.id }) { index, document ->
                    val color = documentColor(document.type)
                    AnimatedCard(index, Modifier.fillMaxWidth().padding(vertical = 3.dp).pulseOnClick()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { Text(documentIcon(document.type), fontSize = 18.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(documentTypeLabel(document.type), fontWeight = FontWeight.SemiBold)
                                Text(
                                    dateFormat.format(Date(document.occurredAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (document.notes.isNotBlank()) Text(document.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(displayMoney(document.amountMinor), fontWeight = FontWeight.Bold, color = color)
                                Row {
                                    TextButton(
                                        onClick = { onEvent(UiEvent.ShareReceipt(document)) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) { Text(stringResource(R.string.receipt_pdf), style = MaterialTheme.typography.labelSmall) }
                                    TextButton(
                                        onClick = { editing = document },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) { Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelSmall) }
                                    TextButton(
                                        onClick = { pendingArchive = document },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.action_archive),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { document ->
        DocumentSheet(state, onEvent, DocType.SALE, document) { editing = null }
    }
    pendingArchive?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.archive_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    onEvent(UiEvent.DeleteDocument(document.id))
                    pendingArchive = null
                }) { Text(stringResource(R.string.action_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
