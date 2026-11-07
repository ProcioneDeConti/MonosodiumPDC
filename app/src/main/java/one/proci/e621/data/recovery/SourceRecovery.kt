package one.proci.e621.data.recovery

import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import one.proci.e621.BuildConfig
import one.proci.e621.data.model.Post
import one.proci.e621.data.settings.Site
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

/**
 * Deleted-post recovery, ported from the desktop app's `source_fetch.rs`. e621 keeps a deleted
 * post's sources plus its file's md5, byte size, dimensions and format (only the URLs are null),
 * so this fetches the file from the post's own source URLs and grades every candidate against what
 * e621 kept. Strictly opt-in: nothing here runs until the user asks.
 *
 * Per source: thumbnail/CDN URLs are first upgraded to their originals ([upgradeSource]); then a
 * site-specific resolver if the host has one (X, Bluesky, Pixiv, DeviantArt, HentaiVox, Fur
 * Affinity, Inkbunny, Itaku, Weasyl, SoFurry - unofficial endpoints that may break), else the URL
 * itself if it serves a file, else the page's `og:image`. Sources on different hosts run in
 * parallel; md5 lookups on other boorus run alongside. When nothing reaches
 * [Grade.SAME_DIMENSIONS], Wayback Machine captures of each source (and of e621's own file) are
 * tried too. Everything found is written to [RecoveredStore].
 */
object SourceRecovery {

    /** Status updates for the viewer's loading line; [Scored] and [Failed] also go in its log. */
    sealed interface Progress {
        data class Fetching(val host: String, val index: Int, val total: Int) : Progress
        data class Mirror(val name: String) : Progress
        data class Downloading(val host: String, val index: Int, val total: Int) : Progress
        data class Comparing(val host: String) : Progress
        data class Scored(val host: String, val width: Int, val height: Int, val ext: String, val grade: Grade?) : Progress
        data class Failed(val host: String, val reason: String) : Progress
        data class Archive(val host: String) : Progress
        data class ArchiveCapture(val timestamp: String, val host: String) : Progress
        data object TimeLimit : Progress
    }

    /** Cap on one recovered file - e621's own upload limit. */
    private const val MAX_MEDIA_BYTES = 100L * 1024 * 1024
    private const val MAX_HTML_BYTES = 2L * 1024 * 1024
    private const val PAGE_TIMEOUT_S = 20L
    private const val MEDIA_TIMEOUT_S = 120L

    /** Wall-clock budgets for the two phases; whatever was found when one runs out is kept. */
    private const val LIVE_BUDGET_MS = 120_000L
    private const val ARCHIVE_BUDGET_MS = 120_000L

    /** Hosts tried in parallel; one host's sources always run one after another. */
    private const val PARALLEL_HOSTS = 3
    private const val WAYBACK_CAPTURES_PER_TARGET = 4
    private const val MAX_CANDIDATES_PER_SOURCE = 12
    private const val LOGIN_REQUIRED = "login required"

    private const val ACCEPT_ANY = "image/*,video/*,text/html;q=0.8,*/*;q=0.5"
    /** Tumblr's media CDN answers a request that accepts HTML with a viewer page, so file fetches never ask for it. */
    private const val ACCEPT_MEDIA = "image/*,video/*,*/*;q=0.5"

    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "MonosodiumPDC-Android/${BuildConfig.VERSION_NAME} (source recovery for deleted e621 posts)"

    fun webSources(post: Post): List<String> =
        post.sources.map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }

    /** The post's md5, if e621 gave a well-formed one (lowercased). */
    fun validMd5(md5: String?): String? =
        md5?.trim()?.lowercase()?.takeIf { m -> m.length == 32 && m.all { it.isDigit() || it in 'a'..'f' } }

    /** Whether there's anything to search with: a web source, or the md5 (mirrors + e621's own archived file). */
    fun canSearch(post: Post) = webSources(post).isNotEmpty() || validMd5(post.file.md5) != null

    // --- SSRF guard -------------------------------------------------------------------------
    // Source URLs are user-edited post data, so they must never reach loopback, LAN, link-local or
    // other non-public addresses. Enforced on literal-IP hosts and every redirect hop (network
    // interceptor) and on every DNS answer (PublicOnlyDns).

    internal fun isPublic(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
            addr.isAnyLocalAddress || addr.isMulticastAddress
        ) return false
        return when (addr) {
            is Inet4Address -> {
                val o = addr.address.map { it.toInt() and 0xff }
                !(o[0] == 0 || o[0] >= 240 ||
                    (o[0] == 100 && (o[1] and 0xc0) == 64) ||
                    (o[0] == 198 && (o[1] and 0xfe) == 18) ||
                    (o[0] == 192 && o[1] == 0 && o[2] == 2) ||
                    (o[0] == 198 && o[1] == 51 && o[2] == 100) ||
                    (o[0] == 203 && o[1] == 0 && o[2] == 113))
            }
            is Inet6Address -> {
                val b = addr.address
                val first = ((b[0].toInt() and 0xff) shl 8) or (b[1].toInt() and 0xff)
                val mapped = b.take(10).all { it.toInt() == 0 } && b[10].toInt() == -1 && b[11].toInt() == -1
                if (mapped) return isPublic(InetAddress.getByAddress(b.copyOfRange(12, 16)))
                (first and 0xfe00) != 0xfc00
            }
            else -> false
        }
    }

    private fun isIpLiteral(host: String) =
        host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    internal fun checkUrl(url: HttpUrl) {
        val host = url.host.lowercase().trimEnd('.')
        val blocked = if (isIpLiteral(host)) {
            !isPublic(InetAddress.getByName(host))
        } else {
            host.isEmpty() || host == "localhost" || host.endsWith(".localhost")
        }
        if (blocked) throw IOException("blocked non-public address: ${url.host}")
    }

    private object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val public = Dns.SYSTEM.lookup(hostname).filter(::isPublic)
            if (public.isEmpty()) throw IOException("$hostname resolves only to non-public addresses")
            return public
        }
    }

    /**
     * The cookie a request to [url] carries: a saved site session (Settings > Source logins) goes
     * only to that site's own pages; Weasyl's 18+ click-through is just a `ro=<submission id>`
     * cookie, so its submission pages get that. Added per hop, so a redirect elsewhere gets none.
     */
    private fun sessionCookieFor(url: HttpUrl): String? = when (url.host.lowercase()) {
        "www.furaffinity.net", "furaffinity.net" -> SourceLogins.cookieFor(url.host)
        "www.weasyl.com", "weasyl.com" -> weasylSubmissionId(url)?.let { "ro=$it" }
        else -> null
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Idle time between reads; whole-request limits are set per call.
            .readTimeout(20, TimeUnit.SECONDS)
            .dns(PublicOnlyDns)
            .addNetworkInterceptor(Interceptor { chain ->
                val request = chain.request()
                checkUrl(request.url)
                val cookie = if (request.header("Cookie") == null) sessionCookieFor(request.url) else null
                chain.proceed(if (cookie != null) request.newBuilder().header("Cookie", cookie).build() else request)
            })
            .build()
    }

    /** Fetches a page with an explicit cookie (sign-in verification); null on any failure. */
    internal fun fetchPageWithCookie(url: String, cookie: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Cookie", cookie).build()
        client.newCall(request).execute().use { r -> if (r.isSuccessful) r.readCapped(MAX_HTML_BYTES).decodeToString() else null }
    } catch (_: Exception) {
        null
    }

    // --- Politeness -------------------------------------------------------------------------
    // Shared by every recovery (a bulk run is many back to back): about one request a second to any
    // one site (burst 2). The Internet Archive throttles busy clients, so all of archive.org shares
    // one slower allowance, and a 429 from it pauses the Wayback steps for a few minutes.

    private class Bucket(val periodMs: Long, val burst: Int) {
        var tat = 0L
    }

    private val buckets = HashMap<String, Bucket>()
    private const val WAYBACK_PAUSE_MS = 300_000L
    @Volatile private var waybackPausedUntil = 0L

    private fun isArchiveHost(url: HttpUrl) = url.host == "archive.org" || url.host.endsWith(".archive.org")

    private suspend fun throttle(url: HttpUrl) {
        val archive = isArchiveHost(url)
        val key = if (archive) "archive.org" else url.host.removePrefix("www.")
        val wait = synchronized(buckets) {
            val b = buckets.getOrPut(key) { Bucket(if (archive) 1500 else 1000, 2) }
            val now = SystemClock.elapsedRealtime()
            val tat = maxOf(b.tat, now)
            b.tat = tat + b.periodMs
            (tat - b.periodMs * (b.burst - 1) - now).coerceAtLeast(0)
        }
        if (wait > 0) delay(wait)
    }

    private fun noteStatus(url: HttpUrl, code: Int) {
        if (code == 429 && isArchiveHost(url)) waybackPausedUntil = SystemClock.elapsedRealtime() + WAYBACK_PAUSE_MS
    }

    private fun waybackPaused() = SystemClock.elapsedRealtime() < waybackPausedUntil

    // --- HTTP helpers -----------------------------------------------------------------------

    /** Runs [this] call asynchronously and reads it on OkHttp's thread; cancelling the coroutine cancels the call. */
    private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                cont.resumeWith(runCatching { response.use(read) })
            }
        })
    }

    private sealed interface Outcome<out T> {
        class Done<T>(val value: T) : Outcome<T>
        class Retry(val waitMs: Long) : Outcome<Nothing>
    }

    private fun retryAfterMs(response: Response): Long =
        (response.header("Retry-After")?.trim()?.toLongOrNull() ?: 2L).coerceIn(1, 5) * 1000

    private fun isTimeout(e: IOException) = e is InterruptedIOException || e.message.orEmpty().contains("timeout", ignoreCase = true)

    /**
     * GET [url] with one retry after a rate-limit/overload answer (429/502/503/504) or a timeout.
     * Non-2xx answers become `HTTP <code>` errors. [read] gets the successful response.
     */
    private suspend fun <T> send(url: HttpUrl, timeoutS: Long, headers: Map<String, String>, read: (Response) -> T): T {
        if (url.scheme != "http" && url.scheme != "https") throw IOException("unsupported scheme")
        checkUrl(url)
        for (attempt in 0..1) {
            throttle(url)
            val request = Request.Builder().url(url).header("User-Agent", userAgent).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            val call = client.newCall(request).apply { timeout().timeout(timeoutS, TimeUnit.SECONDS) }
            val outcome: Outcome<T> = try {
                call.await { r ->
                    noteStatus(url, r.code)
                    when {
                        attempt == 0 && r.code in setOf(429, 502, 503, 504) -> Outcome.Retry(retryAfterMs(r))
                        !r.isSuccessful -> throw IOException("HTTP ${r.code}")
                        else -> Outcome.Done(read(r))
                    }
                }
            } catch (e: IOException) {
                if (!isTimeout(e)) throw e
                if (attempt == 0) Outcome.Retry(1000) else throw IOException("timed out")
            }
            when (outcome) {
                is Outcome.Done -> return outcome.value
                is Outcome.Retry -> delay(outcome.waitMs)
            }
        }
        throw IOException("timed out")
    }

    /** Some hosts reject hotlinks by Referer; send the host's own origin (Pixiv's CDN wants pixiv.net). */
    private fun refererFor(url: HttpUrl): String =
        if (url.host.endsWith("pximg.net")) "https://www.pixiv.net/" else "${url.scheme}://${url.host}/"

    private fun Response.contentType(): String =
        header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()

    private fun Response.readCapped(limit: Long): ByteArray {
        val body = body
        if (body.contentLength() > limit) throw IOException("response too large")
        val source = body.source()
        val out = okio.Buffer()
        while (true) {
            if (source.read(out, 64 * 1024) == -1L) break
            if (out.size > limit) throw IOException("response too large")
        }
        return out.readByteArray()
    }

    private suspend fun getPage(url: HttpUrl): String =
        send(url, PAGE_TIMEOUT_S, mapOf("Referer" to refererFor(url), "Accept" to ACCEPT_ANY)) {
            it.readCapped(MAX_HTML_BYTES).decodeToString()
        }

    /** A lookup API's answer (small JSON/XML). */
    private suspend fun getText(url: String, query: List<Pair<String, String>> = emptyList(), headers: Map<String, String> = emptyMap()): String {
        val u = url.toHttpUrlOrNull()?.newBuilder()?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }?.build()
            ?: throw IOException("invalid URL")
        return send(u, PAGE_TIMEOUT_S, headers) { it.readCapped(MAX_HTML_BYTES).decodeToString() }
    }

    private suspend fun getJson(url: String, query: List<Pair<String, String>> = emptyList(), headers: Map<String, String> = emptyMap()): JsonElement {
        val text = getText(url, query, headers + ("Accept" to "application/json"))
        return try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            throw IOException("unexpected answer")
        }
    }

    /** Downloads one file and checks it really is an image/video the viewer can show. */
    private suspend fun fetchMedia(url: HttpUrl): ByteArray =
        send(url, MEDIA_TIMEOUT_S, mapOf("Referer" to refererFor(url), "Accept" to ACCEPT_MEDIA)) { r ->
            val ct = r.contentType()
            if (ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")) throw IOException("not an image ($ct)")
            val bytes = r.readCapped(MAX_MEDIA_BYTES)
            if (sniffMime(bytes) == null) throw IOException("not a usable image or video (${ct.ifEmpty { "unknown type" }})")
            bytes
        }

    private fun parse(s: String): HttpUrl = s.trim().toHttpUrlOrNull() ?: throw IOException("invalid URL")

    private fun hostOf(url: HttpUrl) = url.host.removePrefix("www.")
    fun hostOf(s: String) = s.toHttpUrlOrNull()?.let(::hostOf) ?: s

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private operator fun JsonElement?.get(i: Int): JsonElement? = (this as? JsonArray)?.getOrNull(i)
    private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray).orEmpty()

    // --- File types and grading -------------------------------------------------------------

    fun normalizeExt(ext: String): String =
        ext.trim().removePrefix(".").lowercase().let { if (it == "jpeg") "jpg" else it }

    private fun isVideoExt(ext: String) = normalizeExt(ext) in setOf("webm", "mp4")

    fun extForMime(mime: String) = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "video/webm" -> "webm"
        "video/mp4" -> "mp4"
        else -> ""
    }

    /**
     * The file's real type from its leading bytes. Servers mislabel files all the time (an
     * octet-stream image, an HTML error page served as image/png), so this decides what a download is.
     */
    internal fun sniffMime(b: ByteArray): String? {
        fun at(i: Int, vararg bytes: Int) = b.size >= i + bytes.size && bytes.indices.all { (b[i + it].toInt() and 0xff) == bytes[it] }
        fun ascii(i: Int, s: String) = b.size >= i + s.length && s.indices.all { b[i + it].toInt() == s[it].code }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> "video/webm"
            ascii(4, "ftyp") && b.size >= 12 -> when (String(b, 8, 4, Charsets.ISO_8859_1)) {
                "avif", "avis" -> "image/avif"
                "heic", "heix", "heim", "heis", "mif1", "msf1" -> null
                else -> "video/mp4"
            }
            else -> null
        }
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** What e621 still knows about a deleted post's file. */
    private class Expected(val md5: String?, val size: Long, val width: Int, val height: Int, val ext: String) {
        val isVideo get() = isVideoExt(ext)
    }

    /** Grades fetched bytes against the post's original file; null when they aren't a file the viewer can show. */
    private fun scoreCandidate(expected: Expected, source: String, bytes: ByteArray, imageUrl: HttpUrl, viaArchive: Boolean): RecoveredImage? {
        val mime = sniffMime(bytes) ?: return null
        val isVideo = mime.startsWith("video/")
        var w = 0
        var h = 0
        if (!isVideo) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            w = opts.outWidth
            h = opts.outHeight
            if (w <= 0 || h <= 0) return null
        }
        val notes = mutableListOf<Note>()
        val gotMd5 = md5Hex(bytes)
        val md5Match = expected.md5?.trim()?.equals(gotMd5, ignoreCase = true) == true

        val wantExt = normalizeExt(expected.ext)
        val gotExt = extForMime(mime)
        val extMatch = wantExt.isEmpty() || wantExt == gotExt
        if (!extMatch) notes += Note(Note.Kind.FORMAT, got = gotExt, want = wantExt)

        val haveDims = expected.width > 0 && expected.height > 0
        val sameDims = haveDims && w == expected.width && h == expected.height
        val sizeRatio = if (expected.size > 0) bytes.size.toDouble() / expected.size else 1.0

        val grade = when {
            md5Match -> Grade.EXACT
            expected.isVideo && !isVideo -> { notes += Note(Note.Kind.STILL_OF_VIDEO); Grade.MISMATCH }
            isVideo -> { notes += Note(Note.Kind.VIDEO_UNCHECKED); Grade.RESCALED }
            sameDims || !haveDims -> Grade.SAME_DIMENSIONS
            else -> {
                val aspectOff = kotlin.math.abs((w.toDouble() / h) / (expected.width.toDouble() / expected.height) - 1.0)
                if (aspectOff <= 0.015) Grade.RESCALED else Grade.MISMATCH
            }
        }
        if (!md5Match) {
            if (haveDims && !isVideo && !sameDims) notes += Note(Note.Kind.DIMENSIONS, width = w, height = h, wantWidth = expected.width, wantHeight = expected.height)
            if (expected.size > 0 && (sizeRatio < 0.9 || sizeRatio > 1.1)) {
                notes += Note(if (sizeRatio < 1.0) Note.Kind.SMALLER_FILE else Note.Kind.LARGER_FILE)
            }
        }
        if (grade == Grade.MISMATCH && !isVideo && !expected.isVideo) notes += Note(Note.Kind.SHAPE_DIFFERS)

        val closeness = if (expected.size > 0) {
            val r = sizeRatio.coerceAtLeast(1e-6)
            Math.round(100.0 * minOf(r, 1.0 / r))
        } else 0L
        val score = grade.ordinal * 1000L + (if (extMatch) 100 else 0) + closeness

        return RecoveredImage(
            md5 = gotMd5, sourceUrl = source, imageUrl = imageUrl.toString(), mime = mime, isVideo = isVideo,
            sizeBytes = bytes.size.toLong(), viaArchive = viaArchive, grade = grade, md5Match = md5Match,
            width = w, height = h, expectedWidth = expected.width, expectedHeight = expected.height,
            expectedSize = expected.size, notes = notes, score = score,
        )
    }

    // --- HTML scraping ----------------------------------------------------------------------

    /** Attribute-value entity decoding: the named entities seen in URLs plus every numeric form. */
    internal fun decodeEntities(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val end = s.indexOf(';', i)
                if (end in (i + 1)..(i + 10)) {
                    val entity = s.substring(i + 1, end)
                    val decoded: String? = when (entity) {
                        "amp" -> "&"
                        "quot" -> "\""
                        "apos" -> "'"
                        "lt" -> "<"
                        "gt" -> ">"
                        else -> if (entity.startsWith("#")) {
                            val num = entity.substring(1)
                            val code = if (num.startsWith("x") || num.startsWith("X")) num.substring(1).toIntOrNull(16) else num.toIntOrNull()
                            code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
                        } else null
                    }
                    if (decoded != null) {
                        out.append(decoded)
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** Value of `name="..."` (or single-quoted/unquoted) inside one tag's text, case-insensitive. */
    internal fun attr(tag: String, name: String): String? {
        val re = Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val m = re.find(tag) ?: return null
        return decodeEntities(m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: "")
    }

    /** Every `<name ...>` opening tag's text in [html], in order. */
    private fun tags(html: String, name: String): List<String> {
        val lower = html.lowercase()
        val open = "<$name"
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = lower.indexOf(open, from)
            if (start < 0) break
            val end = lower.indexOf('>', start).let { if (it < 0) lower.length else it }
            out += html.substring(start, end)
            from = maxOf(end, start + open.length)
        }
        return out
    }

    /** First `og:image` (preferred) or `twitter:image` declared in the page, resolved against it. */
    internal fun findMetaImage(html: String, base: HttpUrl): HttpUrl? {
        var og: String? = null
        var twitter: String? = null
        for (tag in tags(html, "meta")) {
            val key = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase()
            val content = attr(tag, "content")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            when (key) {
                "og:image", "og:image:url", "og:image:secure_url" -> if (og == null) og = content
                "twitter:image", "twitter:image:src" -> if (twitter == null) twitter = content
            }
            if (og != null) break
        }
        return base.resolve(og ?: twitter ?: return null)
    }

    private fun segments(url: HttpUrl) = url.pathSegments.filter { it.isNotEmpty() }
    private fun dedupe(urls: List<HttpUrl>) = urls.distinctBy { it.toString() }

    /** The id at the start of a path segment like `3978541` or `3978541-p2-some-title`. */
    private fun leadingId(seg: String): String? = seg.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }

    // --- Site resolvers ---------------------------------------------------------------------

    /** Mirrors X's web embed token: (id / 1e15 * pi) in base 36, zeros and the point removed. */
    internal fun twitterToken(id: String): String {
        val n = (id.toDoubleOrNull() ?: 0.0) / 1e15 * Math.PI
        val sb = StringBuilder(n.toLong().toString(36))
        var frac = n - n.toLong()
        repeat(11) {
            frac *= 36
            sb.append(Character.forDigit(frac.toInt(), 36))
            frac -= frac.toInt()
        }
        return sb.toString().replace("0", "")
    }

    /** One media item of a tweet: a photo's `pbs.twimg.com/media/<id>.<ext>` (no query), or a video's best mp4. */
    private sealed interface TweetMedia {
        data class Photo(val url: String) : TweetMedia
        data class Video(val url: HttpUrl) : TweetMedia
    }

    private fun tweetPhoto(url: String) = TweetMedia.Photo(url.substringBefore('?'))

    private fun syndicationMedia(o: JsonElement): List<TweetMedia> {
        val items = mutableListOf<TweetMedia>()
        for (m in o["mediaDetails"].arr()) {
            val kind = m["type"].str() ?: "photo"
            if (kind == "video" || kind == "animated_gif") {
                val best = m["video_info"]["variants"].arr()
                    .filter { it["content_type"].str() == "video/mp4" }
                    .maxByOrNull { (it["bitrate"] as? JsonPrimitive)?.longOrNull ?: 0 }
                    ?.let { it["url"].str()?.toHttpUrlOrNull() }
                if (best != null) items += TweetMedia.Video(best)
            } else {
                m["media_url_https"].str()?.let { items += tweetPhoto(it) }
            }
        }
        if (items.isEmpty()) items += o["photos"].arr().mapNotNull { it["url"].str()?.let(::tweetPhoto) }
        return items
    }

    /** FxTwitter (`{tweet: {media: {all: [...]}}}`) or vxTwitter (`{media_extended: [...]}`). */
    private fun mirrorMedia(o: JsonElement): List<TweetMedia> {
        val list = (o["tweet"]["media"]["all"] as? JsonArray) ?: o["media_extended"].arr()
        return list.mapNotNull { m ->
            val url = m["url"].str() ?: return@mapNotNull null
            when (m["type"].str() ?: "photo") {
                "video", "gif", "animated_gif" -> url.toHttpUrlOrNull()?.let { TweetMedia.Video(it) }
                else -> tweetPhoto(url)
            }
        }
    }

    /**
     * X/Twitter: X's own embed endpoint first; it answers an age-restricted tweet with a
     * TweetTombstone to logged-out clients, so then the FxTwitter and vxTwitter APIs, which do
     * return its media. All three only hand back X CDN URLs; the files are fetched from X as usual.
     */
    private suspend fun resolveTwitter(url: HttpUrl, expected: Expected): List<HttpUrl> {
        val seg = segments(url)
        val at = seg.indexOf("status").takeIf { it >= 0 } ?: throw IOException("not a tweet URL")
        val id = seg.getOrNull(at + 1)?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } } ?: throw IOException("no tweet id")
        val user = seg.getOrNull(at - 1)?.takeIf { it.isNotEmpty() && it != "i" } ?: "i"
        val photoIndex = if (seg.getOrNull(at + 2) == "photo") ((seg.getOrNull(at + 3)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0) else 0

        val problems = mutableListOf<String>()
        var media: List<TweetMedia> = emptyList()
        try {
            val o = getJson("https://cdn.syndication.twimg.com/tweet-result", listOf("id" to id, "token" to twitterToken(id)))
            if (o["__typename"].str() == "TweetTombstone") {
                problems += "X hides this tweet from logged-out viewers (age-restricted, or removed)"
            } else {
                media = syndicationMedia(o)
                if (media.isEmpty()) problems += "X's embed shows no media"
            }
        } catch (e: IOException) {
            problems += "X embed lookup failed (${e.message})"
        }
        for ((name, api) in listOf("FxTwitter" to "https://api.fxtwitter.com", "vxTwitter" to "https://api.vxtwitter.com")) {
            if (media.isNotEmpty()) break
            try {
                media = mirrorMedia(getJson("$api/$user/status/$id"))
                if (media.isEmpty()) problems += "$name: no media"
            } catch (e: IOException) {
                problems += "$name: ${e.message}"
            }
        }
        if (media.isEmpty()) throw IOException("tweet media not found (${problems.joinToString("; ")})")

        val want = normalizeExt(expected.ext)
        val items = media.map { m ->
            when (m) {
                is TweetMedia.Video -> listOf(m.url)
                is TweetMedia.Photo -> {
                    val stem = m.url.substringBeforeLast('.')
                    val ext = m.url.substringAfterLast('.', "")
                    listOfNotNull(
                        "${m.url}?name=orig".toHttpUrlOrNull(),
                        if (want.isNotEmpty() && !expected.isVideo && normalizeExt(ext) != want) "$stem?format=$want&name=orig".toHttpUrlOrNull() else null,
                    )
                }
            }
        }.filter { it.isNotEmpty() }
        if (items.isEmpty()) throw IOException("tweet media not found")
        val first = photoIndex.coerceAtMost(items.lastIndex)
        return items[first] + items.filterIndexed { i, _ -> i != first }.flatten()
    }

    private fun collectFullsize(v: JsonElement?, out: MutableList<String>) {
        when (v) {
            is JsonObject -> v.forEach { (k, child) ->
                val s = child.str()
                if (k == "fullsize" && s != null) out += s else collectFullsize(child, out)
            }
            is JsonArray -> v.forEach { collectFullsize(it, out) }
            else -> {}
        }
    }

    /** `cdn.bsky.app/img/<preset>/plain/<did>/<cid>@<fmt>` -> (did, cid). */
    private fun parseBskyCdn(url: HttpUrl): Pair<String, String>? {
        if (url.host != "cdn.bsky.app") return null
        val seg = segments(url)
        if (seg.size < 5 || seg[0] != "img" || seg[2] != "plain" || !seg[3].startsWith("did:")) return null
        val cid = seg[4].substringBefore('@').takeIf { it.isNotEmpty() } ?: return null
        return seg[3] to cid
    }

    /** The PDS (the author's own data server) a Bluesky DID lives on, from its DID document. */
    private suspend fun bskyPds(did: String): String? {
        val docUrl = when {
            did.startsWith("did:plc:") -> "https://plc.directory/$did"
            did.startsWith("did:web:") -> "https://${did.removePrefix("did:web:")}/.well-known/did.json"
            else -> return null
        }
        val doc = runCatching { getJson(docUrl) }.getOrNull() ?: return null
        return doc["service"].arr().firstOrNull { it["id"].str()?.endsWith("#atproto_pds") == true }
            ?.get("serviceEndpoint").str()?.trimEnd('/')
    }

    /**
     * For each Bluesky CDN image: the original upload from the author's PDS (`getBlob` - the CDN's
     * full-size copy is always a re-encoded JPEG, so only the blob can be exact), then the CDN copy.
     */
    private suspend fun bskyOriginals(urls: List<HttpUrl>): List<HttpUrl> {
        val pdsFor = HashMap<String, String?>()
        val out = mutableListOf<HttpUrl>()
        for (u in urls) {
            val parsed = parseBskyCdn(u)
            if (parsed == null) {
                out += u
                continue
            }
            val (did, cid) = parsed
            if (did !in pdsFor) pdsFor[did] = bskyPds(did)
            pdsFor[did]?.let { pds ->
                "$pds/xrpc/com.atproto.sync.getBlob".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("did", did)?.addQueryParameter("cid", cid)?.build()?.let { out += it }
            }
            "https://cdn.bsky.app/img/feed_fullsize/plain/$did/$cid@jpeg".toHttpUrlOrNull()?.let { out += it }
        }
        return dedupe(out)
    }

    private suspend fun resolveBluesky(url: HttpUrl): List<HttpUrl> {
        val seg = segments(url)
        if (seg.size < 4 || seg[0] != "profile" || seg[2] != "post") throw IOException("not a Bluesky post URL")
        val thread = try {
            getJson("https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread", listOf("uri" to "at://${seg[1]}/app.bsky.feed.post/${seg[3]}", "depth" to "0"))
        } catch (e: IOException) {
            throw IOException("post lookup failed (${e.message})")
        }
        val found = mutableListOf<String>()
        collectFullsize(thread["thread"]["post"]["embed"], found)
        if (found.isEmpty()) throw IOException("post has no images")
        return bskyOriginals(found.mapNotNull { it.toHttpUrlOrNull() })
    }

    private suspend fun resolvePixiv(url: HttpUrl): List<HttpUrl> {
        val seg = segments(url)
        val at = seg.indexOf("artworks").takeIf { it >= 0 } ?: throw IOException("not a Pixiv artwork URL")
        val id = seg.getOrNull(at + 1)?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } } ?: throw IOException("no artwork id")
        val pages = try {
            getJson("https://www.pixiv.net/ajax/illust/$id/pages", headers = mapOf("Referer" to "https://www.pixiv.net/"))
        } catch (e: IOException) {
            throw IOException("artwork lookup failed (${e.message}; R18 works need a login)")
        }
        val urls = pages["body"].arr().mapNotNull { it["urls"]["original"].str()?.toHttpUrlOrNull() }
        if (urls.isEmpty()) throw IOException("artwork has no pages")
        return urls
    }

    private suspend fun resolveDeviantArt(url: HttpUrl): List<HttpUrl> {
        val o = try {
            getJson("https://backend.deviantart.com/oembed", listOf("url" to url.toString()))
        } catch (e: IOException) {
            throw IOException("oEmbed lookup failed (${e.message})")
        }
        val chosen = (if (o["type"].str() == "photo") o["url"].str() else null) ?: o["thumbnail_url"].str()
            ?: throw IOException("oEmbed returned no image")
        return listOf(parse(chosen))
    }

    /** HentaiVox reader pages declare no meta image; the page image is `<img class="js-main-img">`. */
    private fun parseHentaiVox(html: String, base: HttpUrl): List<HttpUrl> {
        val tag = tags(html, "img").firstOrNull { t -> attr(t, "class")?.split(Regex("\\s+"))?.contains("js-main-img") == true }
            ?: throw IOException("no reader image on the page")
        val src = attr(tag, "src") ?: throw IOException("main image has no src")
        return listOf(base.resolve(src.trim()) ?: throw IOException("bad image URL"))
    }

    /**
     * Fur Affinity: the full-size file is the `submissionImg`'s `data-fullview-src` (the og:image
     * is a 600px thumbnail). Logged-out visitors get a "System Message" page for Mature/Adult work
     * whose og:image is the FA logo, so that's reported as final rather than scraped.
     */
    private fun parseFurAffinity(html: String, base: HttpUrl): List<HttpUrl> {
        val tag = tags(html, "img").firstOrNull { attr(it, "id") == "submissionImg" }
        if (tag != null) {
            val src = attr(tag, "data-fullview-src") ?: attr(tag, "src") ?: throw IOException("submission image has no src")
            return listOf(base.resolve(src.trim()) ?: throw IOException("bad image URL"))
        }
        val lower = html.lowercase()
        if (lower.contains("not in our database")) throw IOException("deleted from Fur Affinity (\"not in our database\")")
        if (lower.contains("you must log in")) {
            if (SourceLogins.cookieFor(base.host) != null) {
                throw IOException("$LOGIN_REQUIRED: your saved Fur Affinity sign-in has expired (FA answered as logged out) - sign in again under Settings > Account > Source logins")
            }
            throw IOException("$LOGIN_REQUIRED: Fur Affinity only shows this Mature/Adult submission to signed-in users - sign in under Settings > Account > Source logins, with Mature/Adult content enabled on your FA account")
        }
        throw IOException("no submission image on the page (removed or disabled?)")
    }

    /**
     * SoFurry submission pages (`sofurry.com/s/<id>`) have no og:image; the file is in the page's
     * embedded route data as `"displayUrl","https://cdn.sofurryfiles.com/..."` - a full-resolution
     * WebP, so it can reach "same dimensions", never "exact".
     */
    internal fun parseSoFurry(html: String): List<HttpUrl> {
        val key = "displayUrl"
        val cdn = "https://cdn.sofurryfiles.com/"
        val out = mutableListOf<HttpUrl>()
        var from = 0
        while (true) {
            val pos = html.indexOf(key, from)
            if (pos < 0) break
            val start = pos + key.length
            from = start
            val window = html.substring(start, minOf(html.length, start + 200))
            val at = window.indexOf(cdn).takeIf { it >= 0 } ?: continue
            val rest = window.substring(at)
            val end = rest.indexOfFirst { it == '\\' || it == '"' || it == '\'' || it.isWhitespace() }.let { if (it < 0) rest.length else it }
            rest.substring(0, end).toHttpUrlOrNull()?.let { out += it }
        }
        return dedupe(out).ifEmpty { throw IOException("no submission file on the page (removed, or a writing/audio submission?)") }
    }

    /**
     * Weasyl submission pages: the full original is the `#detail-art` Download link; og:image is
     * only a preview. An adult page without the `ro` cookie has neither, just the age prompt.
     */
    private fun parseWeasyl(html: String, page: HttpUrl): List<HttpUrl> {
        val out = tags(html, "a").mapNotNull { attr(it, "href") }
            .filter { it.startsWith("https://cdn.weasyl.com/~") && it.contains("/submissions/") }
            .mapNotNull { it.toHttpUrlOrNull() }
            .toMutableList()
        findMetaImage(html, page)?.let { out += it }
        val urls = dedupe(out)
        if (urls.isNotEmpty()) return urls
        throw IOException(if (html.contains("rating-override")) "Weasyl kept this submission behind its 18+ prompt" else "no submission file on the page (removed?)")
    }

    /** The submission id in a Weasyl URL (`/~user/submissions/<id>/...`, `/submission/<id>`, `/view/<id>`). */
    private fun weasylSubmissionId(url: HttpUrl): String? {
        val seg = segments(url)
        val i = seg.indexOfFirst { it == "submissions" || it == "submission" || it == "view" }.takeIf { it >= 0 } ?: return null
        return seg.getOrNull(i + 1)?.let(::leadingId)
    }

    private fun isHost(url: HttpUrl, vararg hosts: String) = url.host.lowercase() in hosts
    private fun isFurAffinity(url: HttpUrl) = isHost(url, "www.furaffinity.net", "furaffinity.net")
    private fun isHentaiVox(url: HttpUrl) = isHost(url, "hentaivox.com", "www.hentaivox.com")
    private fun isSoFurry(url: HttpUrl) = isHost(url, "sofurry.com", "www.sofurry.com")
    private fun isWeasyl(url: HttpUrl) = isHost(url, "www.weasyl.com", "weasyl.com")

    /** The image(s) an HTML page shows: the site's own markup where known, else its og:image. [page] is the page's real URL. */
    private fun scrapePage(html: String, page: HttpUrl): List<HttpUrl> = when {
        isFurAffinity(page) -> parseFurAffinity(html, page)
        isHentaiVox(page) -> parseHentaiVox(html, page)
        isSoFurry(page) -> parseSoFurry(html)
        isWeasyl(page) -> parseWeasyl(html, page)
        else -> listOf(findMetaImage(html, page) ?: throw IOException("no og:image on the page"))
    }

    @Volatile private var inkbunnySid: String? = null

    /**
     * A guest session on Inkbunny's API with every rating switched on (a fresh guest session only
     * sees General work, and deleted e621 posts mostly aren't). Cached for the process's lifetime.
     */
    private suspend fun inkbunnySid(fresh: Boolean): String {
        if (!fresh) inkbunnySid?.let { return it }
        val login = getJson("https://inkbunny.net/api_login.php", listOf("username" to "guest"))
        val sid = login["sid"].str() ?: throw IOException("guest login refused")
        runCatching {
            getJson("https://inkbunny.net/api_userrating.php", listOf("sid" to sid, "tag[2]" to "yes", "tag[3]" to "yes", "tag[4]" to "yes", "tag[5]" to "yes"))
        }
        inkbunnySid = sid
        return sid
    }

    /** Inkbunny via its API, which reports every file's md5 - the matching file goes first. */
    private suspend fun resolveInkbunny(url: HttpUrl, expected: Expected): List<HttpUrl> {
        val seg = segments(url)
        val id = (if (seg.firstOrNull() == "s" && seg.size >= 2) leadingId(seg[1]) else url.queryParameter("id")?.let(::leadingId))
            ?: throw IOException("not an Inkbunny submission URL")
        var o: JsonElement = JsonNull
        for (fresh in listOf(false, true)) {
            o = getJson("https://inkbunny.net/api_submissions.php", listOf("sid" to inkbunnySid(fresh), "submission_ids" to id))
            if (o["error_code"] == null) break
        }
        o["error_message"].str()?.let { throw IOException("Inkbunny API: $it") }
        val files = (o["submissions"][0]["files"] as? JsonArray) ?: throw IOException("submission not found (removed?)")
        val want = expected.md5?.trim()?.lowercase().orEmpty()
        val (matching, rest) = files.mapNotNull { f ->
            val u = f["file_url_full"].str()?.toHttpUrlOrNull() ?: return@mapNotNull null
            u to (want.isNotEmpty() && f["full_file_md5"].str().equals(want, ignoreCase = true))
        }.partition { it.second }
        return (matching + rest).map { it.first }.ifEmpty { throw IOException("submission has no files") }
    }

    /** Itaku via its public API: `image` is the original upload, `image_xl` a resize kept as a fallback. */
    private suspend fun resolveItaku(url: HttpUrl): List<HttpUrl> {
        val seg = segments(url)
        val id = (if (seg.firstOrNull() == "images" && seg.size >= 2) leadingId(seg[1]) else null) ?: throw IOException("not an Itaku image URL")
        val o = getJson("https://itaku.ee/api/galleries/images/$id/")
        return listOf("image", "image_xl").mapNotNull { o[it].str()?.toHttpUrlOrNull() }.ifEmpty { throw IOException("image not found (removed?)") }
    }

    /** Weasyl: with a saved API key, its API first; otherwise (or if that fails) the page, with the 18+ cookie. Null = not a submission URL. */
    private suspend fun resolveWeasyl(url: HttpUrl): List<HttpUrl>? {
        val id = weasylSubmissionId(url) ?: return null
        var apiError: String? = null
        SourceLogins.apiKey(SourceLogins.KeySite.WEASYL)?.let { key ->
            try {
                val o = getJson("https://www.weasyl.com/api/submissions/$id/view", headers = mapOf("X-Weasyl-API-Key" to key.apiKey))
                val out = o["media"]["submission"].arr().mapNotNull { it["url"].str()?.toHttpUrlOrNull() }
                if (out.isNotEmpty()) return out
                apiError = "Weasyl API: submission has no file"
            } catch (e: IOException) {
                apiError = "Weasyl API: ${e.message}"
            }
        }
        return try {
            parseWeasyl(getPage(url), url)
        } catch (e: IOException) {
            throw IOException(if (apiError != null) "$apiError; page: ${e.message}" else e.message)
        }
    }

    /** Hosts with a special lookup. Null = no special handling for this host. */
    private suspend fun resolveSite(url: HttpUrl, expected: Expected): List<HttpUrl>? =
        when (url.host.lowercase().removePrefix("www.").removePrefix("mobile.")) {
            "twitter.com", "x.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com" -> resolveTwitter(url, expected)
            "bsky.app" -> resolveBluesky(url)
            "pixiv.net" -> resolvePixiv(url)
            "deviantart.com", "fav.me" -> resolveDeviantArt(url)
            "hentaivox.com" -> parseHentaiVox(getPage(url), url)
            "furaffinity.net" -> parseFurAffinity(getPage(url), url)
            "inkbunny.net" -> resolveInkbunny(url, expected)
            "itaku.ee" -> resolveItaku(url)
            "weasyl.com" -> resolveWeasyl(url)
            "sofurry.com" -> parseSoFurry(getPage(url))
            else -> null
        }

    // --- md5 mirrors ------------------------------------------------------------------------

    /** A copy found by md5 on another booru: its post page (shown as the "source") and file. */
    private class MirrorHit(val page: String, val file: HttpUrl)

    private fun booruHits(posts: List<JsonElement>, md5: String, md5Field: String, pageFor: (String) -> String): List<MirrorHit> =
        posts.mapNotNull { p ->
            val reported = p[md5Field].str() ?: md5
            if (!reported.equals(md5, ignoreCase = true)) return@mapNotNull null
            val id = (p["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val file = p["file_url"].str()?.toHttpUrlOrNull() ?: return@mapNotNull null
            MirrorHit(pageFor(id), file)
        }

    /** rule34.paheal's Danbooru-compatible XML; a miss is `<posts count='1'>` with no `<post>` in it. */
    private fun pahealHits(xml: String, md5: String): List<MirrorHit> =
        tags(xml, "post ").mapNotNull { t ->
            if (attr(t, "md5")?.equals(md5, ignoreCase = true) != true) return@mapNotNull null
            val id = attr(t, "id") ?: return@mapNotNull null
            val file = attr(t, "file_url")?.toHttpUrlOrNull() ?: return@mapNotNull null
            MirrorHit("https://rule34.paheal.net/post/view/$id", file)
        }

    /** Gelbooru wraps posts as `{"post": [...]}`, Rule34.xxx returns the bare array and an empty body for a miss; a refused key is a JSON string. */
    private fun parseDapi(body: String, md5: String, md5Field: String, page: String): List<MirrorHit> {
        if (body.isBlank()) return emptyList()
        val o = try { json.parseToJsonElement(body) } catch (_: Exception) { throw IOException("unexpected answer") }
        o.str()?.let { throw IOException("$it - check the key under Settings > Account > Source logins") }
        val posts = (o["post"] ?: o).arr()
        return booruHits(posts, md5, md5Field) { "$page$it" }
    }

    /** Boorus searchable by md5 - a hit is byte-identical by definition. Gelbooru/Rule34.xxx only with a saved key. */
    private enum class Mirror(val label: String) {
        DANBOORU("Danbooru"), PAHEAL("rule34.paheal"), GELBOORU("Gelbooru"), RULE34("Rule34.xxx");

        companion object {
            fun available() = buildList {
                add(DANBOORU)
                add(PAHEAL)
                if (SourceLogins.apiKey(SourceLogins.KeySite.GELBOORU) != null) add(GELBOORU)
                if (SourceLogins.apiKey(SourceLogins.KeySite.RULE34) != null) add(RULE34)
            }
        }
    }

    private suspend fun lookupMirror(mirror: Mirror, md5: String): List<MirrorHit> {
        val tag = "md5:$md5"
        return when (mirror) {
            Mirror.DANBOORU -> booruHits(getJson("https://danbooru.donmai.us/posts.json", listOf("tags" to tag, "limit" to "5")).arr(), md5, "md5") {
                "https://danbooru.donmai.us/posts/$it"
            }
            Mirror.PAHEAL -> pahealHits(getText("https://rule34.paheal.net/api/danbooru/find_posts", listOf("md5" to md5)), md5)
            Mirror.GELBOORU, Mirror.RULE34 -> {
                val key = SourceLogins.apiKey(if (mirror == Mirror.GELBOORU) SourceLogins.KeySite.GELBOORU else SourceLogins.KeySite.RULE34)
                    ?: throw IOException("no API key saved")
                val (api, page, field) = if (mirror == Mirror.GELBOORU) {
                    Triple("https://gelbooru.com/index.php", "https://gelbooru.com/index.php?page=post&s=view&id=", "md5")
                } else {
                    Triple("https://api.rule34.xxx/index.php", "https://rule34.xxx/index.php?page=post&s=view&id=", "hash")
                }
                val body = getText(api, listOf("page" to "dapi", "s" to "post", "q" to "index", "json" to "1", "tags" to tag, "user_id" to key.userId, "api_key" to key.apiKey))
                parseDapi(body, md5, field, page)
            }
        }
    }

    /** Whether a saved source API key works, for Settings. */
    enum class KeyState { VALID, REJECTED, UNVERIFIED, MISSING }
    class KeyCheck(val state: KeyState, val message: String? = null)

    /** Tries a saved key with one cheap request (Gelbooru 401s a bad key; Rule34.xxx answers 200 with a JSON string; Weasyl's whoami 401s). */
    suspend fun checkKey(site: SourceLogins.KeySite): KeyCheck = withContext(Dispatchers.IO) {
        val key = SourceLogins.apiKey(site) ?: return@withContext KeyCheck(KeyState.MISSING)
        val result: Result<JsonElement> = runCatching {
            when (site) {
                SourceLogins.KeySite.WEASYL -> getJson("https://www.weasyl.com/api/whoami", headers = mapOf("X-Weasyl-API-Key" to key.apiKey))
                else -> {
                    val api = if (site == SourceLogins.KeySite.GELBOORU) "https://gelbooru.com/index.php" else "https://api.rule34.xxx/index.php"
                    val body = getText(api, listOf("page" to "dapi", "s" to "post", "q" to "index", "json" to "1", "limit" to "1", "user_id" to key.userId, "api_key" to key.apiKey))
                    if (body.isBlank()) JsonArray(emptyList()) else try { json.parseToJsonElement(body) } catch (_: Exception) { throw IOException("unexpected answer") }
                }
            }
        }
        val e = result.exceptionOrNull()
        val o = result.getOrNull()
        when {
            e != null && (e.message.orEmpty().startsWith("HTTP 401") || e.message.orEmpty().startsWith("HTTP 403")) -> KeyCheck(KeyState.REJECTED, "The site refused this key")
            e != null -> KeyCheck(KeyState.UNVERIFIED, e.message)
            o.str() != null -> KeyCheck(KeyState.REJECTED, o.str())
            site == SourceLogins.KeySite.WEASYL -> o["login"].str()?.let { KeyCheck(KeyState.VALID, it) } ?: KeyCheck(KeyState.UNVERIFIED, "unexpected answer")
            o is JsonArray || o is JsonObject -> KeyCheck(KeyState.VALID)
            else -> KeyCheck(KeyState.UNVERIFIED, "unexpected answer")
        }
    }

    // --- URL upgrades -----------------------------------------------------------------------

    /** What a source URL should become before it's fetched: often a thumbnail one tweak away from its original. */
    private sealed interface Upgrade {
        /** Fetch this page instead (FA thumbnail -> its `/view/<id>/` page). */
        data class Page(val url: HttpUrl) : Upgrade
        /** Direct media: try these, best first; the URL itself is always last. */
        data class Media(val urls: List<HttpUrl>) : Upgrade
        /** A Bluesky CDN image, expanded by [bskyOriginals]. */
        data class Bluesky(val url: HttpUrl) : Upgrade
    }

    private fun isTumblrSize(seg: String): Boolean {
        if (!seg.startsWith("s")) return false
        val (w, h) = seg.substring(1).split('x', limit = 2).takeIf { it.size == 2 } ?: return false
        return w.isNotEmpty() && h.isNotEmpty() && w.all { it.isDigit() } && h.all { it.isDigit() }
    }

    /** Fur Affinity's file host moved from d.facdn.net to d.furaffinity.net (same paths); the other spelling. */
    private fun faCdnTwin(url: HttpUrl): HttpUrl? = when (url.host) {
        "d.facdn.net" -> url.newBuilder().host("d.furaffinity.net").build()
        "d.furaffinity.net" -> url.newBuilder().host("d.facdn.net").build()
        else -> null
    }

    private fun upgradeSource(url: HttpUrl, expected: Expected): Upgrade? {
        val host = url.host.lowercase()
        val want = normalizeExt(expected.ext)
        val seg = segments(url)
        fun finish(list: List<HttpUrl>) = Upgrade.Media(dedupe(list + url))

        if (host == "d.facdn.net") return finish(listOfNotNull(faCdnTwin(url)))

        if (host == "t.furaffinity.net") {
            val id = seg.firstOrNull()?.substringBefore('@')?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } } ?: return null
            return "https://www.furaffinity.net/view/$id/".toHttpUrlOrNull()?.let { Upgrade.Page(it) }
        }

        if (host == "cdn.bsky.app" && parseBskyCdn(url) != null) return Upgrade.Bluesky(url)

        if (host == "pbs.twimg.com" && seg.firstOrNull() == "media" && seg.size == 2) {
            val file = seg[1].substringBefore(':')
            val stem = file.substringBeforeLast('.')
            val pathExt = if (file.contains('.')) file.substringAfterLast('.') else ""
            val have = normalizeExt(url.queryParameter("format") ?: pathExt)
            val list = listOf(want, have).filter { it.isNotEmpty() && it != "webm" && it != "mp4" }
                .mapNotNull { "https://pbs.twimg.com/media/$stem?format=$it&name=orig".toHttpUrlOrNull() }
            return finish(list)
        }

        if (host == "i.pximg.net") {
            val path = url.encodedPath
            val rest = listOf("/img-master/img/", "/custom-thumb/img/").firstNotNullOfOrNull { m ->
                path.indexOf(m).takeIf { it >= 0 }?.let { path.substring(it + m.length) }
            } ?: return null
            val dir = rest.substringBeforeLast('/', "").takeIf { it.isNotEmpty() } ?: return null
            val parts = rest.substringAfterLast('/').split('_')
            if (parts.size < 2 || !parts[1].startsWith("p")) return null
            val list = listOf(want, "png", "jpg", "gif").filter { it.isNotEmpty() && it != "webm" && it != "mp4" }.distinct()
                .mapNotNull { "https://i.pximg.net/img-original/img/$dir/${parts[0]}_${parts[1]}.$it".toHttpUrlOrNull() }
            return finish(list)
        }

        if (host.endsWith(".media.tumblr.com")) {
            // Always Media, even with nothing to upgrade, so the URL is fetched with ACCEPT_MEDIA.
            val file = seg.lastOrNull() ?: return null
            val stem = file.substringBeforeLast('.')
            val ext = if (file.contains('.')) file.substringAfterLast('.') else ""
            // `.pnj` is Tumblr's JPEG re-encode of a PNG (the `.png` URL is the real one); `.gifv` a GIF's video form.
            val realExt = when (ext) { "pnj" -> "png"; "gifv" -> "gif"; else -> ext }
            fun with(size: String?, name: String): HttpUrl? {
                val path = seg.dropLast(1).map { if (isTumblrSize(it) && size != null) size else it } + name
                return url.newBuilder().encodedPath("/" + path.joinToString("/")).query(null).build()
            }
            val real = if (realExt.isEmpty()) file else "$stem.$realExt"
            val legacySize = stem.substringAfterLast('_', "")
            val list = when {
                seg.any(::isTumblrSize) -> listOfNotNull(with("s2048x3072", real), with(null, real))
                file.startsWith("tumblr_") && stem.contains('_') && legacySize.isNotEmpty() && legacySize.all { it.isDigit() } ->
                    listOfNotNull(with(null, "${stem.substringBeforeLast('_')}_1280.$realExt"), with(null, real))
                else -> listOfNotNull(with(null, real))
            }
            return finish(list)
        }

        // Blog-subdomain post pages answer 403 to non-browser clients; www.tumblr.com/<blog>/<id> is public.
        if (host.endsWith(".tumblr.com")) {
            val blog = host.removeSuffix(".tumblr.com")
            if (!blog.contains('.') && blog != "www" && blog != "assets" && seg.firstOrNull() in setOf("post", "image")) {
                val id = seg.getOrNull(1)?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }
                if (id != null) return "https://www.tumblr.com/$blog/$id".toHttpUrlOrNull()?.let { Upgrade.Page(it) }
            }
        }
        return null
    }

    private fun upgradeAll(urls: List<HttpUrl>, expected: Expected): List<HttpUrl> =
        dedupe(urls.flatMap { u -> (upgradeSource(u, expected) as? Upgrade.Media)?.urls ?: listOf(u) })

    // --- Finding ----------------------------------------------------------------------------

    private sealed interface Found {
        class File(val bytes: ByteArray, val url: HttpUrl) : Found
        class Urls(val urls: List<HttpUrl>) : Found
    }

    /**
     * The URL itself is a file, or an HTML page whose image [scrapePage] can find. [page] is the URL
     * the content belongs to: [fetch] itself for a live source, the original URL when [fetch] is a
     * Wayback capture (then [archiveTs] re-points the page's images at the archive).
     */
    private suspend fun findGeneric(fetch: HttpUrl, page: HttpUrl, archiveTs: String?): Found {
        val result = send(fetch, MEDIA_TIMEOUT_S, mapOf("Referer" to refererFor(fetch), "Accept" to ACCEPT_ANY)) { r ->
            val ct = r.contentType()
            when {
                ct == "text/html" || ct == "application/xhtml+xml" -> r.readCapped(MAX_HTML_BYTES).decodeToString()
                ct.startsWith("text/") || ct.contains("json") -> throw IOException("unsupported content ($ct)")
                else -> {
                    val bytes = r.readCapped(MAX_MEDIA_BYTES)
                    if (sniffMime(bytes) == null) throw IOException("not a usable image or video (${ct.ifEmpty { "unknown type" }})")
                    bytes
                }
            }
        }
        return when (result) {
            is String -> {
                val images = scrapePage(result, page)
                Found.Urls(if (archiveTs == null) images else images.mapNotNull { waybackRaw(archiveTs, it.toString()) })
            }
            else -> Found.File(result as ByteArray, fetch)
        }
    }

    private suspend fun findImages(source: String, expected: Expected): Found {
        var url = parse(source)
        if (url.scheme != "http" && url.scheme != "https") throw IOException("unsupported scheme")
        when (val up = upgradeSource(url, expected)) {
            is Upgrade.Page -> url = up.url
            is Upgrade.Media -> return Found.Urls(up.urls)
            is Upgrade.Bluesky -> return Found.Urls(bskyOriginals(listOf(up.url)))
            null -> {}
        }
        var siteError: String? = null
        try {
            resolveSite(url, expected)?.let { return Found.Urls(upgradeAll(it, expected)) }
        } catch (e: IOException) {
            val msg = e.message.orEmpty()
            // These parse the page themselves; the generic scrape would only fetch it again.
            if (msg.startsWith(LOGIN_REQUIRED) || isFurAffinity(url) || isHentaiVox(url) || isSoFurry(url) || isWeasyl(url)) throw e
            siteError = msg
        }
        return try {
            when (val found = findGeneric(url, url, null)) {
                is Found.Urls -> Found.Urls(upgradeAll(found.urls, expected))
                else -> found
            }
        } catch (e: IOException) {
            throw IOException(if (siteError != null) "$siteError; page scrape: ${e.message}" else e.message)
        }
    }

    // --- Wayback Machine --------------------------------------------------------------------

    /** A raw capture URL (`<ts>id_` = the original bytes, no toolbar or rewriting). */
    private fun waybackRaw(ts: String, url: String): HttpUrl? {
        val original = if (url.contains("id_/")) url.substringAfterLast("id_/") else url
        return "https://web.archive.org/web/${ts}id_/$original".toHttpUrlOrNull()
    }

    /** Up to [k] items spread evenly from the first to the last (oldest to newest capture). */
    internal fun <T> pickSpread(items: List<T>, k: Int): List<T> {
        val n = items.size
        if (n <= k || k < 2) return items.take(k.coerceAtLeast(1).coerceAtMost(n))
        return (0 until k).map { i -> (i * (n - 1) + (k - 1) / 2) / (k - 1) }.distinct().map { items[it] }
    }

    /** Capture timestamps worth trying: the CDX index's distinct-content 200 captures spread over time, else the closest one. */
    private suspend fun waybackCaptures(url: String): List<String> {
        try {
            val cdx = getJson(
                "https://web.archive.org/cdx/search/cdx",
                listOf("url" to url, "output" to "json", "fl" to "timestamp", "filter" to "statuscode:200", "collapse" to "digest", "limit" to "200"),
            )
            val all = cdx.arr().mapNotNull { it[0].str() }.filter { ts -> ts.isNotEmpty() && ts.all { it.isDigit() } }
            if (all.isNotEmpty()) return pickSpread(all, WAYBACK_CAPTURES_PER_TARGET)
        } catch (_: IOException) {
        }
        val closest = getJson("https://archive.org/wayback/available", listOf("url" to url))["archived_snapshots"]["closest"]
        val ts = closest["timestamp"].str()
        if ((closest["available"] as? JsonPrimitive)?.booleanOrNull == true && ts != null) return listOf(ts)
        throw IOException("no Wayback snapshot")
    }

    // --- The search -------------------------------------------------------------------------

    /** Why a source can't hold the post's image: a site's front page, or an artist profile page. */
    internal fun notAPost(source: String): String? {
        val url = source.toHttpUrlOrNull() ?: return null
        if ((url.encodedPath == "/" || url.encodedPath.isEmpty()) && url.query == null) return "only the site's front page"
        val host = url.host.lowercase().removePrefix("www.").removePrefix("mobile.")
        val seg = segments(url)
        val first = seg.firstOrNull().orEmpty()
        val profile = when (host) {
            "furaffinity.net" -> first in setOf("user", "gallery", "scraps", "favorites", "journals", "commissions")
            "twitter.com", "x.com" -> "status" !in seg && first != "i"
            "bsky.app" -> first == "profile" && "post" !in seg
            "pixiv.net" -> "users" in seg
            "deviantart.com" -> seg.size == 1 || seg.getOrNull(1) in setOf("gallery", "about")
            else -> false
        }
        return if (profile) "an artist's profile page, not a link to the post" else null
    }

    private fun looksLikeFile(source: String): Boolean {
        val path = source.toHttpUrlOrNull()?.encodedPath?.lowercase() ?: return false
        return listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".webm", ".mp4", ".pnj").any { path.endsWith(it) }
    }

    /** Shared state of one run; its tasks run in parallel. */
    private class Session(
        val site: Site,
        val postId: Long,
        val expected: Expected,
        val onProgress: (Progress) -> Unit,
    ) {
        val found = mutableListOf<Pair<Int, RecoveredImage>>()
        val seenMd5 = HashSet<String>()
        val failures = mutableListOf<Attempt>()
        val exact = AtomicBoolean(false)

        fun fail(stage: Attempt.Stage, label: String, url: String?, reason: String) =
            synchronized(failures) { failures += Attempt(stage, label, url, reason) }

        fun done() = exact.get()
        fun goodEnough() = done() || synchronized(found) { found.any { it.second.grade >= Grade.SAME_DIMENSIONS } }
    }

    /** Grades one downloaded file and, if it's usable and new, stores it in the post's cache folder. */
    private fun Session.consider(order: Int, source: String, bytes: ByteArray, url: HttpUrl, viaArchive: Boolean) {
        onProgress(Progress.Comparing(hostOf(url)))
        val c = scoreCandidate(expected, source, bytes, url, viaArchive)
        onProgress(Progress.Scored(hostOf(url), c?.width ?: 0, c?.height ?: 0, c?.let { extForMime(it.mime) }.orEmpty(), c?.grade))
        if (c == null) return
        if (!synchronized(seenMd5) { seenMd5.add(c.md5) }) return
        val file = try {
            RecoveredStore.storeFile(site, postId, c.md5, extForMime(c.mime), bytes)
        } catch (e: IOException) {
            fail(Attempt.Stage.SOURCE, hostOf(url), url.toString(), "couldn't save the file (${e.message})")
            return
        }
        if (c.grade == Grade.EXACT) exact.set(true)
        synchronized(found) { found += order to c.copy(file = file) }
    }

    private suspend fun Session.evaluate(order: Int, source: String, found: Found, viaArchive: Boolean) {
        when (found) {
            is Found.File -> consider(order, source, found.bytes, found.url, viaArchive)
            is Found.Urls -> {
                val urls = found.urls.take(MAX_CANDIDATES_PER_SOURCE)
                for ((i, url) in urls.withIndex()) {
                    if (done()) break
                    onProgress(Progress.Downloading(hostOf(url), i + 1, urls.size))
                    try {
                        consider(order, source, fetchMedia(url), url, viaArchive)
                    } catch (_: IOException) {
                        // One failed candidate doesn't sink the source.
                    }
                }
            }
        }
    }

    private suspend fun Session.trySource(order: Int, total: Int, source: String) {
        val host = hostOf(source)
        onProgress(Progress.Fetching(host, order + 1, total))
        try {
            evaluate(order, source, findImages(source, expected), false)
        } catch (e: IOException) {
            val msg = e.message ?: e.javaClass.simpleName
            onProgress(Progress.Failed(host, msg.lineSequence().first()))
            fail(Attempt.Stage.SOURCE, host, source, msg)
        }
    }

    private suspend fun Session.tryMirror(order: Int, mirror: Mirror, md5: String) {
        onProgress(Progress.Mirror(mirror.label))
        try {
            val hits = lookupMirror(mirror, md5)
            if (hits.isEmpty()) fail(Attempt.Stage.MIRROR, mirror.label, null, "no copy")
            for (hit in hits) {
                if (done()) break
                evaluate(order, hit.page, Found.Urls(listOf(hit.file)), false)
            }
        } catch (e: IOException) {
            val msg = e.message ?: e.javaClass.simpleName
            onProgress(Progress.Failed(mirror.label, msg.lineSequence().first()))
            fail(Attempt.Stage.MIRROR, mirror.label, null, msg)
        }
    }

    private suspend fun Session.tryArchive(order: Int, target: String) {
        val page = target.toHttpUrlOrNull() ?: return
        val host = hostOf(page)
        if (waybackPaused()) {
            fail(Attempt.Stage.ARCHIVE, host, target, "skipped - the Wayback Machine asked us to slow down; it's left alone for a few minutes")
            return
        }
        onProgress(Progress.Archive(host))
        val captures = try {
            waybackCaptures(target)
        } catch (e: IOException) {
            fail(Attempt.Stage.ARCHIVE, host, target, e.message ?: "lookup failed")
            return
        }
        val errors = mutableListOf<String>()
        var tried = 0
        for (ts in captures) {
            if (goodEnough()) return
            val raw = waybackRaw(ts, target) ?: continue
            onProgress(Progress.ArchiveCapture(ts.take(8), host))
            tried++
            try {
                evaluate(order, target, findGeneric(raw, page, ts), true)
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                if (msg !in errors) errors += msg
            }
        }
        if (errors.isNotEmpty()) {
            val n = if (tried == 1) "1 capture" else "$tried captures"
            fail(Attempt.Stage.ARCHIVE, host, target, "$n, none usable: ${errors.joinToString("; ")}")
        }
    }

    /**
     * Searches for a copy of the deleted [post] and returns its cache entry: every usable file
     * found (merged with anything recovered before), best match first. Throws [RecoveryFailed]
     * when nothing turned up (and records the miss). Cancelling the caller cancels the search.
     */
    suspend fun recover(site: Site, post: Post, onProgress: (Progress) -> Unit): RecoveredSet = withContext(Dispatchers.IO) {
        val all = webSources(post)
        val (homepages, sources) = all.partition { notAPost(it) != null }
        val md5 = validMd5(post.file.md5)
        val f = post.file
        val session = Session(site, post.id, Expected(f.md5, f.size, f.width, f.height, f.ext), onProgress)

        for (p in homepages) session.fail(Attempt.Stage.SKIPPED, hostOf(p), p, notAPost(p) ?: "not a link to the post")

        coroutineScope {
            // Live sources: hosts in parallel, each host's sources in order; md5 lookups alongside.
            val permits = Semaphore(PARALLEL_HOSTS)
            val groups = sources.withIndex().groupBy { hostOf(it.value) }.values
            val jobs = mutableListOf<Job>()
            for (group in groups) {
                jobs += launch {
                    permits.withPermit {
                        for ((i, s) in group) {
                            if (session.done()) break
                            session.trySource(i, sources.size, s)
                        }
                    }
                }
            }
            if (md5 != null) {
                Mirror.available().forEachIndexed { k, mirror ->
                    jobs += launch { session.tryMirror(sources.size + 100 + k, mirror, md5) }
                }
            }
            val finished = withTimeoutOrNull(LIVE_BUDGET_MS) {
                while (jobs.any { it.isActive } && !session.done()) delay(100)
                true
            }
            jobs.forEach { it.cancel() }
            jobs.joinAll()
            if (finished == null) {
                onProgress(Progress.TimeLimit)
                session.fail(Attempt.Stage.LIMIT, "Live sources", null, "stopped after ${LIVE_BUDGET_MS / 1000}s")
            }
        }

        if (!session.goodEnough()) {
            // Direct file URLs first - an archived copy of the file itself is the best find.
            val targets = mutableListOf<String>()
            for (t in sources.filter(::looksLikeFile) + sources.filterNot(::looksLikeFile)) {
                targets += t
                t.toHttpUrlOrNull()?.let(::faCdnTwin)?.let { targets += it.toString() }
            }
            if (md5 != null) {
                val ext = normalizeExt(f.ext).ifEmpty { "jpg" }
                targets += "https://static1.${site.apiHost}/data/${md5.substring(0, 2)}/${md5.substring(2, 4)}/$md5.$ext"
            }
            val finished = withTimeoutOrNull(ARCHIVE_BUDGET_MS) {
                for ((k, target) in targets.withIndex()) {
                    if (session.goodEnough()) break
                    session.tryArchive(sources.size + k, target)
                }
                true
            }
            if (finished == null) session.fail(Attempt.Stage.LIMIT, "Wayback Machine", null, "stopped after ${ARCHIVE_BUDGET_MS / 1000}s")
        }

        val found = synchronized(session.found) { session.found.toList() }
            .sortedWith(compareByDescending<Pair<Int, RecoveredImage>> { it.second.score }.thenBy { it.first })
            .map { it.second }
        val failures = synchronized(session.failures) { session.failures.toList() }
        if (found.isEmpty()) {
            RecoveredStore.markNotFound(site, post.id, failures)
            throw RecoveryFailed(noSources = sources.isEmpty() && homepages.isEmpty(), attempts = failures)
        }
        RecoveredStore.save(site, post.id, found, failures)
    }
}
