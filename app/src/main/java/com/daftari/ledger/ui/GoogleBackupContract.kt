package com.daftari.ledger.ui

import com.daftari.ledger.backup.BackupRunStatus
import com.daftari.ledger.backup.GoogleBackupSettings
import com.daftari.ledger.backup.RemoteBackup

data class GoogleBackupUiState(
    val screenOpen: Boolean = false,
    val settings: GoogleBackupSettings = GoogleBackupSettings(),
    val remoteBackups: List<RemoteBackup> = emptyList(),
    val loading: Boolean = false,
    val action: String = "",
    val pendingRestore: RemoteBackup? = null,
    val pendingDelete: RemoteBackup? = null,
    val hasLocalData: Boolean = false
) {
    val status: BackupRunStatus get() = settings.status
}
