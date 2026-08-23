package com.daftari.ledger.backup

import android.content.Context
import com.daftari.ledger.data.AppDb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleBackupManager(
    private val context: Context,
    private val backupManager: BackupManager,
    private val drive: GoogleDriveBackupClient = GoogleDriveBackupClient()
) {
    data class BackupResult(val remote: RemoteBackup?, val skippedUnchanged: Boolean, val conflict: Boolean)

    val preferences = GoogleBackupPreferences(context)

    suspend fun list(accessToken: String): List<RemoteBackup> = withContext(Dispatchers.IO) {
        verifyAccount(accessToken)
        preferences.setStatus(BackupRunStatus.RUNNING)
        try {
            drive.list(accessToken).sortedByDescending { it.createdAt }.also {
                preferences.setStatus(BackupRunStatus.IDLE)
            }
        } catch (error: Exception) {
            recordError(error)
            throw error
        }
    }

    suspend fun backupNow(accessToken: String): BackupResult = withContext(Dispatchers.IO) {
        verifyAccount(accessToken)
        val settings = preferences.load()
        check(settings.linked) { "Link a Google Account first" }
        preferences.setStatus(BackupRunStatus.RUNNING)
        val snapshot = backupManager.createValidatedSnapshot("google-")
        val inspection = backupManager.inspectDatabase(snapshot)
        val databaseHash = BackupArchive.sha256(snapshot)
        try {
            val existing = drive.list(accessToken).sortedByDescending { it.createdAt }
            if (settings.lastContentHash.equals(databaseHash, true) && settings.lastRemoteId.isNotBlank()) {
                preferences.setStatus(BackupRunStatus.SUCCESS)
                return@withContext BackupResult(existing.firstOrNull { it.id == settings.lastRemoteId }, true, false)
            }
            val conflict = BackupPolicy.hasDiverged(settings, existing)
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }.getOrDefault("")
            val archiveFile = File(snapshot.parentFile, "$CLOUD_BACKUP_PREFIX${System.currentTimeMillis()}.dfb")
            val archive = BackupArchive.create(snapshot, archiveFile) { hash, bytes ->
                BackupManifest(
                    appVersion = appVersion,
                    databaseVersion = inspection.version,
                    createdAt = System.currentTimeMillis(),
                    dataModifiedAt = inspection.latestDataChangeAt,
                    deviceId = settings.deviceId,
                    deviceName = preferences.deviceName(),
                    datasetId = settings.datasetId,
                    parentBackupId = settings.lastRemoteId.ifBlank { null },
                    databaseSha256 = hash,
                    databaseBytes = bytes,
                    rowCounts = inspection.rowCounts,
                    conflict = conflict
                )
            }
            val remote = drive.upload(accessToken, archive)
            preferences.recordSuccess(remote.id, remote.size, databaseHash)
            if (conflict) preferences.setStatus(BackupRunStatus.CONFLICT)
            rotate(accessToken, existing + remote, settings)
            BackupResult(remote, false, conflict)
        } catch (error: Exception) {
            recordError(error)
            throw error
        } finally {
            snapshot.parentFile?.deleteRecursively()
        }
    }

    suspend fun restore(accessToken: String, remote: RemoteBackup) = withContext(Dispatchers.IO) {
        verifyAccount(accessToken)
        preferences.setStatus(BackupRunStatus.RUNNING)
        val directory = File(context.noBackupFilesDir, "cloud-restore-${System.currentTimeMillis()}").apply { mkdirs() }
        val archive = File(directory, remote.name.ifBlank { "backup.dfb" })
        try {
            drive.download(accessToken, remote, archive)
            val extracted = BackupArchive.extractAndVerify(archive, File(directory, "extracted"))
            if (extracted.manifest.databaseVersion > AppDb.VERSION) error("Update Daftari before restoring this backup")
            backupManager.inspectDatabase(extracted.database)
            backupManager.restoreFrom(extracted.database)
            val restoredDb = AppDb.get(context)
            restoredDb.settings().get()?.let { current ->
                restoredDb.settings().update(
                    current.copy(
                        currentEmployeeId = null,
                        failedPinAttempts = 0,
                        pinLockedUntil = 0,
                        biometricUnlock = false,
                        autoBackupEnabled = false
                    )
                )
            }
            preferences.adoptDataset(extracted.manifest.datasetId)
            preferences.recordSuccess(remote.id, remote.size, extracted.manifest.databaseSha256)
        } catch (error: Exception) {
            recordError(error)
            throw error
        } finally {
            directory.deleteRecursively()
        }
    }

    suspend fun delete(accessToken: String, remote: RemoteBackup) = withContext(Dispatchers.IO) {
        verifyAccount(accessToken)
        drive.delete(accessToken, remote.id)
    }

    fun hasLocalData(): Boolean = runCatching {
        val db = AppDb.get(context).openHelper.readableDatabase
        listOf("documents", "parties", "employees").sumOf { table ->
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }
        } > 0
    }.getOrDefault(false)

    private fun rotate(token: String, all: List<RemoteBackup>, settings: GoogleBackupSettings) {
        BackupPolicy.deletionCandidates(all, settings).forEach { runCatching { drive.delete(token, it.id) } }
    }

    private fun verifyAccount(token: String) {
        val user = drive.userInfo(token)
        check(user.subject.isNotBlank() && user.email.isNotBlank()) { "Google Account identity could not be verified" }
        val settings = preferences.load()
        if (settings.linked && settings.accountSubject != user.subject) {
            throw DriveBackupException("The authorized Google Account differs from the linked backup account")
        }
        if (!settings.linked) preferences.link(user.email, user.subject)
    }

    private fun recordError(error: Exception) {
        val driveError = error as? DriveBackupException
        val status = when {
            driveError?.authorizationRequired == true -> BackupRunStatus.AUTH_REQUIRED
            else -> BackupRunStatus.FAILED
        }
        preferences.recordFailure(status, error.message.orEmpty())
    }
}
