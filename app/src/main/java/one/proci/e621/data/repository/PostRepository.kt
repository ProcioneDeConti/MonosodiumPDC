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
     * for infinite scroll since e621's results are sorted newest-first by default. Only valid
     * when [tags] has no custom `order:`, since the id cursor doesn't bound a non-id-sorted
     * result set - use [pageNumber] instead for those queries.
     * @param pageNumber when set (and [beforeId] is not), fetches by plain numeric page instead
     * of the id cursor.
     */
    suspend fun fetchPosts(
        tags: String,
        beforeId: Long? = null,
        pageNumber: Int? = null,
        limit: Int = 50,
    ): List<Post> {
        val ratingFilter = settings.value.ratingTagFilter()
        val combinedTags = listOfNotNull(tags.trim().ifBlank { null }, ratingFilter)
            .joinToString(" ")
            .ifBlank { null }
        val page = beforeId?.let { "b$it" } ?: pageNumber?.toString()
        return api.getPosts(tags = combinedTags, limit = limit, page = page).posts
    }
}
