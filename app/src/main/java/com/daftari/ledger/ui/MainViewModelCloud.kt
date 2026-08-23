package com.daftari.ledger.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.R
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.saveCloudFolder(uri: String) {
    val app = getApplication<DaftariApp>()
    app.cloudBackup.saveTreeUri(Uri.parse(uri))
    mutableState.update { it.copy(cloudSettings = app.cloudBackup.settings(), message = text(R.string.msg_cloud_folder_saved)) }
}

internal fun MainViewModel.clearCloudFolder() {
    val app = getApplication<DaftariApp>()
    app.cloudBackup.clearTreeUri()
    mutableState.update { it.copy(cloudSettings = app.cloudBackup.settings()) }
}

internal fun MainViewModel.saveWebDav(url: String, user: String, password: String) {
    val app = getApplication<DaftariApp>()
    app.cloudBackup.saveWebDav(url, user, password)
    mutableState.update { it.copy(cloudSettings = app.cloudBackup.settings(), message = text(R.string.msg_webdav_saved)) }
}

internal fun MainViewModel.clearWebDav() {
    val app = getApplication<DaftariApp>()
    app.cloudBackup.clearWebDav()
    mutableState.update { it.copy(cloudSettings = app.cloudBackup.settings()) }
}

internal fun MainViewModel.cloudBackupNow() = viewModelScope.launch {
    try {
        val app = getApplication<DaftariApp>()
        val (_, result) = app.cloudBackup.backupNow()
        mutableState.update {
            it.copy(
                backups = app.backup.listBackups(),
                message = text(if (result.anyUploaded) R.string.msg_cloud_backup_ready else R.string.msg_cloud_not_configured)
            )
        }
    } catch (error: Exception) {
        message(R.string.msg_cloud_backup_failed, error.message.orEmpty())
    }
}

internal fun MainViewModel.restoreCloudFile(uri: String) = viewModelScope.launch {
    try {
        getApplication<DaftariApp>().cloudBackup.restoreFromUri(Uri.parse(uri))
        mutableState.update { it.copy(restartRequested = true, message = text(R.string.msg_restore_restarting)) }
    } catch (error: Exception) {
        message(R.string.msg_restore_failed, error.message.orEmpty())
    }
}

internal fun MainViewModel.restoreLatestWebDav() = viewModelScope.launch {
    try {
        getApplication<DaftariApp>().cloudBackup.restoreLatestWebDav()
        mutableState.update { it.copy(restartRequested = true, message = text(R.string.msg_restore_restarting)) }
    } catch (error: Exception) {
        message(R.string.msg_restore_failed, error.message.orEmpty())
    }
}
