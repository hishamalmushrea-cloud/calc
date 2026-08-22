package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.domain.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MoreScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    var shopName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var closeNotes by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onEvent(UiEvent.RefreshBackups) }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
        Section(stringResource(R.string.section_shops), initiallyExpanded = true) {
            state.shops.forEach { shop ->
                TextButton(onClick = { onEvent(UiEvent.SelectShop(shop)) }) {
                    Text(if (state.shop?.id == shop.id) "✓ ${shop.name}" else shop.name)
                }
            }
            OutlinedTextField(shopName, { shopName = it }, label = { Text(stringResource(R.string.new_shop_name)) })
            Button(onClick = {
                if (shopName.isNotBlank()) {
                    onEvent(UiEvent.AddShop(shopName))
                    shopName = ""
                }
            }) { Text(stringResource(R.string.create_shop)) }
        }

        Section(stringResource(R.string.section_day_close)) {
            Text(stringResource(R.string.expected_cash, Money(state.totals.cashNet).format()))
            OutlinedTextField(cash, { cash = it }, label = { Text(stringResource(R.string.actual_cash)) })
            OutlinedTextField(closeNotes, { closeNotes = it }, label = { Text(stringResource(R.string.notes)) })
            Button(onClick = { onEvent(UiEvent.CloseDay(cash, closeNotes)) }) {
                Text(stringResource(R.string.section_day_close))
            }
        }

        Section(stringResource(R.string.section_security)) {
            OutlinedTextField(
                pin,
                { pin = it },
                label = { Text(stringResource(R.string.pin)) },
                visualTransformation = PasswordVisualTransformation()
            )
            Row {
                Button(onClick = { onEvent(UiEvent.SavePin(pin)) }) { Text(stringResource(R.string.save_lock)) }
                TextButton(onClick = { onEvent(UiEvent.ClearPin) }) { Text(stringResource(R.string.remove_pin)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.biometric_on_open), modifier = Modifier.weight(1f))
                Switch(state.biometric, { onEvent(UiEvent.ToggleBiometric(it)) })
            }
        }

        Section(stringResource(R.string.section_backup)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.daily_auto_backup), modifier = Modifier.weight(1f))
                Switch(state.autoBackup, { onEvent(UiEvent.ToggleBackup(it)) })
            }
            Button(onClick = { onEvent(UiEvent.BackupNow) }) { Text(stringResource(R.string.backup_now_share)) }
            OutlinedTextField(
                backupPassword,
                { backupPassword = it },
                label = { Text(stringResource(R.string.backup_password)) },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(onClick = { onEvent(UiEvent.BackupEncrypted(backupPassword)) }) {
                Text(stringResource(R.string.encrypted_backup_share))
            }
            Button(onClick = { onEvent(UiEvent.ExportCsv) }) { Text(stringResource(R.string.export_csv_share)) }
            Spacer(Modifier.padding(4.dp))
            if (state.backups.isEmpty()) Text(stringResource(R.string.no_backups), style = MaterialTheme.typography.bodySmall)
            val backupFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
            state.backups.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        backupFormat.format(Date(file.lastModified())) + if (file.name.endsWith(".enc")) " 🔒" else "",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onEvent(UiEvent.RestoreBackup(file, backupPassword)) }) {
                        Text(stringResource(R.string.action_restore))
                    }
                }
            }
        }

        Section(stringResource(R.string.section_csv_import)) {
            Text(stringResource(R.string.csv_format_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(csv, { csv = it }, label = { Text(stringResource(R.string.paste_csv)) }, minLines = 4)
            Row {
                Button(onClick = { onEvent(UiEvent.PreviewCsv(csv)) }) { Text(stringResource(R.string.action_preview)) }
                TextButton(onClick = { onEvent(UiEvent.CommitCsv) }, enabled = state.csvPreview.isNotEmpty()) {
                    Text(stringResource(R.string.action_execute))
                }
            }
            val ready = stringResource(R.string.csv_ready)
            state.csvPreview.take(20).forEach { row ->
                Text(stringResource(R.string.csv_row, row.line, row.name, row.amount, row.error ?: ready))
            }
        }

        Section(stringResource(R.string.section_audit)) {
            if (state.audit.isEmpty()) Text(stringResource(R.string.no_audit), style = MaterialTheme.typography.bodySmall)
            val auditFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
            state.audit.take(30).forEach { row ->
                Text(
                    "${auditFormat.format(Date(row.at))} — ${row.action} ${row.entity} ${row.detail}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
