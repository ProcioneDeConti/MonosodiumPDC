package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.TagSuggestion

class TagSuggestionRepository(private val api: E621ApiService) {
    suspend fun suggest(prefix: String): List<TagSuggestion> {
        val trimmed = prefix.trim()
        if (trimmed.length < 2) return emptyList()
        return runCatching { api.autocompleteTags("$trimmed*") }.getOrDefault(emptyList())
    }
}
