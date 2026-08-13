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

        cache[trimmed]?.let { entry ->
            if (System.currentTimeMillis() - entry.cachedAtMs < ttlMs) return entry.results
        }

        val results = runCatching { api.autocompleteTags("$trimmed*") }.getOrDefault(emptyList())
        cache[trimmed] = CacheEntry(results, System.currentTimeMillis())
        return results
    }
}
