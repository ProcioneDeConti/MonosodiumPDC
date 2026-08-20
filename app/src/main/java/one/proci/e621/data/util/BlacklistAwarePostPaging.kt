package one.proci.e621.data.util

import one.proci.e621.data.model.Post

data class PostPageResult(val posts: List<Post>, val reachedEnd: Boolean)

/**
 * Fetches pages via [fetchPage] (which should update its own cursor/page-number state as a side
 * effect and return the fetched page) and accumulates them, deduplicating against [seenIds],
 * until either a fetched page contains at least one non-blacklisted post, the feed ends, or
 * [maxAttempts] pages have been fetched - so a page that's entirely blacklisted doesn't leave
 * loadMore() looking like it silently did nothing. Shared between PostGridViewModel and
 * FavoritesViewModel, which only differ in how they compute each page's cursor.
 */
suspend fun accumulatePostsUntilVisibleOrEnd(
    seenIds: MutableSet<Long>,
    blacklistDisabled: Boolean,
    isBlacklisted: (Post) -> Boolean,
    maxAttempts: Int = 5,
    fetchPage: suspend () -> List<Post>,
): PostPageResult {
    val accumulated = mutableListOf<Post>()
    var reachedEnd = false
    var attempts = 0
    while (attempts < maxAttempts) {
        attempts++
        val page = fetchPage()
        if (page.isEmpty()) {
            reachedEnd = true
            break
        }
        val newPosts = page.filter { seenIds.add(it.id) }
        accumulated += newPosts
        if (blacklistDisabled || newPosts.any { !isBlacklisted(it) }) break
    }
    return PostPageResult(accumulated, reachedEnd)
}
