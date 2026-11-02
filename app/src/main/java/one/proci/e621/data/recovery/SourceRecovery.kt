package one.proci.e621.data.recovery

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import one.proci.e621.BuildConfig
import one.proci.e621.data.model.Post
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Deleted-post recovery, ported from the desktop app's `source_fetch.rs`. e621 keeps a deleted
 * post's sources plus its file's md5, byte size, dimensions and format (only the URLs are null),
 * so this fetches the image from the post's own source URLs and grades every candidate against
 * what e621 kept. Strictly opt-in: nothing here runs until the user taps "Try to load from source".
 *
 * Per source: a site-specific resolver if the host has one (X, Bluesky, Pixiv, DeviantArt,
 * HentaiVox, Fur Affinity - unofficial public endpoints that may break), else the URL itself if
 * it serves an image, else the page's `og:image`/`twitter:image`. When no live source reaches
 * [Grade.SAME_DIMENSIONS], the Wayback Machine's raw captures of the sources (and of e621's own
 * CDN path for the md5) are tried too.
 */
object SourceRecovery {

    enum class Grade { MISMATCH, RESCALED, SAME_DIMENSIONS, EXACT }

    class RecoveredImage(
        val bytes: ByteArray,
        val sourceUrl: String,
        val imageUrl: String,
        val mime: String,
        val viaArchive: Boolean,
        val grade: Grade,
        val width: Int,
        val height: Int,
        val expectedWidth: Int,
        val expectedHeight: Int,
        /** Ways this differs from the original; rendered to text by the UI. */
        val notes: List<Note>,
        internal val score: Long,
    )

    sealed interface Note {
        data class Format(val got: String, val want: String) : Note
        data class Dimensions(val width: Int, val height: Int, val wantWidth: Int, val wantHeight: Int) : Note
        data object SmallerFile : Note
        data object LargerFile : Note
        data object ShapeDiffers : Note
    }

    /** Status updates for the viewer's loading line. */
    sealed interface Progress {
        data class Fetching(val host: String, val index: Int, val total: Int) : Progress
        data class Downloading(val host: String, val index: Int, val total: Int) : Progress
        data class Comparing(val host: String) : Progress
        data class Failed(val host: String, val reason: String) : Progress
        data object Archive : Progress
    }

    class RecoveryFailed(val noSources: Boolean, val details: List<String>) : Exception()

    private const val MAX_IMAGE_BYTES = 40L * 1024 * 1024
    private const val MAX_HTML_BYTES = 2L * 1024 * 1024
    private const val MAX_CANDIDATES_PER_SOURCE = 8
    /** Candidates kept for the picker; each holds its full file in memory. */
    private const val MAX_KEPT = 8
    private const val LOGIN_REQUIRED = "login required"

    private val json = Json { ignoreUnknownKeys = true }

    /** Last results per post, so swiping away and back doesn't re-fetch. Small: these hold image bytes. */
    private val cache = object : LinkedHashMap<Long, List<RecoveredImage>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<RecoveredImage>>?) = size > 3
    }

    fun cached(postId: Long): List<RecoveredImage>? = synchronized(cache) { cache[postId] }

    fun webSources(post: Post): List<String> =
        post.sources.map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }

    // --- SSRF guard -------------------------------------------------------------------------
    // Source URLs are user-edited post data, so they must never reach loopback, LAN, link-local or
    // other non-public addresses (a phone's LAN has routers and such). Enforced on literal-IP hosts
    // and every redirect hop (network interceptor) and on every DNS answer (PublicOnlyDns).

    internal fun isPublic(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
            addr.isAnyLocalAddress || addr.isMulticastAddress
        ) return false
        return when (addr) {
            is Inet4Address -> {
                val o = addr.address.map { it.toInt() and 0xff }
                !(o[0] == 0 || o[0] >= 240 ||
                    (o[0] == 100 && (o[1] and 0xc0) == 64) || // 100.64/10 carrier-grade NAT
                    (o[0] == 198 && (o[1] and 0xfe) == 18) || // 198.18/15 benchmarking
                    (o[0] == 192 && o[1] == 0 && o[2] == 2) || // documentation ranges
                    (o[0] == 198 && o[1] == 51 && o[2] == 100) ||
                    (o[0] == 203 && o[1] == 0 && o[2] == 113))
            }
            is Inet6Address -> {
                val b = addr.address
                val first = ((b[0].toInt() and 0xff) shl 8) or (b[1].toInt() and 0xff)
                val mapped = b.take(10).all { it.toInt() == 0 } && b[10].toInt() == -1 && b[11].toInt() == -1
                if (mapped) return isPublic(InetAddress.getByAddress(b.copyOfRange(12, 16)))
                (first and 0xfe00) != 0xfc00 // fc00::/7 unique local
            }
            else -> false
        }
    }

    private fun isIpLiteral(host: String) =
        host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    internal fun checkUrl(url: HttpUrl) {
        val host = url.host.lowercase().trimEnd('.')
        val blocked = if (isIpLiteral(host)) {
            // Literal addresses never touch Dns, so they're checked here. getByName doesn't resolve them.
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

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .dns(PublicOnlyDns)
            // Network interceptors run once per hop, so every redirect target is checked too.
            // A saved site session (SourceLogins) is attached here, per hop, so it only ever goes
            // to that site's own hosts - a redirect elsewhere gets no cookie.
            .addNetworkInterceptor(Interceptor { chain ->
                val request = chain.request()
                checkUrl(request.url)
                val cookie = if (request.header("Cookie") == null) SourceLogins.cookieFor(request.url.host) else null
                chain.proceed(if (cookie != null) request.newBuilder().header("Cookie", cookie).build() else request)
            })
            .build()
    }

    /** Fetches a page with an explicit cookie (sign-in verification); null on any failure. */
    internal fun fetchPageWithCookie(url: String, cookie: String): String? = try {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Cookie", cookie)
            .build()
        client.newCall(request).execute().takeIf { it.isSuccessful }?.readCapped(MAX_HTML_BYTES)?.decodeToString()
    } catch (_: Exception) {
        null
    }

    private val userAgent = "MonosodiumPDC-Android/${BuildConfig.VERSION_NAME} (source recovery for deleted e621 posts)"

    // --- HTTP helpers -----------------------------------------------------------------------

    /** Some hosts reject hotlinks by Referer; send the host's own origin (Pixiv's CDN wants pixiv.net). */
    private fun refererFor(url: HttpUrl): String =
        if (url.host.endsWith("pximg.net")) "https://www.pixiv.net/" else "${url.scheme}://${url.host}/"

    private fun get(url: HttpUrl, referer: String? = refererFor(url), accept: String = "image/*,text/html;q=0.8,*/*;q=0.5"): Response {
        if (url.scheme != "http" && url.scheme != "https") throw IOException("unsupported scheme")
        checkUrl(url)
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response
    }

    private fun Response.contentType(): String =
        header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()

    private fun Response.readCapped(limit: Long): ByteArray = use { response ->
        val body = response.body
        val length = body.contentLength()
        if (length > limit) throw IOException("response too large")
        val source = body.source()
        val out = okio.Buffer()
        while (true) {
            val read = source.read(out, 64 * 1024)
            if (read == -1L) break
            if (out.size > limit) throw IOException("response too large")
        }
        out.readByteArray()
    }

    private fun getJson(url: HttpUrl, referer: String? = null): JsonElement {
        val bytes = get(url, referer = referer, accept = "application/json").readCapped(MAX_HTML_BYTES)
        return json.parseToJsonElement(bytes.decodeToString())
    }

    private fun parse(s: String): HttpUrl = s.trim().toHttpUrlOrNull() ?: throw IOException("invalid URL")

    private fun hostOf(url: HttpUrl) = url.host.removePrefix("www.")
    private fun hostOf(s: String) = s.toHttpUrlOrNull()?.let(::hostOf) ?: s

    // --- HTML scraping ----------------------------------------------------------------------

    private fun decodeEntities(s: String) =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")

    /** Value of `name="..."` (or single-quoted/unquoted) inside one tag's text, case-insensitive. */
    internal fun attr(tag: String, name: String): String? {
        val re = Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val m = re.find(tag) ?: return null
        return decodeEntities(m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: "")
    }

    private fun tags(html: String, tagName: String): Sequence<String> =
        Regex("""<$tagName\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { it.value }

    /** First `og:image` (preferred) or `twitter:image` declared in the page, resolved against it. */
    internal fun findMetaImage(html: String, base: HttpUrl): HttpUrl? {
        var og: String? = null
        var twitter: String? = null
        for (tag in tags(html, "meta")) {
            val key = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
            val content = attr(tag, "content")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            when (key) {
                "og:image", "og:image:url", "og:image:secure_url" -> if (og == null) og = content
                "twitter:image", "twitter:image:src" -> if (twitter == null) twitter = content
            }
            if (og != null) break
        }
        return base.resolve(og ?: twitter ?: return null)
    }

    private fun fetchHtml(url: HttpUrl): String = get(url).readCapped(MAX_HTML_BYTES).decodeToString()

    // --- Site resolvers ---------------------------------------------------------------------

    private fun segments(url: HttpUrl) = url.pathSegments.filter { it.isNotEmpty() }

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

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

    private fun resolveTwitter(url: HttpUrl, wantExt: String): List<HttpUrl> {
        val seg = segments(url)
        val at = seg.indexOf("status").takeIf { it >= 0 } ?: throw IOException("not a tweet URL")
        val id = seg.getOrNull(at + 1)?.takeIf { s -> s.all { it.isDigit() } } ?: throw IOException("no tweet id")
        val photoIndex = if (seg.getOrNull(at + 2) == "photo") ((seg.getOrNull(at + 3)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0) else 0

        val api = "https://cdn.syndication.twimg.com/tweet-result".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("id", id).addQueryParameter("token", twitterToken(id)).build()
        val tweet = try { getJson(api) } catch (e: IOException) { throw IOException("tweet lookup failed (${e.message})") }
        val media = (tweet["mediaDetails"] as? JsonArray)?.mapNotNull { it["media_url_https"].str() }?.takeIf { it.isNotEmpty() }
            ?: (tweet["photos"] as? JsonArray)?.mapNotNull { it["url"].str() }.orEmpty()
        if (media.isEmpty()) throw IOException("tweet has no media (or it was removed)")
        // The photo the URL points at first, then the rest; `name=orig` asks for the original
        // resolution, and when the format differs from the post's, also ask for that format.
        val first = photoIndex.coerceAtMost(media.lastIndex)
        val ordered = listOf(media[first]) + media.filterIndexed { i, _ -> i != first }
        return ordered.flatMap { m ->
            val stem = m.substringBeforeLast('.')
            val ext = normalizeExt(m.substringAfterLast('.', ""))
            listOfNotNull(
                "$m?name=orig".toHttpUrlOrNull(),
                if (wantExt.isNotEmpty() && ext != wantExt) "$stem?format=$wantExt&name=orig".toHttpUrlOrNull() else null,
            )
        }
    }

    private fun collectFullsize(v: JsonElement?, out: MutableList<String>) {
        when (v) {
            is JsonObject -> v.forEach { (k, child) ->
                val s = (child as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (k == "fullsize" && s != null) out += s else collectFullsize(child, out)
            }
            is JsonArray -> v.forEach { collectFullsize(it, out) }
            else -> {}
        }
    }

    private fun resolveBluesky(url: HttpUrl): List<HttpUrl> {
        val seg = segments(url)
        if (seg.size < 4 || seg[0] != "profile" || seg[2] != "post") throw IOException("not a Bluesky post URL")
        val api = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("uri", "at://${seg[1]}/app.bsky.feed.post/${seg[3]}")
            .addQueryParameter("depth", "0").build()
        val thread = try { getJson(api) } catch (e: IOException) { throw IOException("post lookup failed (${e.message})") }
        val found = mutableListOf<String>()
        collectFullsize(thread["thread"]["post"]["embed"], found)
        if (found.isEmpty()) throw IOException("post has no images")
        return found.mapNotNull { it.toHttpUrlOrNull() }
    }

    private fun resolvePixiv(url: HttpUrl): List<HttpUrl> {
        val seg = segments(url)
        val at = seg.indexOf("artworks").takeIf { it >= 0 } ?: throw IOException("not a Pixiv artwork URL")
        val id = seg.getOrNull(at + 1)?.takeIf { s -> s.all { it.isDigit() } } ?: throw IOException("no artwork id")
        val pages = try {
            getJson(parse("https://www.pixiv.net/ajax/illust/$id/pages"), referer = "https://www.pixiv.net/")
        } catch (e: IOException) {
            throw IOException("artwork lookup failed (${e.message}; R18 works need a login)")
        }
        // Every page: a multi-page work's deleted post may be any of them.
        val urls = (pages["body"] as? JsonArray)?.mapNotNull { it["urls"]["original"].str()?.toHttpUrlOrNull() }.orEmpty()
        if (urls.isEmpty()) throw IOException("artwork has no pages")
        return urls
    }

    private fun resolveDeviantArt(url: HttpUrl): List<HttpUrl> {
        val api = "https://backend.deviantart.com/oembed".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("url", url.toString()).build()
        val o = try { getJson(api) } catch (e: IOException) { throw IOException("oEmbed lookup failed (${e.message})") }
        val chosen = (if (o["type"].str() == "photo") o["url"].str() else null) ?: o["thumbnail_url"].str()
            ?: throw IOException("oEmbed returned no image")
        return listOf(parse(chosen))
    }

    /** HentaiVox reader pages declare no meta image; the page image is `<img class="js-main-img">`. */
    private fun resolveHentaiVox(url: HttpUrl): List<HttpUrl> {
        val html = fetchHtml(url)
        val tag = tags(html, "img").firstOrNull { t -> attr(t, "class")?.split(Regex("\\s+"))?.contains("js-main-img") == true }
            ?: throw IOException("no reader image on the page")
        val src = attr(tag, "src") ?: throw IOException("main image has no src")
        return listOf(url.resolve(src.trim()) ?: throw IOException("bad image URL"))
    }

    /**
     * Fur Affinity: the full-size file is the `submissionImg`'s `data-fullview-src` (the og:image
     * is a 600px thumbnail). Logged-out visitors get a "System Message" page for Mature/Adult work
     * whose og:image is the FA logo, so that's reported as final rather than scraped.
     */
    private fun resolveFurAffinity(url: HttpUrl): List<HttpUrl> {
        val html = fetchHtml(url)
        val tag = tags(html, "img").firstOrNull { attr(it, "id") == "submissionImg" }
        if (tag != null) {
            val src = attr(tag, "data-fullview-src") ?: attr(tag, "src") ?: throw IOException("submission image has no src")
            return listOf(url.resolve(src.trim()) ?: throw IOException("bad image URL"))
        }
        if (html.contains("you must log in", ignoreCase = true)) {
            throw IOException("$LOGIN_REQUIRED: Fur Affinity only shows this Mature/Adult submission to signed-in users - sign in under Settings > Account > Source logins, with Mature/Adult content enabled on your FA account")
        }
        throw IOException("no submission image on the page (removed or disabled?)")
    }

    /** null = no special handling for this host. */
    private fun resolveSite(url: HttpUrl, wantExt: String): (() -> List<HttpUrl>)? =
        when (url.host.lowercase().removePrefix("www.").removePrefix("mobile.")) {
            "twitter.com", "x.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com" -> { { resolveTwitter(url, wantExt) } }
            "bsky.app" -> { { resolveBluesky(url) } }
            "pixiv.net" -> { { resolvePixiv(url) } }
            "deviantart.com", "fav.me" -> { { resolveDeviantArt(url) } }
            "hentaivox.com" -> { { resolveHentaiVox(url) } }
            "furaffinity.net" -> { { resolveFurAffinity(url) } }
            else -> null
        }

    // --- Finding and grading ----------------------------------------------------------------

    private sealed interface Found {
        class Image(val mime: String, val bytes: ByteArray, val url: HttpUrl) : Found
        class Urls(val urls: List<HttpUrl>) : Found
    }

    private fun findGeneric(url: HttpUrl): Found {
        val response = get(url)
        val ct = response.contentType()
        return when {
            ct.startsWith("image/") -> Found.Image(ct, response.readCapped(MAX_IMAGE_BYTES), url)
            ct == "text/html" || ct == "application/xhtml+xml" -> {
                val html = response.readCapped(MAX_HTML_BYTES).decodeToString()
                Found.Urls(listOf(findMetaImage(html, url) ?: throw IOException("no og:image on the page")))
            }
            else -> { response.close(); throw IOException("unsupported content (${ct.ifEmpty { "unknown type" }})") }
        }
    }

    private fun findImages(source: String, wantExt: String): Found {
        val url = parse(source)
        val resolver = resolveSite(url, wantExt)
        var siteError: String? = null
        if (resolver != null) {
            try {
                return Found.Urls(resolver())
            } catch (e: IOException) {
                if (e.message.orEmpty().startsWith(LOGIN_REQUIRED)) throw e
                siteError = e.message
            }
        }
        return try {
            findGeneric(url)
        } catch (e: IOException) {
            throw IOException(if (siteError != null) "$siteError; page scrape: ${e.message}" else e.message)
        }
    }

    private fun fetchImage(url: HttpUrl): Pair<String, ByteArray> {
        val response = get(url)
        val ct = response.contentType()
        if (!ct.startsWith("image/")) {
            response.close()
            throw IOException("not an image (${ct.ifEmpty { "unknown type" }})")
        }
        return ct to response.readCapped(MAX_IMAGE_BYTES)
    }

    private fun normalizeExt(ext: String): String =
        ext.trim().removePrefix(".").lowercase().let { if (it == "jpeg") "jpg" else it }

    private fun extForMime(mime: String) = when (mime) {
        "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg"
        "image/png", "image/apng" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        else -> ""
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Grades fetched bytes against the post's original file; null when they aren't a decodable image. */
    private fun score(post: Post, source: String, mime: String, bytes: ByteArray, imageUrl: HttpUrl, viaArchive: Boolean): RecoveredImage? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return null

        val file = post.file
        val notes = mutableListOf<Note>()
        val md5Match = file.md5?.trim()?.equals(md5Hex(bytes), ignoreCase = true) == true

        val wantExt = normalizeExt(file.ext)
        val gotExt = extForMime(mime)
        val extMatch = wantExt.isEmpty() || wantExt == gotExt
        if (!extMatch) notes += Note.Format(gotExt.ifEmpty { mime }, wantExt)

        val haveDims = file.width > 0 && file.height > 0
        val sameDims = haveDims && w == file.width && h == file.height
        val sizeRatio = if (file.size > 0) bytes.size.toDouble() / file.size else 1.0

        val grade = when {
            md5Match -> Grade.EXACT
            sameDims || !haveDims -> Grade.SAME_DIMENSIONS
            else -> {
                val aspectOff = kotlin.math.abs((w.toDouble() / h) / (file.width.toDouble() / file.height) - 1.0)
                if (aspectOff <= 0.015) Grade.RESCALED else Grade.MISMATCH
            }
        }
        if (!md5Match) {
            if (haveDims && !sameDims) notes += Note.Dimensions(w, h, file.width, file.height)
            if (file.size > 0 && (sizeRatio < 0.9 || sizeRatio > 1.1)) notes += if (sizeRatio < 1.0) Note.SmallerFile else Note.LargerFile
        }
        if (grade == Grade.MISMATCH) notes += Note.ShapeDiffers

        // Byte-size closeness 0..100, only a tiebreaker within a grade.
        val closeness = if (file.size > 0) {
            val r = sizeRatio.coerceAtLeast(1e-6)
            Math.round(100.0 * minOf(r, 1.0 / r))
        } else 0L
        val score = grade.ordinal * 1000L + (if (extMatch) 100 else 0) + closeness

        return RecoveredImage(bytes, source, imageUrl.toString(), mime, viaArchive, grade, w, h, file.width, file.height, notes, score)
    }

    /** Fetches and grades [found]'s images into [all]. True once an exact (md5) match turns up. */
    private suspend fun evaluate(
        post: Post,
        source: String,
        found: Found,
        viaArchive: Boolean,
        all: MutableList<RecoveredImage>,
        onProgress: (Progress) -> Unit,
    ): Boolean {
        val fetched = mutableListOf<Triple<String, ByteArray, HttpUrl>>()
        when (found) {
            is Found.Image -> fetched += Triple(found.mime, found.bytes, found.url)
            is Found.Urls -> {
                val urls = found.urls.take(MAX_CANDIDATES_PER_SOURCE)
                for ((i, url) in urls.withIndex()) {
                    coroutineContext.ensureActive()
                    onProgress(Progress.Downloading(hostOf(url), i + 1, urls.size))
                    try {
                        val (mime, bytes) = fetchImage(url)
                        fetched += Triple(mime, bytes, url)
                        if (post.file.md5?.equals(md5Hex(bytes), ignoreCase = true) == true) break
                    } catch (_: IOException) {
                        // One failed candidate doesn't sink the source.
                    }
                }
            }
        }
        for ((mime, bytes, url) in fetched) {
            onProgress(Progress.Comparing(hostOf(url)))
            val candidate = score(post, source, mime, bytes, url, viaArchive) ?: continue
            if (all.none { it.bytes.contentEquals(candidate.bytes) }) all += candidate
            if (candidate.grade == Grade.EXACT) return true
        }
        // Keep memory bounded: only the best few files survive.
        if (all.size > MAX_KEPT) {
            all.sortByDescending { it.score }
            while (all.size > MAX_KEPT) all.removeAt(all.lastIndex)
        }
        return false
    }

    /** Timestamp of the Wayback Machine's closest snapshot of [url], if any. */
    private fun waybackSnapshot(url: String): String? = try {
        val api = "https://archive.org/wayback/available".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("url", url).build()
        val closest = getJson(api)["archived_snapshots"]["closest"]
        if ((closest["available"] as? JsonPrimitive)?.booleanOrNull == true) closest["timestamp"].str() else null
    } catch (_: Exception) {
        null
    }

    /**
     * The Wayback Machine's raw capture (`<ts>id_`: original bytes, no toolbar or rewriting) of
     * [url]: the image itself, or for a page, the raw archived copy of its og:image.
     */
    private fun findArchived(url: String): Found {
        val ts = waybackSnapshot(url) ?: throw IOException("no Wayback snapshot")
        val raw = parse("https://web.archive.org/web/${ts}id_/$url")
        val page = parse(url)
        return when (val found = findGeneric(raw)) {
            is Found.Image -> Found.Image(found.mime, found.bytes, raw)
            is Found.Urls -> {
                val archived = found.urls.mapNotNull { u ->
                    val s = u.toString()
                    val original = page.resolve(if (s.contains("id_/")) s.substringAfter("id_/") else s) ?: return@mapNotNull null
                    "https://web.archive.org/web/${ts}id_/$original".toHttpUrlOrNull()
                }
                if (archived.isEmpty()) throw IOException("no archived image")
                Found.Urls(archived)
            }
        }
    }

    /**
     * Tries every web source of the deleted [post] and returns the usable images found, best
     * match to the original first. Throws [RecoveryFailed] when nothing came back.
     */
    suspend fun recover(post: Post, onProgress: (Progress) -> Unit): List<RecoveredImage> = withContext(Dispatchers.IO) {
        val sources = webSources(post)
        if (sources.isEmpty()) throw RecoveryFailed(noSources = true, details = emptyList())
        val wantExt = normalizeExt(post.file.ext)
        val failures = mutableListOf<String>()
        val all = mutableListOf<RecoveredImage>()
        var done = false

        for ((i, source) in sources.withIndex()) {
            ensureActive()
            val host = hostOf(source)
            onProgress(Progress.Fetching(host, i + 1, sources.size))
            try {
                if (evaluate(post, source, findImages(source, wantExt), false, all, onProgress)) {
                    done = true
                    break
                }
            } catch (e: IOException) {
                val msg = e.message ?: e.javaClass.simpleName
                onProgress(Progress.Failed(host, msg.lineSequence().first()))
                failures += "$source: $msg"
            }
        }

        fun goodEnough() = all.any { it.grade >= Grade.SAME_DIMENSIONS }

        if (!done && !goodEnough()) {
            val targets = sources.toMutableList()
            val md5 = post.file.md5?.trim()
            if (md5 != null && md5.length >= 4) {
                targets += "https://static1.e621.net/data/${md5.substring(0, 2)}/${md5.substring(2, 4)}/$md5.${wantExt.ifEmpty { "jpg" }}"
            }
            for (target in targets) {
                ensureActive()
                onProgress(Progress.Archive)
                try {
                    if (evaluate(post, target, findArchived(target), true, all, onProgress)) break
                } catch (e: IOException) {
                    failures += "$target (Wayback): ${e.message}"
                }
                if (goodEnough()) break
            }
        }

        // Stable sort, so equal scores keep the order the sources were tried in.
        val ranked = all.sortedByDescending { it.score }
        if (ranked.isEmpty()) throw RecoveryFailed(noSources = false, details = failures)
        synchronized(cache) { cache[post.id] = ranked }
        ranked
    }
}
