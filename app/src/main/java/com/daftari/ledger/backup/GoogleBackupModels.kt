package com.daftari.ledger.backup

import android.content.Context
import android.os.Build
import java.util.UUID

const val GOOGLE_DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
const val GOOGLE_OPENID_SCOPE = "openid"
const val GOOGLE_EMAIL_SCOPE = "email"
const val CLOUD_BACKUP_MIME = "application/vnd.daftari.backup"
const val CLOUD_BACKUP_PREFIX = "daftari-cloud-"

data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAt: Long,
    val dataModifiedAt: Long,
    val deviceId: String,
    val deviceName: String,
    val datasetId: String,
    val parentBackupId: String? = null,
    val databaseSha256: String,
    val databaseBytes: Long,
    val rowCounts: Map<String, Long>,
    val attachments: List<BackupAttachment> = emptyList(),
    val conflict: Boolean = false
)

data class BackupAttachment(
    val relativePath: String,
    val sha256: String,
    val bytes: Long
)

data class RemoteBackup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: String,
    val size: Long,
    val deviceId: String,
    val deviceName: String,
    val datasetId: String,
    val parentBackupId: String?,
    val databaseVersion: Int,
    val appVersion: String,
    val contentSha256: String,
    val databaseSha256: String,
    val conflict: Boolean,
    val valid: Boolean = true,
    val validationMessage: String = ""
)

enum class BackupRunStatus { DISCONNECTED, IDLE, WAITING, RUNNING, SUCCESS, FAILED, AUTH_REQUIRED, CONFLICT }

data class GoogleBackupSettings(
    val accountEmail: String = "",
    val accountSubject: String = "",
    val automaticEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val status: BackupRunStatus = BackupRunStatus.DISCONNECTED,
    val lastSuccessAt: Long = 0,
    val lastSize: Long = 0,
    val lastRemoteId: String = "",
    val lastContentHash: String = "",
    val lastError: String = "",
    val failureCount: Int = 0,
    val deviceId: String = "",
    val datasetId: String = ""
) {
    val linked: Boolean get() = accountEmail.isNotBlank() && accountSubject.isNotBlank()
}

/** Device-local metadata only. OAuth tokens and Google passwords are never stored here. */
class GoogleBackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("google_backup_state", Context.MODE_PRIVATE)

    fun load(): GoogleBackupSettings = GoogleBackupSettings(
        accountEmail = prefs.getString(KEY_EMAIL, "").orEmpty(),
        accountSubject = prefs.getString(KEY_SUBJECT, "").orEmpty(),
        automaticEnabled = prefs.getBoolean(KEY_AUTOMATIC, false),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        keepDaily = prefs.getInt(KEY_KEEP_DAILY, 7).coerceIn(1, 30),
        keepWeekly = prefs.getInt(KEY_KEEP_WEEKLY, 4).coerceIn(0, 12),
        status = runCatching { BackupRunStatus.valueOf(prefs.getString(KEY_STATUS, BackupRunStatus.DISCONNECTED.name).orEmpty()) }
            .getOrDefault(BackupRunStatus.DISCONNECTED),
        lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0),
        lastSize = prefs.getLong(KEY_LAST_SIZE, 0),
        lastRemoteId = prefs.getString(KEY_LAST_REMOTE_ID, "").orEmpty(),
        lastContentHash = prefs.getString(KEY_LAST_HASH, "").orEmpty(),
        lastError = prefs.getString(KEY_LAST_ERROR, "").orEmpty(),
        failureCount = prefs.getInt(KEY_FAILURE_COUNT, 0),
        deviceId = deviceId(),
        datasetId = datasetId()
    )

    fun link(email: String, subject: String) {
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_SUBJECT, subject)
            .putString(KEY_STATUS, BackupRunStatus.IDLE.name).putString(KEY_LAST_ERROR, "").apply()
    }

    fun unlink() {
        prefs.edit().remove(KEY_EMAIL).remove(KEY_SUBJECT).putBoolean(KEY_AUTOMATIC, false)
            .putString(KEY_STATUS, BackupRunStatus.DISCONNECTED.name).apply()
    }

    fun setAutomatic(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTOMATIC, enabled).apply()
    fun setWifiOnly(enabled: Boolean) = prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    fun setStatus(status: BackupRunStatus, error: String = "") = prefs.edit()
        .putString(KEY_STATUS, status.name).putString(KEY_LAST_ERROR, error).apply()

    fun recordSuccess(remoteId: String, bytes: Long, contentHash: String) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).putLong(KEY_LAST_SIZE, bytes)
            .putString(KEY_LAST_REMOTE_ID, remoteId).putString(KEY_LAST_HASH, contentHash)
            .putString(KEY_STATUS, BackupRunStatus.SUCCESS.name).putString(KEY_LAST_ERROR, "")
            .putInt(KEY_FAILURE_COUNT, 0).apply()
    }

    fun recordFailure(status: BackupRunStatus, message: String) {
        prefs.edit().putString(KEY_STATUS, status.name).putString(KEY_LAST_ERROR, message)
            .putInt(KEY_FAILURE_COUNT, prefs.getInt(KEY_FAILURE_COUNT, 0) + 1).apply()
    }

    fun adoptDataset(id: String) {
        if (id.isNotBlank()) prefs.edit().putString(KEY_DATASET_ID, id).apply()
    }

    fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }.joinToString(" ").trim().take(80)

    private fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    private fun datasetId(): String = prefs.getString(KEY_DATASET_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DATASET_ID, it).apply()
    }

    private companion object {
        const val KEY_EMAIL = "account_email"
        const val KEY_SUBJECT = "account_subject"
        const val KEY_AUTOMATIC = "automatic"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_KEEP_DAILY = "keep_daily"
        const val KEY_KEEP_WEEKLY = "keep_weekly"
        const val KEY_STATUS = "status"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_LAST_SIZE = "last_size"
        const val KEY_LAST_REMOTE_ID = "last_remote_id"
        const val KEY_LAST_HASH = "last_hash"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DATASET_ID = "dataset_id"
    }
}
