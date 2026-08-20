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

    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("appVersion", "1.0.0")
            addProperty("exportedAt", System.currentTimeMillis())
        }
        // Room export via attaching raw copy is safer for restore integrity
        val dbFile = ctx.getDatabasePath("daftari.db")
        val out = File(ctx.filesDir, "backups").apply { mkdirs() }
        val dest = File(out, "daftari-backup-${System.currentTimeMillis()}.db")
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
        dbFile.copyTo(dest, overwrite = true)
        dest
    }

    suspend fun restoreFrom(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 100) error("نسخة احتياطية غير صالحة")
        val current = ctx.getDatabasePath("daftari.db")
        val safety = File(ctx.filesDir, "backups/pre-restore-${System.currentTimeMillis()}.db")
        safety.parentFile?.mkdirs()
        if (current.exists()) current.copyTo(safety, overwrite = true)
        AppDb.get(ctx).close()
        file.copyTo(current, overwrite = true)
    }

    /** قائمة النسخ المتاحة للاستعادة، الأحدث أولًا، مع استبعاد نسخ الحماية قبل الاستعادة. */
    fun listBackups(): List<File> =
        File(ctx.filesDir, "backups").listFiles { f ->
            f.name.endsWith(".db") && !f.name.startsWith("pre-restore-")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
}
