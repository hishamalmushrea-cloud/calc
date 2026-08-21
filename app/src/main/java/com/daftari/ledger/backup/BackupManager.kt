package com.daftari.ledger.backup

import android.content.Context
import com.daftari.ledger.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupManager(private val ctx: Context, private val db: AppDb) {

    private fun dbFile(): File = ctx.getDatabasePath("daftari.db")
    private fun backupsDir(): File = File(ctx.filesDir, "backups").apply { mkdirs() }

    private fun checkpoint() {
        // PRAGMA لا يُنفَّذ فعليًا حتى تُستهلك نتيجته (حرك إلى الصف الأول ثم أغلق).
        val c = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
        c.use { if (it.moveToFirst()) it.getString(0) }
    }

    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        checkpoint()
        val dest = File(backupsDir(), "daftari-backup-${System.currentTimeMillis()}.db")
        dbFile().copyTo(dest, overwrite = true)
        dest
    }

    suspend fun exportEncrypted(password: String): File = withContext(Dispatchers.IO) {
        checkpoint()
        val dest = File(backupsDir(), "daftari-backup-${System.currentTimeMillis()}.enc")
        EncryptedBackup.encrypt(dbFile(), dest, password)
        dest
    }

    /**
     * يستبدل قاعدة البيانات الحالية بالملف المحدد.
     *
     * بعد النسخ تُغلق القاعدة ويُسقط الـ singleton وتُعاد تهيئتها لتفتح على
     * الملف الجديد. يبقى على المستدعي إعادة تشغيل واجهة التطبيق بعد ذلك
     * (انظر [AppDb.invalidate]) لأن الـ Repository الحالي يحمل مرجعًا قديمًا.
     */
    private suspend fun replaceDb(file: File) {
        if (!file.exists() || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val current = dbFile()
        withContext(Dispatchers.IO) {
            // نسخة أمان قبل الاستبدال.
            val safety = File(backupsDir(), "pre-restore-${System.currentTimeMillis()}.db")
            if (current.exists()) current.copyTo(safety, overwrite = true)
        }
        db.close()
        withContext(Dispatchers.IO) {
            // نضمن عدم بقاء ملفات WAL/SHM القديمة التي قد تُلغي محتوى النسخة.
            File(current.path + "-wal").delete()
            File(current.path + "-shm").delete()
            file.copyTo(current, overwrite = true)
        }
        AppDb.invalidate(ctx)
    }

    suspend fun restoreFrom(file: File) {
        replaceDb(file)
    }

    suspend fun restoreEncrypted(file: File, password: String) {
        if (!file.exists() || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val current = dbFile()
        withContext(Dispatchers.IO) {
            val safety = File(backupsDir(), "pre-restore-${System.currentTimeMillis()}.db")
            if (current.exists()) current.copyTo(safety, overwrite = true)
        }
        db.close()
        withContext(Dispatchers.IO) {
            File(current.path + "-wal").delete()
            File(current.path + "-shm").delete()
            EncryptedBackup.decrypt(file, current, password)
        }
        AppDb.invalidate(ctx)
    }

    /**
     * قائمة النسخ المتاحة للاستعادة، الأحدث أولًا.
     * تشمل النسخ اليدوية والآلية فقط، وتستبعد نسخ الحماية قبل الاستعادة.
     */
    fun listBackups(): List<File> =
        backupsDir().listFiles { f ->
            f.isFile &&
                (f.name.endsWith(".db") || f.name.endsWith(".enc")) &&
                f.name.startsWith("daftari-backup-")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** نسخ الحماية (pre-restore) معروضة بشكل منفصل لمنع استعادة عرضية. */
    fun listSafetyCopies(): List<File> =
        backupsDir().listFiles { f -> f.isFile && f.name.startsWith("pre-restore-") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
}
