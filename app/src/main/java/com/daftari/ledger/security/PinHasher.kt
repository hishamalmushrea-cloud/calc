package com.daftari.ledger.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * تجزئة رمز القفل محليًا.
 *
 * الصيغة الحالية (الأقوى): `pbkdf2$<iterations>$<saltHex>$<hashHex>`
 * عبر PBKDF2WithHmacSHA256 — بطيئة عمدًا لتصعيب تخمين رمز قصير.
 *
 * التوافق الرجعي:
 * - تنسيق وسيط قديم `saltHex$sha256Hex` (SHA-256 + ملح).
 * - تنسيق أقدم `pin.toByteArray().contentHashCode()` (بدون `$`).
 */
object PinHasher {

    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITERATIONS)
        return "$PREFIX\$$ITERATIONS\$${salt.toHex()}\$${hash.toHex()}"
    }

    fun verify(pin: String, stored: String): Boolean = when {
        stored.startsWith("$PREFIX$") -> {
            val parts = stored.split('$')
            val iterations = parts.getOrNull(1)?.toIntOrNull()
            val salt = parts.getOrNull(2)?.fromHexOrNull()
            val expected = parts.getOrNull(3)?.fromHexOrNull()
            if (iterations == null || salt == null || expected == null) false
            else MessageDigest.isEqual(pbkdf2(pin, salt, iterations), expected)
        }
        stored.contains('$') -> {
            // تنسيق وسيط: saltHex$sha256Hex
            val sep = stored.indexOf('$')
            val salt = stored.substring(0, sep).fromHexOrNull()
            if (salt == null) false
            else {
                val expected = sha256(salt, pin).toByteArray(Charsets.US_ASCII)
                val actual = stored.substring(sep + 1).toByteArray(Charsets.US_ASCII)
                MessageDigest.isEqual(expected, actual)
            }
        }
        else -> stored == pin.toByteArray().contentHashCode().toString() // قديم
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun sha256(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }

    private fun String.fromHexOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
