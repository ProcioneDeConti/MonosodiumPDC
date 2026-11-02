package one.proci.e621.data.settings

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How already-opened posts appear in the main search grid. */
enum class SeenMode { DIM, HIDE, OFF;
    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DIM
    }
}

/**
 * Seen-post tracking (ported from the desktop app): every post opened in the viewer is remembered
 * per site - e621 and e6AI ids are separate spaces - so the search grid can dim or hide it later.
 * Local-only, one plain id-per-line file per site under filesDir/seen_posts, insertion-ordered and
 * capped at [MAX_PER_SITE] so the oldest ids fall off first. Writes are debounced since swiping
 * through the viewer can mark a post every second. Not part of settings backup, like the desktop.
 */
class SeenPostsStore(context: Context, private val scope: CoroutineScope) {

    private val dir = File(context.applicationContext.filesDir, "seen_posts")
    private val prefs = context.applicationContext.getSharedPreferences("seen_posts", Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow<Map<Site, Set<Long>>>(emptyMap())
    /** Replaced (never mutated) on every change so collectors re-emit. */
    val seen: StateFlow<Map<Site, Set<Long>>> = _seen.asStateFlow()

    private val _mode = MutableStateFlow(SeenMode.fromName(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<SeenMode> = _mode.asStateFlow()

    private var persistJob: Job? = null
    private val dirtySites = mutableSetOf<Site>()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = Site.entries.associateWith { site ->
                runCatching { fileFor(site).readLines().mapNotNullTo(LinkedHashSet()) { it.trim().toLongOrNull() } }
                    .getOrDefault(LinkedHashSet())
            }
            // Merge in anything marked before the load finished.
            _seen.update { current -> loaded.mapValues { (site, ids) -> LinkedHashSet(ids).apply { addAll(current[site].orEmpty()) } } }
        }
    }

    fun setMode(mode: SeenMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun markSeen(site: Site, postId: Long) {
        if (_seen.value[site]?.contains(postId) == true) return
        _seen.update { current ->
            val next = LinkedHashSet(current[site].orEmpty())
            next.add(postId)
            if (next.size > MAX_PER_SITE) next.remove(next.first())
            current + (site to next)
        }
        synchronized(dirtySites) { dirtySites += site }
        persistJob?.cancel()
        persistJob = scope.launch(Dispatchers.IO) {
            delay(PERSIST_DEBOUNCE_MS)
            flush()
        }
    }

    fun clear() {
        persistJob?.cancel()
        _seen.value = emptyMap()
        synchronized(dirtySites) { dirtySites.clear() }
        scope.launch(Dispatchers.IO) { runCatching { dir.deleteRecursively() } }
    }

    private suspend fun flush() = withContext(Dispatchers.IO) {
        val sites = synchronized(dirtySites) { dirtySites.toList().also { dirtySites.clear() } }
        val snapshot = _seen.value
        dir.mkdirs()
        for (site in sites) {
            runCatching {
                val tmp = File(dir, "${site.name}.tmp")
                tmp.writeText(snapshot[site].orEmpty().joinToString("\n"))
                tmp.renameTo(fileFor(site))
            }
        }
    }

    private fun fileFor(site: Site) = File(dir, "${site.name}.txt")

    private companion object {
        const val MAX_PER_SITE = 50_000
        const val PERSIST_DEBOUNCE_MS = 2_000L
        const val KEY_MODE = "mode"
    }
}
