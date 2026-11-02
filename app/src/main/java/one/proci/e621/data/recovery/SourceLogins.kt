package one.proci.e621.data.recovery

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional sign-ins that let deleted-post recovery reach login-gated source pages (Fur Affinity
 * only, as on desktop). Only the site's session cookies are kept, encrypted with an Android
 * Keystore key, in `noBackupFilesDir` - which Android never backs up or device-transfers, and
 * which the settings export doesn't include - so a session can't leave this device.
 */
object SourceLogins {

    /** A site the user can sign in to. [cookieNames] are the session cookies kept after login. */
    enum class Site(
        val key: String,
        val hosts: Set<String>,
        val loginUrl: String,
        val checkUrl: String,
        val loggedInMarker: String,
        val cookieNames: List<String>,
    ) {
        FUR_AFFINITY(
            key = "furaffinity",
            hosts = setOf("www.furaffinity.net", "furaffinity.net"),
            loginUrl = "https://www.furaffinity.net/login/",
            checkUrl = "https://www.furaffinity.net/",
            loggedInMarker = "/logout/",
            cookieNames = listOf("a", "b"),
        ),
    }

    private const val KEY_ALIAS = "source_logins"
    private lateinit var dir: File
    private val cookies = mutableMapOf<Site, String>()
    private val _signedIn = MutableStateFlow<Set<Site>>(emptySet())
    val signedIn: StateFlow<Set<Site>> = _signedIn

    /** Loads saved sessions; called once from Application.onCreate. */
    fun init(context: Context) {
        dir = File(context.noBackupFilesDir, "source_logins").apply { mkdirs() }
        Site.entries.forEach { site ->
            val file = File(dir, site.key)
            if (!file.exists()) return@forEach
            val cookie = runCatching { decrypt(file.readText()) }.getOrNull()
            if (cookie != null) cookies[site] = cookie else file.delete() // e.g. key lost after a restore
        }
        _signedIn.value = cookies.keys.toSet()
    }

    /** The saved session cookie for a request to [host], if that host belongs to a signed-in site. */
    fun cookieFor(host: String): String? = synchronized(cookies) {
        val h = host.lowercase()
        cookies.entries.firstOrNull { h in it.key.hosts }?.value
    }

    /** Keeps only [site]'s session cookies out of a WebView `Cookie` header string; null if any is missing. */
    fun sessionCookie(site: Site, rawCookieHeader: String?): String? {
        val all = rawCookieHeader.orEmpty().split(';').mapNotNull {
            val (k, v) = it.trim().split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to v
        }.toMap()
        val parts = site.cookieNames.map { name -> all[name]?.takeIf { it.isNotEmpty() }?.let { "$name=$it" } ?: return null }
        return parts.joinToString("; ")
    }

    /** True when [cookie] really is a logged-in session (the site's page shows its logout link). */
    suspend fun verify(site: Site, cookie: String): Boolean = withContext(Dispatchers.IO) {
        SourceRecovery.fetchPageWithCookie(site.checkUrl, cookie)?.contains(site.loggedInMarker) == true
    }

    suspend fun save(site: Site, cookie: String) = withContext(Dispatchers.IO) {
        File(dir, site.key).writeText(encrypt(cookie))
        synchronized(cookies) { cookies[site] = cookie }
        _signedIn.value = cookies.keys.toSet()
    }

    suspend fun signOut(site: Site) = withContext(Dispatchers.IO) {
        File(dir, site.key).delete()
        synchronized(cookies) { cookies.remove(site) }
        _signedIn.value = cookies.keys.toSet()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12))
    }
}
