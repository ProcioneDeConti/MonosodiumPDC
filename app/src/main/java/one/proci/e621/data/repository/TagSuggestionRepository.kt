package one.proci.e621.data.repository

import java.util.Collections
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.TagSuggestion

/**
 * Autocomplete fires on every keystroke, and users routinely retype/backspace over the same
 * prefixes - tag names change rarely enough that a short in-memory cache avoids most of that
 * redundant traffic without the results ever feeling stale.
 */
class TagSuggestionRepository(private val api: E621ApiService) {

    private data class CacheEntry(val results: List<TagSuggestion>, val cachedAtMs: Long)

    private val ttlMs = 5 * 60_000L
    private val maxEntries = 200

    // LRU by access order; synchronized since suggest() can be called from multiple typing events in flight.
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>) = size > maxEntries
        },
    )

    suspend fun suggest(prefix: String): List<TagSuggestion> {
        val trimmed = prefix.trim().lowercase()
        if (trimmed.length < 2) return emptyList()
        // A `:` means this is a metatag/operator. e621's *tag* autocomplete can't resolve those,
        // so route to metatag-value completion instead (static enums for rating:/order:/…, live
        // lookups for user:/fav:/pool:); everything else with a colon gets nothing.
        if (':' in trimmed) return suggestMetatagValues(trimmed)

        cache[trimmed]?.let { entry ->
            if (System.currentTimeMillis() - entry.cachedAtMs < ttlMs) return entry.results
        }

        val results = runCatching { api.autocompleteTags("$trimmed*") }.getOrDefault(emptyList())
        cache[trimmed] = CacheEntry(results, System.currentTimeMillis())
        return results
    }

    /**
     * Value completion for a `metatag:value` prefix. Static enums for `rating:`/`order:`/`type:`/
     * `filetype:`/`status:`/`locked:`; live lookups against e621 for `user:`/`fav:`/`pool:`.
     * Anything else (score:, date:, id:, …) returns nothing.
     */
    private suspend fun suggestMetatagValues(prefix: String): List<TagSuggestion> {
        val key = prefix.substringBefore(':')
        val value = prefix.substringAfter(':').trim()

        STATIC_METATAGS[key]?.let { options ->
            return options.filter { it.startsWith(value) }.map { TagSuggestion(name = "$key:$it") }
        }

        if (value.length < 2) return emptyList()
        return when (key) {
            "user", "fav" -> runCatching { api.autocompleteUsers("$value*") }
                .getOrDefault(emptyList())
                .map { TagSuggestion(name = "$key:${it.name}") }
            "pool" -> runCatching { api.autocompletePools("$value*") }
                .getOrDefault(emptyList())
                .map { TagSuggestion(name = "pool:${it.name}", postCount = it.postCount) }
            else -> emptyList()
        }
    }

    private companion object {
        val STATIC_METATAGS: Map<String, List<String>> = mapOf(
            "rating" to listOf("safe", "questionable", "explicit"),
            "order" to listOf(
                "id", "id_desc", "score", "score_asc", "favcount", "favcount_asc",
                "comment_count", "comment_count_asc", "tagcount", "mpixels", "mpixels_asc",
                "filesize", "filesize_asc", "duration", "duration_asc", "change", "random", "rank",
            ),
            "type" to listOf("jpg", "png", "gif", "webm", "mp4", "swf"),
            "filetype" to listOf("jpg", "png", "gif", "webm", "mp4", "swf"),
            "status" to listOf("active", "pending", "flagged", "deleted", "modqueue", "any"),
            "locked" to listOf("rating", "note", "status"),
        )
    }
}
