package com.daftari.ledger.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اختيار أحدث نسخة WebDAV قابلة للاستعادة — منطق صرفٍ يُختبر بلا شبكة.
 *
 * كان الاختيار سابقًا `maxOrNull()` على الأسماء، وهو ترتيب أبجدي يقدّم
 * `999999999` على `1700000000000`، ويقبل نسخة مشفّرة لا يمرّ بها مسار الاستعادة.
 */
class CloudBackupManagerTest {

    @Test
    fun picksTheNewestByTimestampNotAlphabetically() {
        assertEquals(
            "daftari-backup-1700000000000.db",
            latestRestorableWebDavBackup(
                listOf("daftari-backup-1700000000000.db", "daftari-backup-999999999.db")
            )
        )
    }

    @Test
    fun skipsEncryptedBackupsBecauseTheyNeedAPassword() {
        assertEquals(
            "daftari-backup-1600000000000.db",
            latestRestorableWebDavBackup(
                listOf("daftari-backup-1700000000000.enc", "daftari-backup-1600000000000.db")
            )
        )
    }

    @Test
    fun returnsNullWhenNothingIsRestorable() {
        assertNull(latestRestorableWebDavBackup(listOf("daftari-backup-1700000000000.enc", "notes.txt")))
        assertNull(latestRestorableWebDavBackup(emptyList()))
    }

    @Test
    fun ignoresUnrelatedFiles() {
        assertEquals(
            "daftari-backup-1700000000000.db",
            latestRestorableWebDavBackup(
                listOf("readme.txt", "pre-restore-1.db", "daftari-backup-1700000000000.db")
            )
        )
    }
}
