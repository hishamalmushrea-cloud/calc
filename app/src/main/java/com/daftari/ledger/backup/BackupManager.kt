package com.daftari.ledger.backup

import android.content.Context
import com.daftari.ledger.data.AppDb
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupManager(private val ctx: Context, private val db: AppDb) {
    private val gson = Gson()

    private fun dbFile(): File = ctx.getDatabasePath("daftari.db")
    private fun backupsDir(): File = File(ctx.filesDir, "backups").apply { mkdirs() }

    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
    }

    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("appVersion", "1.0.0")
            addProperty("exportedAt", System.currentTimeMillis())
        }
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

    suspend fun restoreFrom(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val current = dbFile()
        val safety = File(backupsDir(), "pre-restore-${System.currentTimeMillis()}.db")
        if (current.exists()) current.copyTo(safety, overwrite = true)
        AppDb.get(ctx).close()
        file.copyTo(current, overwrite = true)
    }

    suspend fun restoreEncrypted(file: File, password: String) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val current = dbFile()
        val safety = File(backupsDir(), "pre-restore-${System.currentTimeMillis()}.db")
        if (current.exists()) current.copyTo(safety, overwrite = true)
        AppDb.get(ctx).close()
        EncryptedBackup.decrypt(file, current, password)
    }

    /** قائمة النسخ المتاحة للاستعادة، الأحدث أولًا، مع استبعاد نسخ الحماية قبل الاستعادة. */
    fun listBackups(): List<File> =
        backupsDir().listFiles { f ->
            (f.name.endsWith(".db") || f.name.endsWith(".enc")) && !f.name.startsWith("pre-restore-")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
}
