package com.daftari.ledger.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EncryptedBackupTest {

    @Test
    fun encryptThenDecryptRestoresExactBytes() {
        val dir = Files.createTempDirectory("daftari-encrypted-backup-test").toFile()
        try {
            val source = File(dir, "source.db").apply { writeBytes(ByteArray(512) { it.toByte() }) }
            val encrypted = File(dir, "backup.enc")
            val restored = File(dir, "restored.db")

            EncryptedBackup.encrypt(source, encrypted, "correct horse battery staple")
            EncryptedBackup.decrypt(encrypted, restored, "correct horse battery staple")

            assertArrayEquals(source.readBytes(), restored.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrongPasswordDoesNotProduceUsableRestore() {
        val dir = Files.createTempDirectory("daftari-encrypted-backup-test").toFile()
        try {
            val source = File(dir, "source.db").apply { writeBytes(ByteArray(512) { it.toByte() }) }
            val encrypted = File(dir, "backup.enc")
            val restored = File(dir, "restored.db")
            EncryptedBackup.encrypt(source, encrypted, "right-password")

            val result = runCatching { EncryptedBackup.decrypt(encrypted, restored, "wrong-password") }

            assertFalse("فك التشفير بكلمة خاطئة يجب أن يفشل", result.isSuccess)
        } finally {
            dir.deleteRecursively()
        }
    }
}
