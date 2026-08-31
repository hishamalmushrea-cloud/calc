package com.daftari.ledger.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** قرار كفاية المساحة منطق صرفي فيُختبر على JVM. */
class BackupSpacePolicyTest {

    private val mb = 1024L * 1024

    @Test
    fun enoughSpaceForSmallDatabase() {
        // قاعدة 5MB تحتاج 5*2 + 50 = 60MB؛ المتاح 200MB يكفي.
        assertTrue(BackupSpacePolicy.hasRoom(200 * mb, 5 * mb))
    }

    @Test
    fun rejectsWhenBelowMargin() {
        // قاعدة 5MB تحتاج 60MB؛ المتاح 40MB لا يكفي.
        assertFalse(BackupSpacePolicy.hasRoom(40 * mb, 5 * mb))
    }

    @Test
    fun rejectsWhenExactlyAtBoundaryMinusOne() {
        val db = 10 * mb
        val required = db * 2 + BackupSpacePolicy.MIN_FREE_BYTES
        assertFalse(BackupSpacePolicy.hasRoom(required - 1, db))
        assertTrue(BackupSpacePolicy.hasRoom(required, db))
    }

    @Test
    fun zeroDatabaseStillNeedsMargin() {
        assertFalse(BackupSpacePolicy.hasRoom(BackupSpacePolicy.MIN_FREE_BYTES - 1, 0))
        assertTrue(BackupSpacePolicy.hasRoom(BackupSpacePolicy.MIN_FREE_BYTES, 0))
    }

    @Test
    fun largeDatabaseNeedsDoublePlusMargin() {
        val db = 500 * mb
        assertTrue(BackupSpacePolicy.hasRoom(db * 2 + BackupSpacePolicy.MIN_FREE_BYTES, db))
        assertFalse(BackupSpacePolicy.hasRoom(db * 2, db))
    }
}
