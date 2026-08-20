package com.daftari.ledger.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daftari.ledger.data.AppDb
import java.util.concurrent.TimeUnit

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDb.get(applicationContext)
            val keep = db.settings().get()?.autoBackupKeep ?: 7
            val file = BackupManager(applicationContext, db).exportJson()
            val dir = file.parentFile ?: return Result.success()
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork("daftari-auto-backup")
                return
            }
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork("daftari-auto-backup", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
