package com.daftari.ledger.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daftari.ledger.data.AppDb
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * عامل النسخ الاحتياطي اليومي.
 *
 * يصدر نسخة من قاعدة البيانات كل 24 ساعة، ويدير الاحتفاظ بـ `autoBackupKeep`
 * نسخ آلية. عند الدوران لا يُحذف سوى النسخ الآلية (`.db` ببادئة
 * `daftari-backup-`)؛ تُترك نسخ الحماية (`pre-restore-*`) والنسخ اليدوية
 * المشفّرة كما هي.
 */
class AutoBackupWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDb.get(applicationContext)
            val settings = runCatching { db.settings().get() }.getOrNull()
            val keep = (settings?.autoBackupKeep ?: 7).coerceAtLeast(1)
            val mgr = BackupManager(applicationContext, db)
            mgr.exportJson()
            rotate(mgr, keep)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun rotate(mgr: BackupManager, keep: Int) {
        // النسخ الآلية هي ملفات .db ذات البادئة daftari-backup- فقط.
        val auto = mgr.listBackups().filter { it.name.endsWith(".db") }
        auto.drop(keep).forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val UNIQUE = "daftari-auto-backup"

        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE)
                return
            }
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** مجلد النسخ الاحتياطية (للاستخدام الخارجي/الاختبارات). */
        fun backupsDir(ctx: Context): File =
            File(ctx.filesDir, "backups").apply { mkdirs() }
    }
}
