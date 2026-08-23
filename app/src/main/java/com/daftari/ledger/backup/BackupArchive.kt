package com.daftari.ledger.backup

import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupArchive {
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATABASE_ENTRY = "database/daftari.db"
    private const val MAX_DATABASE_BYTES = 2L * 1024 * 1024 * 1024
    private const val BUFFER = 128 * 1024
    private val gson = Gson()

    data class Created(val file: File, val manifest: BackupManifest, val contentSha256: String)
    data class Extracted(val directory: File, val database: File, val manifest: BackupManifest)

    fun create(database: File, destination: File, manifestFactory: (databaseHash: String, bytes: Long) -> BackupManifest): Created {
        require(database.isFile && database.length() in 1..MAX_DATABASE_BYTES) { "Invalid database snapshot" }
        val databaseHash = sha256(database)
        val manifest = manifestFactory(databaseHash, database.length())
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".partial")
        try {
            ZipOutputStream(FileOutputStream(temporary).buffered(BUFFER)).use { zip ->
                zip.setLevel(6)
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY).apply { time = manifest.createdAt })
                zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY).apply { time = manifest.createdAt })
                FileInputStream(database).buffered(BUFFER).use { it.copyTo(zip, BUFFER) }
                zip.closeEntry()
            }
            check(temporary.renameTo(destination) || run { temporary.copyTo(destination, true); temporary.delete(); true })
            return Created(destination, manifest, sha256(destination))
        } finally {
            temporary.delete()
        }
    }

    fun readManifest(archive: File): BackupManifest = ZipFile(archive).use { zip ->
        val entry = zip.getEntry(MANIFEST_ENTRY) ?: error("Backup manifest is missing")
        require(!entry.isDirectory && entry.size in 1..1_000_000) { "Invalid backup manifest" }
        zip.getInputStream(entry).bufferedReader().use { gson.fromJson(it, BackupManifest::class.java) }
            ?: error("Invalid backup manifest")
    }

    fun extractAndVerify(archive: File, destination: File): Extracted {
        require(archive.isFile && archive.length() > 0) { "Backup file is empty" }
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        val manifest = readManifest(archive)
        require(manifest.formatVersion == 1) { "Unsupported backup format" }
        val database = File(destination, DATABASE_ENTRY)
        ZipFile(archive).use { zip ->
            val databaseEntry = zip.getEntry(DATABASE_ENTRY) ?: error("Backup database is missing")
            require(!databaseEntry.isDirectory && databaseEntry.size in 1..MAX_DATABASE_BYTES) { "Invalid database size" }
            val output = database.canonicalFile
            require(output.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe backup path" }
            output.parentFile?.mkdirs()
            zip.getInputStream(databaseEntry).use { input ->
                FileOutputStream(output).buffered(BUFFER).use { out -> input.copyTo(out, BUFFER) }
            }
        }
        require(database.length() == manifest.databaseBytes) { "Backup database size does not match" }
        require(sha256(database).equals(manifest.databaseSha256, ignoreCase = true)) { "Backup database checksum does not match" }
        return Extracted(destination, database, manifest)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(BUFFER).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
