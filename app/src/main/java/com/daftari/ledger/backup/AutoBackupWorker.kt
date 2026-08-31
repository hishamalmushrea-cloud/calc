package com.daftari.ledger.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.daftari.ledger.R
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daftari.ledger.data.AppDb
import java.io.File
import java.util.concurrent.TimeUnit

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = AppDb.get(applicationContext)
        val local = BackupManager(applicationContext, db)
        val google = GoogleBackupManager(applicationContext, local)
        val googleSettings = google.preferences.load()
        if (googleSettings.linked && (googleSettings.automaticEnabled || inputData.getBoolean(KEY_GOOGLE_MANUAL, false))) {
            google.preferences.setStatus(BackupRunStatus.RUNNING)
            return try {
                when (val token = GoogleDriveAuthorization.token(applicationContext)) {
                    is DriveTokenResult.Granted -> {
                        google.backupNow(token.accessToken)
                        Result.success()
                    }
                    DriveTokenResult.UserInteractionRequired -> {
                        google.preferences.recordFailure(BackupRunStatus.AUTH_REQUIRED, "Google Account needs to be reconnected")
                        notifyRepeatedFailure(google.preferences.load())
                        Result.failure()
                    }
                }
            } catch (error: DriveBackupException) {
                notifyRepeatedFailure(google.preferences.load())
                if (error.retryable) Result.retry() else Result.failure()
            } catch (_: Exception) {
                google.preferences.recordFailure(BackupRunStatus.FAILED, "Automatic backup could not be completed")
                notifyRepeatedFailure(google.preferences.load())
                Result.retry()
            }
        }

        // Preserve the existing local/SAF/WebDAV automatic backup mode.
        return try {
            val settings = runCatching { db.settings().get() }.getOrNull()
            if (settings?.autoBackupEnabled != true) return Result.success()
            val keep = settings.autoBackupKeep
            val cloud = CloudBackupManager(applicationContext, local)
            // نفاد المساحة: لا تبدأ النسخ إذا لم تتسع، ونبّه المستخدم ثم أعد المحاولة لاحقًا.
            val dbBytes = runCatching { applicationContext.getDatabasePath("daftari.db").length() }.getOrDefault(0L)
            val availableBytes = runCatching { StatFs(applicationContext.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
            if (!BackupSpacePolicy.hasRoom(availableBytes, dbBytes)) {
                notifyIssue(NOTIFY_SPACE, R.string.backup_space_low_title, R.string.backup_space_low_text)
                return Result.retry()
            }
            val file = local.exportDatabase()
            cloud.upload(file)
            // فقدان مجلد النسخ الاختياري: النسخة المحلية تمت لكن الوجهة المختارة لم تعد متاحة.
            if (!cloud.isTreeAccessible()) {
                notifyIssue(NOTIFY_FOLDER, R.string.backup_folder_lost_title, R.string.backup_folder_lost_text)
            }
            rotate(local, keep)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun rotate(manager: BackupManager, keep: Int) {
        val backups = manager.listBackups()
        val infos = backups.map { BackupFile(it.name, it.lastModified(), it.length()) }
        val doomed = BackupRetention.selectDeletions(infos, keep).mapTo(mutableSetOf()) { it.name }
        if (doomed.isEmpty()) return
        backups.filter { it.name in doomed }.forEach { runCatching { it.delete() } }
    }

    private fun notifyRepeatedFailure(settings: GoogleBackupSettings) {
        if (settings.failureCount < 3) return
        notifyIssue(NOTIFY_FAILURE, R.string.backup_status_failed, R.string.backup_failure_notification)
    }

    /** إشعار موحّد لمشاكل النسخ (فشل متكرر، نفاد مساحة، فقدان مجلد) على نفس القناة. */
    private fun notifyIssue(notificationId: Int, titleRes: Int, textRes: Int) {
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.backup_failure_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(com.daftari.ledger.R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(titleRes))
            .setContentText(applicationContext.getString(textRes))
            .setAutoCancel(true)
            .build()
        notifications.notify(notificationId, notification)
    }

    companion object {
        private const val UNIQUE = "daftari-auto-backup"
        private const val CHANNEL = "backup_failures"
        private const val KEY_GOOGLE_MANUAL = "google_manual"
        private const val NOTIFY_FAILURE = 3902
        private const val NOTIFY_SPACE = 3903
        private const val NOTIFY_FOLDER = 3904

        fun schedule(context: Context, enabled: Boolean, wifiOnly: Boolean = GoogleBackupPreferences(context).load().wifiOnly) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(UNIQUE)
                return
            }
            val googleActive = GoogleBackupPreferences(context).load().let { it.linked && it.automaticEnabled }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (!googleActive) NetworkType.NOT_REQUIRED else if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueueNow(context: Context, wifiOnly: Boolean) {
            val request = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(androidx.work.workDataOf(KEY_GOOGLE_MANUAL to true))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
                ).build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun backupsDir(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }
    }
}
