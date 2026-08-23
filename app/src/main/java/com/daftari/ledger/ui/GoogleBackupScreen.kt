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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.backup.BackupRunStatus
import com.daftari.ledger.backup.RemoteBackup
import java.text.DateFormat
import java.util.Date

@Composable
internal fun GoogleBackupScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val backup = state.googleBackup
    val settings = backup.settings
    LazyColumn(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEvent(UiEvent.CloseGoogleBackup) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.google_backup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.google_account), fontWeight = FontWeight.Bold)
                    if (settings.linked) {
                        Text(settings.accountEmail)
                        Text(backupStatusLabel(settings.status), color = backupStatusColor(settings.status))
                        if (settings.lastSuccessAt > 0) {
                            Text(stringResource(R.string.last_backup_value, DateFormat.getDateTimeInstance().format(Date(settings.lastSuccessAt))))
                            Text(stringResource(R.string.backup_size_value, formatBytes(settings.lastSize)))
                        }
                        Row {
                            OutlinedButton(onClick = { onEvent(UiEvent.RefreshGoogleBackups) }) { Text(stringResource(R.string.action_refresh)) }
                            TextButton(onClick = { onEvent(UiEvent.UnlinkGoogleBackup) }) { Text(stringResource(R.string.unlink_google_account)) }
                        }
                    } else {
                        Text(stringResource(R.string.google_backup_link_hint))
                        Button(onClick = { onEvent(UiEvent.LinkGoogleBackup) }) { Text(stringResource(R.string.link_google_account)) }
                    }
                }
            }
        }
        if (settings.linked) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEvent(UiEvent.GoogleBackupNow) },
                            enabled = !backup.loading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(if (backup.loading) R.string.backup_in_progress else R.string.google_backup_now)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.automatic_daily_backup), modifier = Modifier.weight(1f))
                            Switch(settings.automaticEnabled, { onEvent(UiEvent.SetGoogleBackupAutomatic(it)) })
                        }
                        Text(stringResource(R.string.backup_network))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(settings.wifiOnly, { onEvent(UiEvent.SetGoogleBackupWifiOnly(true)) }, label = { Text(stringResource(R.string.wifi_only)) })
                            FilterChip(!settings.wifiOnly, { onEvent(UiEvent.SetGoogleBackupWifiOnly(false)) }, label = { Text(stringResource(R.string.wifi_and_mobile)) })
                        }
                        if (settings.lastError.isNotBlank()) {
                            Text(settings.lastError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.available_backups), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (backup.remoteBackups.isEmpty() && !backup.loading) {
                item { Text(stringResource(R.string.no_google_backups)) }
            }
            items(backup.remoteBackups, key = { it.id }) { remote ->
                RemoteBackupCard(remote, onRestore = { onEvent(UiEvent.PrepareGoogleRestore(remote)) }, onDelete = { onEvent(UiEvent.PrepareGoogleBackupDelete(remote)) })
            }
        }
    }

    backup.pendingRestore?.let { remote ->
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.PrepareGoogleRestore(null)) },
            title = { Text(stringResource(R.string.restore_backup_question)) },
            text = {
                Text(
                    if (backup.hasLocalData) stringResource(R.string.restore_replaces_local_warning, formatRemoteDate(remote))
                    else stringResource(R.string.restore_empty_device_message, formatRemoteDate(remote))
                )
            },
            confirmButton = { Button(onClick = { onEvent(UiEvent.ConfirmGoogleRestore) }) { Text(stringResource(R.string.action_restore)) } },
            dismissButton = { TextButton(onClick = { onEvent(UiEvent.PrepareGoogleRestore(null)) }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    backup.pendingDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.PrepareGoogleBackupDelete(null)) },
            title = { Text(stringResource(R.string.delete_cloud_backup_question)) },
            text = { Text(stringResource(R.string.delete_cloud_backup_warning, formatRemoteDate(remote))) },
            confirmButton = { Button(onClick = { onEvent(UiEvent.ConfirmGoogleBackupDelete) }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { onEvent(UiEvent.PrepareGoogleBackupDelete(null)) }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun RemoteBackupCard(remote: RemoteBackup, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatRemoteDate(remote), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.backup_device_value, remote.deviceName.ifBlank { stringResource(R.string.unknown_device) }))
            Text(stringResource(R.string.backup_size_value, formatBytes(remote.size)))
            Text(stringResource(R.string.database_version_value, remote.databaseVersion))
            if (remote.conflict) Text(stringResource(R.string.backup_conflict_label), color = MaterialTheme.colorScheme.error)
            if (!remote.valid) Text(stringResource(R.string.backup_invalid_label), color = MaterialTheme.colorScheme.error)
            Row {
                Button(onClick = onRestore, enabled = remote.valid) { Text(stringResource(R.string.action_restore)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_remove)) }
            }
        }
    }
}

@Composable private fun backupStatusLabel(status: BackupRunStatus): String = stringResource(when (status) {
    BackupRunStatus.DISCONNECTED -> R.string.backup_status_disconnected
    BackupRunStatus.IDLE -> R.string.backup_status_ready
    BackupRunStatus.WAITING -> R.string.backup_status_waiting
    BackupRunStatus.RUNNING -> R.string.backup_in_progress
    BackupRunStatus.SUCCESS -> R.string.backup_status_success
    BackupRunStatus.FAILED -> R.string.backup_status_failed
    BackupRunStatus.AUTH_REQUIRED -> R.string.backup_status_auth_required
    BackupRunStatus.CONFLICT -> R.string.backup_conflict_label
})

@Composable private fun backupStatusColor(status: BackupRunStatus) = when (status) {
    BackupRunStatus.SUCCESS, BackupRunStatus.IDLE -> MaterialTheme.colorScheme.primary
    BackupRunStatus.FAILED, BackupRunStatus.AUTH_REQUIRED, BackupRunStatus.CONFLICT -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatRemoteDate(remote: RemoteBackup): String = DateFormat.getDateTimeInstance().format(Date(remote.createdAt))
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
