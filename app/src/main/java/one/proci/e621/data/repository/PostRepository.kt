package one.proci.e621.data.repository

import kotlinx.coroutines.flow.StateFlow
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Post
import one.proci.e621.data.settings.UserSettings

class PostRepository(
    private val api: E621ApiService,
    private val settings: StateFlow<UserSettings>,
) {
    /**
     * @param beforeId when set, fetches posts older than this id (keyset pagination), used
     * for infinite scroll since e621's results are sorted newest-first by default.
     */
    suspend fun fetchPosts(tags: String, beforeId: Long? = null, limit: Int = 50): List<Post> {
        val ratingFilter = settings.value.ratingTagFilter()
        val combinedTags = listOfNotNull(tags.trim().ifBlank { null }, ratingFilter)
            .joinToString(" ")
            .ifBlank { null }
        val page = beforeId?.let { "b$it" }
        return api.getPosts(tags = combinedTags, limit = limit, page = page).posts
    }
}
