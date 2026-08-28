package com.daftari.ledger.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.DatabaseHealthCheck
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(private val ctx: Context, private val db: AppDb) {
    data class DatabaseInspection(
        val version: Int,
        val integrityOk: Boolean,
        val foreignKeysOk: Boolean,
        val rowCounts: Map<String, Long>,
        val latestDataChangeAt: Long
    )

    private fun dbFile(): File = ctx.getDatabasePath(DATABASE_NAME)
    private fun backupsDir(): File = File(ctx.filesDir, "backups").apply { mkdirs() }
    private fun stagingDir(): File = File(ctx.noBackupFilesDir, "backup-staging").apply { mkdirs() }

    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { if (it.moveToFirst()) it.getInt(0) }
    }

    /** Creates a standalone, consolidated SQLite snapshot while Room writes are briefly serialized. */
    suspend fun createValidatedSnapshot(prefix: String = "snapshot-"): File = withContext(Dispatchers.IO) {
        val directory = File(stagingDir(), "$prefix${System.currentTimeMillis()}").apply { mkdirs() }
        val snapshot = File(directory, DATABASE_NAME)
        synchronized(SNAPSHOT_LOCK) {
            checkpoint()
            db.runInTransaction {
                dbFile().copyTo(snapshot, overwrite = true)
                File(dbFile().path + "-wal").takeIf { it.isFile && it.length() > 0 }?.copyTo(File(snapshot.path + "-wal"), true)
                File(dbFile().path + "-shm").takeIf { it.isFile && it.length() > 0 }?.copyTo(File(snapshot.path + "-shm"), true)
            }
        }
        // Opening the copied DB replays its WAL. A checkpoint then makes the snapshot a single portable file.
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", true)) { "Database snapshot failed integrity check" }
            }
            sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        File(snapshot.path + "-wal").delete()
        File(snapshot.path + "-shm").delete()
        inspectDatabase(snapshot)
        snapshot
    }

    suspend fun exportDatabase(): File = withContext(Dispatchers.IO) {
        val snapshot = createValidatedSnapshot("export-")
        val destination = File(backupsDir(), "daftari-backup-${System.currentTimeMillis()}.db")
        try { snapshot.copyTo(destination, true) } finally { snapshot.parentFile?.deleteRecursively() }
        destination
    }

    suspend fun exportEncrypted(password: String): File = withContext(Dispatchers.IO) {
        val snapshot = createValidatedSnapshot("encrypted-")
        val destination = File(backupsDir(), "daftari-backup-${System.currentTimeMillis()}.enc")
        try { EncryptedBackup.encrypt(snapshot, destination, password) } finally { snapshot.parentFile?.deleteRecursively() }
        destination
    }

    fun inspectDatabase(file: File): DatabaseInspection {
        requireSqliteDatabase(file)
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            val version = sqlite.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) }
            val integrity = sqlite.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0).equals("ok", true) }
            val foreignKeys = sqlite.rawQuery("PRAGMA foreign_key_check", null).use { !it.moveToFirst() }
            val tables = sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            val expectedTables = REQUIRED_TABLES.filter { table ->
                when (table) {
                    "categories" -> version >= 5
                    "daily_books" -> version >= 6
                    "employees", "employee_shops", "employee_shifts" -> version >= 7
                    else -> true
                }
            }
            check(expectedTables.all(tables::contains)) { "Backup is missing required data tables" }
            val counts = expectedTables.associateWith { table ->
                sqlite.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getLong(0) }
            }
            DatabaseHealthCheck.checks(version).forEach { check ->
                val broken = sqlite.rawQuery(check.sql, null).use { it.moveToFirst(); it.getLong(0) }
                kotlin.check(broken == 0L) { "Backup failed health check '${check.code}': ${check.message}" }
            }
            val latestQueries = buildList {
                add("SELECT COALESCE(MAX(updatedAt),0) FROM documents")
                add("SELECT COALESCE(MAX(at),0) FROM audit_logs")
                add("SELECT COALESCE(MAX(createdAt),0) FROM shops")
                if ("daily_books" in tables) add("SELECT COALESCE(MAX(updatedAt),0) FROM daily_books")
                if ("employees" in tables) add("SELECT COALESCE(MAX(updatedAt),0) FROM employees")
            }
            val latest = latestQueries.maxOf { sql -> sqlite.rawQuery(sql, null).use { it.moveToFirst(); it.getLong(0) } }
            check(version in 1..AppDb.VERSION) { if (version > AppDb.VERSION) "Backup requires a newer app version" else "Unsupported database version" }
            check(integrity) { "Backup database is damaged" }
            check(foreignKeys) { "Backup contains broken data relationships" }
            DatabaseInspection(version, integrity, foreignKeys, counts, latest)
        }
    }

    private fun requireSqliteDatabase(file: File) {
        if (!file.isFile || file.length() < 100) error("Invalid backup file")
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input ->
            if (input.read(header) != header.size || !header.contentEquals(SQLITE_HEADER)) error("File is not a Daftari SQLite backup")
        }
    }

    /** Validates first, creates a rollback snapshot, then atomically swaps databases on the same filesystem. */
    private suspend fun replaceDb(file: File) {
        withContext(Dispatchers.IO) { inspectDatabase(file) }
        val safetySnapshot = createValidatedSnapshot("pre-restore-")
        val safety = File(backupsDir(), "pre-restore-${System.currentTimeMillis()}.db")
        withContext(Dispatchers.IO) { safetySnapshot.copyTo(safety, true); safetySnapshot.parentFile?.deleteRecursively() }

        val current = dbFile()
        val incoming = File(current.parentFile, "$DATABASE_NAME.restore")
        val rollback = File(current.parentFile, "$DATABASE_NAME.rollback")
        withContext(Dispatchers.IO) {
            file.copyTo(incoming, true)
            FileOutputStream(incoming, true).fd.sync()
        }
        db.close()
        try {
            withContext(Dispatchers.IO) {
                File(current.path + "-wal").delete(); File(current.path + "-shm").delete()
                rollback.delete()
                if (current.exists()) moveReplace(current, rollback)
                moveReplace(incoming, current)
            }
            AppDb.invalidate(ctx)
            // Force Room to open and run every required migration before discarding rollback.
            AppDb.get(ctx).openHelper.writableDatabase.query("PRAGMA quick_check").use {
                check(it.moveToFirst() && it.getString(0).equals("ok", true))
            }
            withContext(Dispatchers.IO) { rollback.delete() }
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) {
                incoming.delete()
                if (rollback.exists()) moveReplace(rollback, current)
            }
            AppDb.invalidate(ctx)
            throw error
        }
    }

    private fun moveReplace(from: File, to: File) {
        runCatching { Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            .getOrElse { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    suspend fun restoreFrom(file: File) = replaceDb(file)

    suspend fun restoreFromUri(uri: Uri) {
        val temporary = withContext(Dispatchers.IO) {
            File.createTempFile("document-restore-", ".db", stagingDir()).also { file ->
                ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: error("Unable to open backup file")
            }
        }
        try { replaceDb(temporary) } finally { withContext(Dispatchers.IO) { temporary.delete() } }
    }

    suspend fun restoreEncrypted(file: File, password: String) {
        if (!file.isFile || file.length() < 100) error("Invalid encrypted backup")
        val temporary = withContext(Dispatchers.IO) { File.createTempFile("restore-", ".db", stagingDir()) }
        try {
            withContext(Dispatchers.IO) { EncryptedBackup.decrypt(file, temporary, password) }
            replaceDb(temporary)
        } finally { withContext(Dispatchers.IO) { temporary.delete() } }
    }

    fun listBackups(): List<File> = backupsDir().listFiles { file ->
        file.isFile && (file.name.endsWith(".db") || file.name.endsWith(".enc")) && file.name.startsWith("daftari-backup-")
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun listSafetyCopies(): List<File> = backupsDir().listFiles { file ->
        file.isFile && file.name.startsWith("pre-restore-")
    }?.sortedByDescending { file -> file.lastModified() } ?: emptyList()

    companion object {
        private const val DATABASE_NAME = "daftari.db"
        private val SNAPSHOT_LOCK = Any()
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val REQUIRED_TABLES = listOf(
            "shops", "parties", "accounts", "documents", "journal_lines", "categories", "audit_logs",
            "app_settings", "daily_closings", "daily_books", "employees", "employee_shops", "employee_shifts"
        )
    }
}
