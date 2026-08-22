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
    /** يتحقق من أن الملف قاعدة SQLite قبل لمس قاعدة المستخدم الحالية. */
    private fun requireSqliteDatabase(file: File) {
        if (!file.isFile || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input ->
            if (input.read(header) != header.size || !header.contentEquals(SQLITE_HEADER)) {
                error("الملف ليس نسخة قاعدة بيانات دفتري صالحة")
            }
        }
    }

    private suspend fun replaceDb(file: File) {
        withContext(Dispatchers.IO) { requireSqliteDatabase(file) }
        val current = dbFile()
        withContext(Dispatchers.IO) {
            // لا نأخذ نسخة الأمان إلا بعد التحقق من النسخة الواردة.
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
        if (!file.isFile || file.length() < 100) error("نسخة احتياطية غير صالحة")
        // فك التشفير في ملف مؤقت أولًا: كلمة مرور خاطئة أو ملف معطوب لا يجوز
        // أن يمس قاعدة البيانات الحالية أو ينشئ نسخة أمان مضللة.
        val temporary = withContext(Dispatchers.IO) {
            File.createTempFile("restore-", ".db", backupsDir())
        }
        try {
            withContext(Dispatchers.IO) { EncryptedBackup.decrypt(file, temporary, password) }
            replaceDb(temporary)
        } finally {
            withContext(Dispatchers.IO) { temporary.delete() }
        }
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

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
