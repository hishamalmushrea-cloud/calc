package com.daftari.ledger.ui

import androidx.lifecycle.viewModelScope
import com.daftari.ledger.DaftariApp
import com.daftari.ledger.R
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.backup.BackupRunStatus
import com.daftari.ledger.backup.DriveBackupException
import com.daftari.ledger.domain.StaffPermission
import java.io.IOException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.openGoogleBackup() {
    if (!state.value.can(StaffPermission.MANAGE_SETTINGS)) return
    val manager = getApplication<DaftariApp>().googleBackup
    mutableState.update {
        it.copy(googleBackup = it.googleBackup.copy(
            screenOpen = true,
            settings = manager.preferences.load(),
            hasLocalData = manager.hasLocalData()
        ))
    }
    if (manager.preferences.load().linked) requestGoogleAuthorization("LIST")
}

internal fun MainViewModel.closeGoogleBackup() {
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(screenOpen = false, pendingRestore = null, pendingDelete = null)) }
}

internal fun MainViewModel.linkGoogleBackup() {
    if (!state.value.can(StaffPermission.MANAGE_SETTINGS)) return
    mutableEffects.tryEmit(UiEffect.LinkGoogleBackup())
}

internal fun MainViewModel.unlinkGoogleBackup() {
    if (!state.value.can(StaffPermission.MANAGE_SETTINGS)) return
    val app = getApplication<DaftariApp>()
    app.googleBackup.preferences.unlink()
    viewModelScope.launch {
        val legacy = runCatching { repo.settings.get()?.autoBackupEnabled == true }.getOrDefault(false)
        AutoBackupWorker.schedule(app, legacy)
    }
    mutableState.update {
        it.copy(googleBackup = it.googleBackup.copy(settings = app.googleBackup.preferences.load(), remoteBackups = emptyList()))
    }
    mutableEffects.tryEmit(UiEffect.UnlinkGoogleBackup)
}

internal fun MainViewModel.requestGoogleAuthorization(action: String) {
    if (!state.value.can(StaffPermission.MANAGE_SETTINGS)) return
    val settings = getApplication<DaftariApp>().googleBackup.preferences.load()
    if (!settings.linked) {
        mutableEffects.tryEmit(UiEffect.LinkGoogleBackup(action))
        return
    }
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(loading = true, action = action)) }
    mutableEffects.tryEmit(UiEffect.AuthorizeGoogleBackup(action))
}

internal fun MainViewModel.handleGoogleBackupAuthorized(event: UiEvent.GoogleBackupAuthorized) = viewModelScope.launch {
    val app = getApplication<DaftariApp>()
    val manager = app.googleBackup
    if (event.accountEmail.isNotBlank() && event.accountSubject.isNotBlank()) {
        val current = manager.preferences.load()
        if (current.linked && current.accountSubject != event.accountSubject) {
            mutableState.update { it.copy(googleBackup = it.googleBackup.copy(loading = false)) }
            message(R.string.msg_google_account_mismatch)
            return@launch
        }
        manager.preferences.link(event.accountEmail, event.accountSubject)
    }
    try {
        when {
            event.action == "LINK" || event.action == "LIST" -> {
                val remote = manager.list(event.accessToken)
                mutableState.update {
                    it.copy(googleBackup = it.googleBackup.copy(
                        settings = manager.preferences.load(), remoteBackups = remote,
                        loading = false, action = "", hasLocalData = manager.hasLocalData()
                    ))
                }
                if (event.action == "LINK") message(R.string.msg_google_backup_linked)
            }
            event.action == "BACKUP" -> {
                val result = manager.backupNow(event.accessToken)
                val remote = manager.list(event.accessToken)
                mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = manager.preferences.load(), remoteBackups = remote, loading = false, action = "")) }
                message(when {
                    result.skippedUnchanged -> R.string.msg_google_backup_unchanged
                    result.conflict -> R.string.msg_google_backup_conflict
                    else -> R.string.msg_google_backup_success
                })
            }
            event.action.startsWith("RESTORE:") -> {
                val id = event.action.substringAfter(':')
                val remote = state.value.googleBackup.remoteBackups.firstOrNull { it.id == id }
                    ?: error("Backup is no longer available")
                manager.restore(event.accessToken, remote)
                mutableState.update { it.copy(restartRequested = true, googleBackup = it.googleBackup.copy(loading = false, pendingRestore = null)) }
            }
            event.action.startsWith("DELETE:") -> {
                val id = event.action.substringAfter(':')
                val remote = state.value.googleBackup.remoteBackups.firstOrNull { it.id == id }
                    ?: error("Backup is no longer available")
                manager.delete(event.accessToken, remote)
                val refreshed = manager.list(event.accessToken)
                mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = manager.preferences.load(), remoteBackups = refreshed, loading = false, pendingDelete = null)) }
                message(R.string.msg_google_backup_deleted)
            }
        }
    } catch (error: Exception) {
        if (error is IOException || (error is DriveBackupException && error.retryable)) {
            AutoBackupWorker.enqueueNow(app, manager.preferences.load().wifiOnly)
            manager.preferences.setStatus(BackupRunStatus.WAITING, error.message.orEmpty())
            message(R.string.msg_google_backup_waiting)
        } else {
            message(R.string.msg_google_backup_failed, friendlyBackupError(error))
        }
        mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = manager.preferences.load(), loading = false, action = "")) }
    }
}

internal fun MainViewModel.googleBackupAuthorizationFailed(reason: String) {
    val manager = getApplication<DaftariApp>().googleBackup
    manager.preferences.recordFailure(BackupRunStatus.AUTH_REQUIRED, reason)
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = manager.preferences.load(), loading = false, action = "")) }
    message(R.string.msg_google_backup_auth_required)
}

internal fun MainViewModel.setGoogleBackupAutomatic(enabled: Boolean) {
    val app = getApplication<DaftariApp>()
    val preferences = app.googleBackup.preferences
    if (enabled && !preferences.load().linked) return linkGoogleBackup()
    preferences.setAutomatic(enabled)
    viewModelScope.launch {
        val legacy = runCatching { repo.settings.get()?.autoBackupEnabled == true }.getOrDefault(false)
        AutoBackupWorker.schedule(app, enabled || legacy, preferences.load().wifiOnly)
    }
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = preferences.load())) }
}

internal fun MainViewModel.setGoogleBackupWifiOnly(enabled: Boolean) {
    val app = getApplication<DaftariApp>()
    app.googleBackup.preferences.setWifiOnly(enabled)
    val settings = app.googleBackup.preferences.load()
    if (settings.automaticEnabled) AutoBackupWorker.schedule(app, true, enabled)
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(settings = settings)) }
}

internal fun MainViewModel.prepareGoogleRestore(backup: com.daftari.ledger.backup.RemoteBackup?) {
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(pendingRestore = backup)) }
}

internal fun MainViewModel.confirmGoogleRestore() {
    val remote = state.value.googleBackup.pendingRestore ?: return
    requestGoogleAuthorization("RESTORE:${remote.id}")
}

internal fun MainViewModel.prepareGoogleBackupDelete(backup: com.daftari.ledger.backup.RemoteBackup?) {
    mutableState.update { it.copy(googleBackup = it.googleBackup.copy(pendingDelete = backup)) }
}

internal fun MainViewModel.confirmGoogleBackupDelete() {
    val remote = state.value.googleBackup.pendingDelete ?: return
    requestGoogleAuthorization("DELETE:${remote.id}")
}

private fun friendlyBackupError(error: Exception): String = when {
    error.message.isNullOrBlank() -> "تعذر إكمال العملية. حاول مرة أخرى."
    error is DriveBackupException && error.statusCode == 403 -> "مساحة Google Drive ممتلئة أو تم رفض الوصول."
    error is DriveBackupException && error.authorizationRequired -> "انتهى تفويض Google. أعد ربط الحساب."
    else -> error.message.orEmpty().take(180)
}
