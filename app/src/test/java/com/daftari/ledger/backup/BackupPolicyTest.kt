package com.daftari.ledger.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    @Test fun newerHeadFromAnotherDeviceIsDetectedWithoutDeletingIt() {
        val settings = settings(last = "old")
        val backups = listOf(
            remote("new", 300, device = "other", parent = "old"),
            remote("old", 200, device = "this"),
            remote("older", 100, device = "this")
        )
        assertTrue(BackupPolicy.hasDiverged(settings, backups))
        assertFalse(BackupPolicy.deletionCandidates(backups, settings).any { it.deviceId == "other" })
    }

    @Test fun conflictAndNewestBackupsAreNeverAutomaticallyDeleted() {
        val settings = settings(keepDaily = 1, keepWeekly = 0)
        val backups = listOf(
            remote("new", 400),
            remote("conflict", 300, conflict = true),
            remote("old", 200),
            remote("older", 100)
        )
        val deleted = BackupPolicy.deletionCandidates(backups, settings).map { it.id }
        assertFalse("new" in deleted)
        assertFalse("conflict" in deleted)
        assertTrue("old" in deleted)
        assertTrue("older" in deleted)
    }

    @Test fun sameKnownHeadIsNotConflict() {
        val settings = settings(last = "head")
        assertFalse(BackupPolicy.hasDiverged(settings, listOf(remote("head", 100))))
    }

    private fun settings(last: String = "", keepDaily: Int = 7, keepWeekly: Int = 4) = GoogleBackupSettings(
        accountEmail = "a@example.com", accountSubject = "sub", deviceId = "this", datasetId = "data",
        lastRemoteId = last, keepDaily = keepDaily, keepWeekly = keepWeekly
    )

    private fun remote(
        id: String,
        time: Long,
        device: String = "this",
        parent: String? = null,
        conflict: Boolean = false
    ) = RemoteBackup(id, "$id.dfb", time, "", 1, device, "Phone", "data", parent, 7, "1", "", "", conflict)
}
