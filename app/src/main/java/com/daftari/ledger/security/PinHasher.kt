package com.daftari.ledger.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * تجزئة رمز القفل محليًا.
 *
 * الصيغة الجديدة: `"<saltHex>$<sha256Hex>"` حيث salt عشوائي 16 بايت لكل عملية حفظ.
 * لا تُخزَّن صيغة النص أبدًا؛ فقط التجزئة المُملّحة.
 *
 * التوافق مع الرموز القديمة: أي قيمة قديمة بلا فاصل `$`
 * (كانت `pin.toByteArray().contentHashCode()`) تُتحقق منها بالطريقة القديمة.
 */
object PinHasher {

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.toHex() + "$" + digest(salt, pin)
    }

    fun verify(pin: String, stored: String): Boolean {
        val sep = stored.indexOf('$')
        if (sep < 0) {
            // قيمة قديمة (contentHashCode) — توافق رجعي
            return stored == pin.toByteArray().contentHashCode().toString()
        }
        val salt = stored.substring(0, sep).fromHex()
        val expected = digest(salt, pin).toByteArray(Charsets.US_ASCII)
        val actual = stored.substring(sep + 1).toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun digest(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "طول hex فردي" }
        return ByteArray(length / 2) { i ->
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "hex غير صالح" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
