package one.proci.e621.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** OWASP's 2023-current minimum for PBKDF2-HMAC-SHA256. */
private const val PBKDF2_ITERATIONS = 210_000
private const val KEY_LENGTH_BITS = 256
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
const val BACKUP_SALT_BYTES = 16

internal data class Encrypted(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
    val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    return SecretKeySpec(raw, "AES")
}

internal fun encryptBackup(plaintext: ByteArray, password: String): Encrypted {
    val salt = ByteArray(BACKUP_SALT_BYTES).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
    return Encrypted(salt, iv, cipher.doFinal(plaintext))
}

/** Throws [javax.crypto.AEADBadTagException] on a wrong password or corrupted/tampered ciphertext. */
internal fun decryptBackup(ciphertext: ByteArray, salt: ByteArray, iv: ByteArray, password: String): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(ciphertext)
}
