package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Pool
import one.proci.e621.data.model.Post
import one.proci.e621.data.util.PERMANENTLY_HIDDEN_TAG

data class PoolContent(val pool: Pool, val posts: List<Post>)

class PoolRepository(private val api: E621ApiService) {
    /**
     * Fetches a pool and its posts in the pool's own order. e621 has no "fetch these posts,
     * ordered" endpoint, so this fetches by `id:` search (whose result order isn't guaranteed)
     * and re-sorts against `pool.post_ids`, which is authoritative. Capped at e621's per-request
     * limit of 320 - a larger pool shows its first 320 posts (a known limitation, not paginated).
     *
     * The user's rating filter is deliberately not applied (a pool is an explicit pick, not a
     * browse), but the hardcoded content filter still is, and the account still can't see past
     * what the server allows it to.
     */
    suspend fun fetchPoolContent(poolId: Long): PoolContent {
        val pool = api.getPool(poolId)
        val ids = pool.postIds.take(MAX_POOL_POSTS)
        if (ids.isEmpty()) return PoolContent(pool, emptyList())
        val order = ids.withIndex().associate { (i, id) -> id to i }
        val tags = "id:${ids.joinToString(",")} -$PERMANENTLY_HIDDEN_TAG"
        val posts = api.getPosts(tags = tags, limit = ids.size).posts
        return PoolContent(pool, posts.sortedBy { order[it.id] ?: Int.MAX_VALUE })
    }

    private companion object {
        const val MAX_POOL_POSTS = 320
    }
}
