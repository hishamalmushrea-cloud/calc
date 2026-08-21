package com.daftari.ledger.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * تشفير النسخ الاحتياطية بكلمة مرور.
 *
 * الصيغة: رأس (magic + version + salt + IV) متبوعًا بنص مشفّر AES/GCM.
 * المفتاح يُشتق من كلمة المرور عبر PBKDF2WithHmacSHA256.
 */
object EncryptedBackup {
    private val MAGIC = "DFTBENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val BUFFER = 64 * 1024

    fun encrypt(src: File, dest: File, password: String) {
        require(password.isNotEmpty()) { "أدخل كلمة مرور" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        FileOutputStream(dest).use { fos ->
            val dos = DataOutputStream(fos)
            dos.write(MAGIC)
            dos.writeInt(1) // version
            dos.writeInt(salt.size); dos.write(salt)
            dos.writeInt(iv.size); dos.write(iv)
            dos.flush()
            FileInputStream(src).use { input ->
                val cos = CipherOutputStream(fos, cipher)
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    cos.write(buf, 0, n)
                }
                cos.close()
            }
        }
    }

    fun decrypt(src: File, dest: File, password: String) {
        require(password.isNotEmpty()) { "أدخل كلمة مرور" }
        try {
            DataInputStream(FileInputStream(src)).use { dis ->
                val magic = ByteArray(MAGIC.size)
                dis.readFully(magic)
                if (!magic.contentEquals(MAGIC)) error("نسخة غير صالحة")
                dis.readInt() // version
                val salt = ByteArray(dis.readInt()); dis.readFully(salt)
                val iv = ByteArray(dis.readInt()); dis.readFully(iv)
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                FileOutputStream(dest).use { fos ->
                    val cis = CipherInputStream(dis, cipher)
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = cis.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                    }
                }
            }
        } catch (e: AEADBadTagException) {
            error("كلمة المرور غير صحيحة أو النسخة تالفة")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }
}
