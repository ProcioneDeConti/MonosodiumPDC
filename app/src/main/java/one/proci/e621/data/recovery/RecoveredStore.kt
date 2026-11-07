package one.proci.e621.data.recovery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import one.proci.e621.data.settings.Site
import java.io.File

/**
 * On-disk cache of deleted-post recoveries (ported from the desktop's `recovered.rs`), so a
 * recovered post opens instantly next time - across restarts - without contacting any third-party
 * site again, and so the grid can show it as a thumbnail.
 *
 * Layout: `filesDir/recovered/<site>/<post id>/` holds each candidate file as `<md5>.<ext>`, a small
 * grid copy of the pick as `<md5>.thumb.jpg|png`, and `index.json` (a [RecoveredSet]). Kept in
 * filesDir rather than cacheDir: Android may empty cacheDir under storage pressure, and these files
 * can't be fetched again once the sources die. Settings > Media shows its size and can clear it.
 */
object RecoveredStore {

    /** What the grid needs for one post: the picked file and its small copy, or a not-found mark. */
    data class Thumb(
        val file: File?,
        val thumb: File?,
        val isVideo: Boolean,
        val notFoundAt: Long?,
        /** When the copy was recovered (unix seconds): the finding search's time, else the index's mtime. */
        val recoveredAt: Long?,
    ) {
        val hasCopy: Boolean get() = file != null
    }

    private const val INDEX_FILE = "index.json"
    private const val THUMB_MAX = 480

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private lateinit var root: File
    private val lock = Any()

    private val _thumbs = MutableStateFlow<Map<Site, Map<Long, Thumb>>>(emptyMap())
    /** Every cached post per site, for grid tiles and the Recovered posts page. */
    val thumbs: StateFlow<Map<Site, Map<Long, Thumb>>> = _thumbs.asStateFlow()

    /** The site the app is showing (set by the Application), for UI that has no site of its own. */
    val currentSite = MutableStateFlow(Site.E621)

    fun init(context: Context, scope: CoroutineScope) {
        root = File(context.filesDir, "recovered")
        scope.launch(Dispatchers.IO) {
            _thumbs.value = Site.entries.associateWith(::scanSite)
            // Grid copies for posts whose thumbnail is missing (e.g. a failed earlier write).
            Site.entries.forEach { site ->
                _thumbs.value[site].orEmpty().forEach { (id, t) ->
                    if (t.hasCopy && t.thumb == null && !t.isVideo) refreshThumb(site, id)
                }
            }
        }
    }

    private fun siteKey(site: Site) = site.name.lowercase()

    fun postDir(site: Site, postId: Long) = File(File(root, siteKey(site)), postId.toString())

    fun fileOf(site: Site, postId: Long, item: RecoveredImage) = File(postDir(site, postId), item.file)

    /** Cache file names are always `<md5>.<ext>`, so nothing can point outside a post's folder. */
    private fun validFileName(name: String): Boolean {
        val stem = name.substringBefore('.', "")
        val ext = name.substringAfter('.', "")
        return stem.length == 32 && stem.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } &&
            ext.length in 1..5 && ext.all { it.isLetterOrDigit() }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw java.io.IOException("couldn't write ${file.name}")
        }
    }

    /** Writes one candidate's bytes (skipped if that md5 is already cached). Returns its file name. */
    fun storeFile(site: Site, postId: Long, md5: String, ext: String, bytes: ByteArray): String {
        val name = "$md5.${ext.ifEmpty { "bin" }}"
        if (!validFileName(name)) throw java.io.IOException("bad cache file name $name")
        val file = File(postDir(site, postId), name)
        if (!file.isFile) writeAtomic(file, bytes)
        return name
    }

    private fun readIndex(dir: File): RecoveredSet? =
        runCatching { json.decodeFromString(RecoveredSet.serializer(), File(dir, INDEX_FILE).readText()) }.getOrNull()

    private fun writeIndex(dir: File, set: RecoveredSet) =
        writeAtomic(File(dir, INDEX_FILE), json.encodeToString(RecoveredSet.serializer(), set).toByteArray())

    /** Drops entries whose file has gone missing and keeps `selected` valid. */
    private fun finish(dir: File, set: RecoveredSet): RecoveredSet {
        val items = set.items.filter { validFileName(it.file) && File(dir, it.file).isFile }
        val selected = set.selected?.takeIf { s -> items.any { it.file == s } } ?: items.firstOrNull()?.file
        return set.copy(items = items, selected = selected)
    }

    private fun thumbMd5(name: String): String? {
        val md5 = name.removeSuffix(".thumb.jpg").removeSuffix(".thumb.png")
        return md5.takeIf { it != name && it.length == 32 }
    }

    /** Deletes files no longer listed in the index (left by a cancelled run, or replaced). */
    private fun removeStrays(dir: File, set: RecoveredSet) {
        dir.listFiles()?.forEach { f ->
            val name = f.name
            val md5 = thumbMd5(name)
            val stray = when {
                md5 != null -> set.items.none { it.md5.equals(md5, ignoreCase = true) }
                name.endsWith(".tmp") -> true
                else -> validFileName(name) && set.items.none { it.file == name }
            }
            if (stray) f.delete()
        }
    }

    private fun existingThumb(dir: File, md5: String): File? =
        listOf("jpg", "png").map { File(dir, "$md5.thumb.$it") }.firstOrNull { it.isFile }

    /**
     * The small grid copy of a recovered image: JPEG, or PNG when it has transparency (a JPEG would
     * turn it black). Null for videos and files the platform can't decode. Recovered originals are
     * often 2-4K and several MB; decoding those per grid cell made the desktop grid lag.
     */
    private fun makeThumb(dir: File, item: RecoveredImage): File? {
        if (item.isVideo) return null
        existingThumb(dir, item.md5)?.let { return it }
        val source = File(dir, item.file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_MAX) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = THUMB_MAX.toFloat() / maxOf(decoded.width, decoded.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        val png = small.hasAlpha()
        val out = java.io.ByteArrayOutputStream()
        small.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, out)
        if (small !== decoded) small.recycle()
        decoded.recycle()
        val file = File(dir, "${item.md5}.thumb.${if (png) "png" else "jpg"}")
        return runCatching { writeAtomic(file, out.toByteArray()); file }.getOrNull()
    }

    private fun summarize(dir: File, set: RecoveredSet): Thumb? {
        val pick = set.picked
        return when {
            pick != null -> Thumb(
                file = File(dir, pick.file),
                thumb = existingThumb(dir, pick.md5),
                isVideo = pick.isVideo,
                notFoundAt = null,
                recoveredAt = set.report?.searchedAt ?: (File(dir, INDEX_FILE).lastModified() / 1000),
            )
            set.notFoundAt != null -> Thumb(null, null, false, set.notFoundAt, null)
            else -> null
        }
    }

    private fun scanSite(site: Site): Map<Long, Thumb> {
        val out = HashMap<Long, Thumb>()
        File(root, siteKey(site)).listFiles()?.forEach { dir ->
            val id = dir.name.toLongOrNull() ?: return@forEach
            val set = readIndex(dir)?.let { finish(dir, it) } ?: return@forEach
            summarize(dir, set)?.let { out[id] = it }
        }
        return out
    }

    private fun publish(site: Site, postId: Long, thumb: Thumb?) {
        _thumbs.update { all ->
            val forSite = all[site].orEmpty().toMutableMap()
            if (thumb == null) forSite.remove(postId) else forSite[postId] = thumb
            all + (site to forSite)
        }
    }

    private fun refreshThumb(site: Site, postId: Long) {
        val dir = postDir(site, postId)
        val set = synchronized(lock) { readIndex(dir)?.let { finish(dir, it) } } ?: return
        set.picked?.let { makeThumb(dir, it) }
        publish(site, postId, summarize(dir, set))
    }

    /** The cached recovery for one post, if any (found files, or a not-found mark). */
    suspend fun load(site: Site, postId: Long): RecoveredSet? = withContext(Dispatchers.IO) {
        val dir = postDir(site, postId)
        val set = synchronized(lock) { readIndex(dir)?.let { finish(dir, it) } } ?: return@withContext null
        set.takeIf { it.items.isNotEmpty() || it.notFoundAt != null }
    }

    private fun nowSecs() = System.currentTimeMillis() / 1000

    /**
     * Merges a fresh run's results (best first) into what's cached for the post: a file found again
     * takes the new metadata, files only an older run found are kept (a source may have died since).
     * The user's pick survives if its file is still there.
     */
    fun save(site: Site, postId: Long, fresh: List<RecoveredImage>, attempts: List<Attempt>): RecoveredSet {
        val dir = postDir(site, postId)
        val set = synchronized(lock) {
            val old = readIndex(dir) ?: RecoveredSet()
            val items = (fresh + old.items.filter { o -> fresh.none { it.md5 == o.md5 } }).sortedByDescending { it.score }
            finish(dir, RecoveredSet(old.selected, items, null, SearchReport(nowSecs(), attempts))).also {
                writeIndex(dir, it)
                removeStrays(dir, it)
            }
        }
        set.picked?.let { makeThumb(dir, it) }
        publish(site, postId, summarize(dir, set))
        return set
    }

    /** Records that a full search found nothing - unless copies from an earlier search are still cached. */
    fun markNotFound(site: Site, postId: Long, attempts: List<Attempt>) {
        val dir = postDir(site, postId)
        val set = synchronized(lock) {
            val old = readIndex(dir)?.let { finish(dir, it) }
            if (old != null && old.items.isNotEmpty()) return
            val now = nowSecs()
            RecoveredSet(notFoundAt = now, report = SearchReport(now, attempts)).also {
                runCatching {
                    dir.mkdirs()
                    writeIndex(dir, it)
                    removeStrays(dir, it)
                }
            }
        }
        publish(site, postId, summarize(dir, set))
    }

    /** Remembers which candidate the user picked (it becomes the grid thumbnail). */
    suspend fun select(site: Site, postId: Long, file: String) = withContext(Dispatchers.IO) {
        val dir = postDir(site, postId)
        synchronized(lock) {
            val set = readIndex(dir) ?: return@withContext
            if (set.items.none { it.file == file }) return@withContext
            writeIndex(dir, set.copy(selected = file))
        }
        refreshThumb(site, postId)
    }

    /** Deletes everything recovered for one post. */
    suspend fun forget(site: Site, postId: Long) = withContext(Dispatchers.IO) {
        synchronized(lock) { postDir(site, postId).deleteRecursively() }
        publish(site, postId, null)
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        synchronized(lock) { root.deleteRecursively() }
        _thumbs.value = emptyMap()
    }
}
