package com.daftari.ledger.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سياسة الاحتفاظ بالنسخ منطق صرفي (بلا Android) فيُختبر على JVM.
 * يضمن ألا تحذف السياسة النسخ المشفّرة اليدوية، وأن «الاحتفاظ بالكل» لا يحذف شيئًا،
 * وأن التدوير يبقي الأحدث فقط.
 */
class BackupRetentionTest {

    private fun auto(timestamp: Long) = BackupFile("daftari-backup-$timestamp.db", timestamp, 1000L)

    @Test
    fun keepAllDeletesNothing() {
        val files = listOf(auto(1), auto(2), auto(3))
        assertTrue(BackupRetention.selectDeletions(files, BackupRetention.KEEP_ALL).isEmpty())
    }

    @Test
    fun keepsNewestAndDropsOlder() {
        val files = (1L..10L).map { auto(it) }
        val deletions = BackupRetention.selectDeletions(files, 7)
        assertEquals(3, deletions.size)
        assertEquals(setOf(1L, 2L, 3L), deletions.map { it.lastModified }.toSet())
    }

    @Test
    fun neverDeletesEncryptedManualBackups() {
        val files = listOf(
            BackupFile("daftari-backup-5.enc", 5L, 10L),
            auto(4),
            auto(3)
        )
        val deletions = BackupRetention.selectDeletions(files, 1)
        assertEquals(listOf("daftari-backup-3.db"), deletions.map { it.name })
    }

    @Test
    fun ignoresSafetyCopiesAndForeignFiles() {
        val files = listOf(
            BackupFile("pre-restore-9.db", 9L, 10L),
            BackupFile("random.txt", 8L, 10L),
            auto(2),
            auto(1)
        )
        val deletions = BackupRetention.selectDeletions(files, 1)
        assertEquals(listOf("daftari-backup-1.db"), deletions.map { it.name })
    }

    @Test
    fun underKeepDeletesNothing() {
        val files = listOf(auto(1), auto(2))
        assertTrue(BackupRetention.selectDeletions(files, 7).isEmpty())
    }
}
