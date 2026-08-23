package com.daftari.ledger.backup

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    @Test
    fun roundTripPreservesDatabaseAndManifest() {
        val root = Files.createTempDirectory("backup-archive").toFile()
        try {
            val database = root.resolve("source.db").apply { writeBytes(ByteArray(32_000) { (it % 251).toByte() }) }
            val archive = root.resolve("backup.dfb")
            val created = BackupArchive.create(database, archive) { hash, bytes -> manifest(hash, bytes) }
            val extracted = BackupArchive.extractAndVerify(created.file, root.resolve("extract"))
            assertArrayEquals(database.readBytes(), extracted.database.readBytes())
            assertEquals(created.manifest.databaseSha256, extracted.manifest.databaseSha256)
            assertEquals("dataset", extracted.manifest.datasetId)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun modifiedArchiveIsRejected() {
        val root = Files.createTempDirectory("backup-archive-corrupt").toFile()
        try {
            val database = root.resolve("source.db").apply { writeBytes(ByteArray(8_000) { it.toByte() }) }
            val archive = root.resolve("backup.dfb")
            BackupArchive.create(database, archive) { hash, bytes -> manifest(hash, bytes) }
            val bytes = archive.readBytes()
            bytes[bytes.lastIndex - 20] = (bytes[bytes.lastIndex - 20].toInt() xor 0x55).toByte()
            archive.writeBytes(bytes)
            val failure = runCatching { BackupArchive.extractAndVerify(archive, root.resolve("extract")) }
            assertTrue(failure.isFailure)
        } finally { root.deleteRecursively() }
    }

    private fun manifest(hash: String, bytes: Long) = BackupManifest(
        appVersion = "1.3.0", databaseVersion = 7, createdAt = 1, dataModifiedAt = 1,
        deviceId = "device", deviceName = "Phone", datasetId = "dataset",
        databaseSha256 = hash, databaseBytes = bytes, rowCounts = mapOf("documents" to 10)
    )
}
