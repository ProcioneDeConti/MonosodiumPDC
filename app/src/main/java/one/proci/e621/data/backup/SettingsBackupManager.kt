package one.proci.e621.data.backup

import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.proci.e621.data.settings.UserSettings

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class BackupEnvelope(
    val version: Int = 1,
    val encrypted: Boolean,
    val salt: String? = null,
    val iv: String? = null,
    val payload: String,
)

class SettingsBackupException(message: String) : Exception(message)

/**
 * Reads/writes the on-disk format for Settings > Backup & Restore. A file is a [BackupEnvelope]
 * JSON object; `payload` is base64 - either the backup JSON directly (unencrypted) or an
 * AES-256-GCM ciphertext of it, keyed by a PBKDF2 stretch of the user's chosen password (see
 * [encryptBackup]/[decryptBackup]). The password itself is never stored, only `salt`/`iv`.
 */
object SettingsBackupManager {

    /** Peeks at a picked file's envelope to tell the caller whether to prompt for a password before [import]. */
    fun isEncrypted(fileContents: String): Boolean =
        parseEnvelope(fileContents).encrypted

    /** Pass a null/blank [password] to export unencrypted (the API key ends up in plain text in the file). */
    fun export(settings: UserSettings, password: String?): String {
        val plaintext = json.encodeToString(settings.toBackup()).toByteArray(Charsets.UTF_8)
        val envelope = if (password.isNullOrEmpty()) {
            BackupEnvelope(encrypted = false, payload = Base64.getEncoder().encodeToString(plaintext))
        } else {
            val enc = encryptBackup(plaintext, password)
            BackupEnvelope(
                encrypted = true,
                salt = Base64.getEncoder().encodeToString(enc.salt),
                iv = Base64.getEncoder().encodeToString(enc.iv),
                payload = Base64.getEncoder().encodeToString(enc.ciphertext),
            )
        }
        return json.encodeToString(envelope)
    }

    /** [password] is ignored for an unencrypted file; pass null/blank when [isEncrypted] said false. */
    fun import(fileContents: String, password: String?): SettingsBackup {
        val envelope = parseEnvelope(fileContents)
        val plaintext = if (!envelope.encrypted) {
            decodeBase64(envelope.payload)
        } else {
            if (password.isNullOrEmpty()) throw SettingsBackupException("This backup is password-protected")
            val salt = envelope.salt?.let(::decodeBase64)
            val iv = envelope.iv?.let(::decodeBase64)
            if (salt == null || iv == null) throw SettingsBackupException("Backup file is missing encryption data")
            try {
                decryptBackup(decodeBase64(envelope.payload), salt, iv, password)
            } catch (e: AEADBadTagException) {
                throw SettingsBackupException("Incorrect password")
            }
        }
        return try {
            json.decodeFromString<SettingsBackup>(plaintext.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw SettingsBackupException("Backup file is corrupted")
        }
    }

    private fun parseEnvelope(fileContents: String): BackupEnvelope = try {
        json.decodeFromString<BackupEnvelope>(fileContents)
    } catch (e: Exception) {
        throw SettingsBackupException("Not a valid e621 settings backup file")
    }

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw SettingsBackupException("Backup file is corrupted")
    }
}
