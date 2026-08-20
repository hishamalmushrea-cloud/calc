package com.daftari.ledger.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test fun hashAndVerifyCorrect() {
        val h = PinHasher.hash("1234")
        assertTrue(h.startsWith("pbkdf2$"))
        assertTrue(PinHasher.verify("1234", h))
        assertFalse(PinHasher.verify("0000", h))
    }

    @Test fun differentSaltsProduceDifferentHashes() {
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test fun legacyContentHashStillWorks() {
        val old = "1234".toByteArray().contentHashCode().toString()
        assertTrue(PinHasher.verify("1234", old))
        assertFalse(PinHasher.verify("1111", old))
    }

    @Test fun intermediateSha256SaltFormatStillWorks() {
        val salt = ByteArray(16)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hex = md.digest("1234".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val stored = "00000000000000000000000000000000\$" + hex
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("1111", stored))
    }

    @Test fun malformedStoredValuesFailSafely() {
        assertFalse(PinHasher.verify("1234", "deadbeef\$abcd"))
        assertFalse(PinHasher.verify("1234", "pbkdf2\$abc\$zz\$ff"))
        assertFalse(PinHasher.verify("1234", "pbkdf2\$120000\$deadbeef\$abcd"))
    }
}
