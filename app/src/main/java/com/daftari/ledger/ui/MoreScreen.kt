package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.daftari.ledger.domain.StaffPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CloudRestoreKind { FILE, WEBDAV }

@Composable
internal fun MoreScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    var shopName by remember { mutableStateOf("") }
    var currencyCode by remember(state.shop?.currencyCode) { mutableStateOf(state.shop?.currencyCode.orEmpty()) }
    var categoryName by remember { mutableStateOf("") }
    var categoryKind by remember { mutableStateOf("EXPENSE") }
    var pin by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var closeNotes by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var webDavUrl by remember(state.cloudSettings.webDavUrl) { mutableStateOf(state.cloudSettings.webDavUrl) }
    var webDavUser by remember(state.cloudSettings.webDavUser) { mutableStateOf(state.cloudSettings.webDavUser) }
    var webDavPassword by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<java.io.File?>(null) }
    var pendingCloudRestore by remember { mutableStateOf<CloudRestoreKind?>(null) }
    LaunchedEffect(Unit) { onEvent(UiEvent.RefreshBackups) }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.more_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(6.dp))

        if (state.can(StaffPermission.MANAGE_SETTINGS)) {
            Section(stringResource(R.string.section_shops), initiallyExpanded = true) {
                Text(stringResource(R.string.section_shops_hint), style = MaterialTheme.typography.bodySmall)
                state.shops.forEach { shop ->
                    TextButton(onClick = { onEvent(UiEvent.SelectShop(shop)) }) {
                        Text(if (state.shop?.id == shop.id) "✓ ${shop.name}" else shop.name)
                    }
                }
                OutlinedTextField(shopName, { shopName = it }, label = { Text(stringResource(R.string.new_shop_name)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (shopName.isNotBlank()) {
                        onEvent(UiEvent.AddShop(shopName))
                        shopName = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_shop)) }
                OutlinedTextField(
                    currencyCode,
                    { currencyCode = it.uppercase().take(3) },
                    label = { Text(stringResource(R.string.currency_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onEvent(UiEvent.UpdateCurrency(currencyCode)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_currency))
                }
            }
        }

        if (state.can(StaffPermission.VIEW_ACCOUNTS) || state.can(StaffPermission.RECORD_SALE)) {
            Section(stringResource(R.string.section_inventory), initiallyExpanded = true) {
                Text(stringResource(R.string.inventory_hint), style = MaterialTheme.typography.bodySmall)
                if (state.inventory.lowStockCount > 0) {
                    Text(stringResource(R.string.low_stock_alert, state.inventory.lowStockCount), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { onEvent(UiEvent.OpenInventory) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_inventory))
                }
            }
        }

        Section(stringResource(R.string.section_employees), initiallyExpanded = state.employees.enabled) {
            if (state.can(StaffPermission.MANAGE_EMPLOYEES)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.enable_employee_system), modifier = Modifier.weight(1f))
                    Switch(state.employees.enabled, { onEvent(UiEvent.SetEmployeesEnabled(it)) })
                }
            }
            if (state.employees.enabled) {
                Text(
                    stringResource(
                        R.string.current_user_value,
                        state.employees.currentEmployee?.name ?: stringResource(R.string.owner_mode)
                    )
                )
                Button(onClick = { onEvent(UiEvent.SetEmployeeSwitcher(true)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.switch_employee))
                }
                if (state.can(StaffPermission.MANAGE_EMPLOYEES) || state.can(StaffPermission.VIEW_REPORTS)) {
                    Button(onClick = { onEvent(UiEvent.OpenEmployees) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.manage_employees))
                    }
                }
            } else {
                Text(stringResource(R.string.employee_system_optional_hint), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.can(StaffPermission.MANAGE_SHIFTS)) {
            Section(stringResource(R.string.section_day_close)) {
                Text(stringResource(R.string.expected_cash, displayMoney(state.totals.cashNet)))
                OutlinedTextField(cash, { cash = it }, label = { Text(stringResource(R.string.actual_cash)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(closeNotes, { closeNotes = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onEvent(UiEvent.CloseDay(cash, closeNotes)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.section_day_close))
                }
            }
        }

        if (state.can(StaffPermission.MANAGE_SETTINGS)) {
            Section(stringResource(R.string.section_security)) {
                OutlinedTextField(
                    pin,
                    { pin = it },
                    label = { Text(stringResource(R.string.pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Button(onClick = { onEvent(UiEvent.SavePin(pin)) }) { Text(stringResource(R.string.save_lock)) }
                    TextButton(onClick = { onEvent(UiEvent.ClearPin) }) { Text(stringResource(R.string.remove_pin)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.biometric_on_open), modifier = Modifier.weight(1f))
                    Switch(state.biometric, { onEvent(UiEvent.ToggleBiometric(it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.privacy_mode), modifier = Modifier.weight(1f))
                    Switch(state.hideBalances, { onEvent(UiEvent.TogglePrivacy(it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.latin_digits), modifier = Modifier.weight(1f))
                    Switch(state.latinDigits, { onEvent(UiEvent.ToggleLatinDigits(it)) })
                }
            }

            Section(stringResource(R.string.section_categories)) {
                Row {
                    TextButton(onClick = { categoryKind = "EXPENSE" }) {
                        Text((if (categoryKind == "EXPENSE") "✓ " else "") + stringResource(R.string.expenses))
                    }
                    TextButton(onClick = { categoryKind = "INCOME" }) {
                        Text((if (categoryKind == "INCOME") "✓ " else "") + stringResource(R.string.other_income))
                    }
                }
                OutlinedTextField(
                    categoryName,
                    { categoryName = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    if (categoryName.isNotBlank()) {
                        onEvent(UiEvent.AddCategory(categoryKind, categoryName))
                        categoryName = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_category)) }
                state.categories.forEach { category -> Text("• ${category.name}") }
            }

            Section(stringResource(R.string.section_backup_hub), initiallyExpanded = true) {
                Text(stringResource(R.string.section_backup_hub_hint), style = MaterialTheme.typography.bodySmall)
                val google = state.googleBackup.settings
                if (google.linked) {
                    Text(stringResource(R.string.google_account_value, google.accountEmail))
                    if (google.lastSuccessAt > 0) {
                        Text(stringResource(R.string.last_backup_value, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(google.lastSuccessAt))))
                    }
                } else {
                    Text(stringResource(R.string.google_backup_link_hint), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onEvent(UiEvent.OpenGoogleBackup) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (google.linked) R.string.manage_google_backup else R.string.restore_my_data))
                }
            }

            Section(stringResource(R.string.section_backup)) {
                Text(stringResource(R.string.section_local_backup_hint), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.daily_auto_backup), modifier = Modifier.weight(1f))
                    Switch(state.autoBackup, { onEvent(UiEvent.ToggleBackup(it)) })
                }
                Button(onClick = { onEvent(UiEvent.BackupNow) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_now_share)) }
                OutlinedTextField(
                    backupPassword,
                    { backupPassword = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onEvent(UiEvent.BackupEncrypted(backupPassword)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.encrypted_backup_share))
                }
                OutlinedButton(onClick = { onEvent(UiEvent.ExportCsv) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_csv_share))
                }
                Spacer(Modifier.padding(4.dp))
                if (state.backups.isEmpty()) Text(stringResource(R.string.no_backups), style = MaterialTheme.typography.bodySmall)
                val backupFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
                state.backups.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            backupFormat.format(Date(file.lastModified())) + if (file.name.endsWith(".enc")) " 🔒" else "",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { pendingRestore = file }) {
                            Text(stringResource(R.string.action_restore))
                        }
                    }
                }
            }

            Section(stringResource(R.string.section_cloud_backup)) {
                Text(stringResource(R.string.section_optional_cloud_hint), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(
                        if (state.cloudSettings.treeUri.isBlank()) R.string.cloud_folder_not_selected
                        else R.string.cloud_folder_selected
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Row {
                    Button(onClick = { onEvent(UiEvent.ChooseCloudFolder) }) {
                        Text(stringResource(R.string.choose_cloud_folder))
                    }
                    if (state.cloudSettings.treeUri.isNotBlank()) {
                        TextButton(onClick = { onEvent(UiEvent.ClearCloudFolder) }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                OutlinedTextField(webDavUrl, { webDavUrl = it }, label = { Text(stringResource(R.string.webdav_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webDavUser, { webDavUser = it }, label = { Text(stringResource(R.string.webdav_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    webDavPassword,
                    { webDavPassword = it },
                    label = { Text(stringResource(R.string.webdav_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Button(onClick = { onEvent(UiEvent.SaveWebDav(webDavUrl, webDavUser, webDavPassword)) }) {
                        Text(stringResource(R.string.action_save))
                    }
                    if (state.cloudSettings.webDavUrl.isNotBlank()) {
                        TextButton(onClick = { onEvent(UiEvent.ClearWebDav) }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
                Button(onClick = { onEvent(UiEvent.CloudBackupNow) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cloud_backup_now))
                }
                OutlinedButton(onClick = { pendingCloudRestore = CloudRestoreKind.FILE }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.restore_cloud_file))
                }
                OutlinedButton(
                    onClick = { pendingCloudRestore = CloudRestoreKind.WEBDAV },
                    enabled = state.cloudSettings.webDavUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.restore_latest_webdav)) }
            }
        }

        if (state.can(StaffPermission.MANAGE_SETTINGS)) {
            Section(stringResource(R.string.section_database_health)) {
                Text(stringResource(R.string.database_health_hint), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onEvent(UiEvent.RunDatabaseHealthCheck) }) {
                        Text(stringResource(R.string.database_health_run))
                    }
                    if (state.healthCheckedAt > 0) {
                        TextButton(onClick = { onEvent(UiEvent.ClearDatabaseHealthCheck) }) {
                            Text(stringResource(R.string.database_health_clear))
                        }
                    }
                }
                if (state.healthCheckedAt > 0) {
                    val healthFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
                    Text(healthFormat.format(Date(state.healthCheckedAt)), style = MaterialTheme.typography.bodySmall)
                    if (state.healthIssues.isEmpty()) {
                        Text(
                            stringResource(R.string.database_health_ok),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        state.healthIssues.forEach { issue ->
                            Text(
                                stringResource(R.string.database_health_issue_row, issue.message, issue.count),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (state.can(StaffPermission.VIEW_ACCOUNTS)) {
            Section(stringResource(R.string.section_csv_import)) {
                Text(stringResource(R.string.csv_format_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(csv, { csv = it }, label = { Text(stringResource(R.string.paste_csv)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
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
        }

        pendingRestore?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.restore_confirm_body))
                        if (file.name.endsWith(".enc")) {
                            Text(stringResource(R.string.restore_encrypted_password_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onEvent(UiEvent.RestoreBackup(file, backupPassword))
                        pendingRestore = null
                    }) { Text(stringResource(R.string.action_restore)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        pendingCloudRestore?.let { kind ->
            AlertDialog(
                onDismissRequest = { pendingCloudRestore = null },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = { Text(stringResource(if (kind == CloudRestoreKind.WEBDAV) R.string.restore_webdav_confirm_body else R.string.restore_cloud_file_confirm_body)) },
                confirmButton = {
                    Button(onClick = {
                        if (kind == CloudRestoreKind.WEBDAV) onEvent(UiEvent.RestoreLatestWebDav)
                        else onEvent(UiEvent.ChooseCloudRestoreFile)
                        pendingCloudRestore = null
                    }) { Text(stringResource(R.string.action_restore)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloudRestore = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        if (state.can(StaffPermission.VIEW_AUDIT)) {
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
}
