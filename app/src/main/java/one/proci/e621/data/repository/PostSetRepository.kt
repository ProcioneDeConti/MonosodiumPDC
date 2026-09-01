package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.CreatePostSetFields
import one.proci.e621.data.model.CreatePostSetRequest
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.PostSet
import one.proci.e621.data.model.SetPostIdsRequest
import one.proci.e621.data.util.PERMANENTLY_HIDDEN_TAG

data class PostSetContent(val set: PostSet, val posts: List<Post>)

class PostSetRepository(private val api: E621ApiService) {

    /** The signed-in user's own sets. Returns empty when signed out. */
    suspend fun fetchMySets(): List<PostSet> {
        val meId = runCatching { api.getCurrentUser().id }.getOrNull() ?: return emptyList()
        return api.getPostSets(creatorId = meId).sortedBy { it.name.lowercase() }
    }

    /** A set's posts, in the set's own order (see [PoolRepository] for the same id: + re-sort trick). */
    suspend fun fetchSetContent(setId: Long): PostSetContent {
        val set = api.getPostSet(setId)
        val ids = set.postIds.take(MAX_SET_POSTS)
        if (ids.isEmpty()) return PostSetContent(set, emptyList())
        val order = ids.withIndex().associate { (i, id) -> id to i }
        val posts = api.getPosts(
            tags = "id:${ids.joinToString(",")} -$PERMANENTLY_HIDDEN_TAG",
            limit = ids.size,
        ).posts
        return PostSetContent(set, posts.sortedBy { order[it.id] ?: Int.MAX_VALUE })
    }

    /**
     * Creates a set. `shortname` must be 3-50 chars of `[a-z0-9_]` with at least one letter or
     * underscore - callers should validate before calling; the server rejects otherwise.
     */
    suspend fun createSet(name: String, shortname: String, isPublic: Boolean): PostSet =
        api.createPostSet(CreatePostSetRequest(CreatePostSetFields(name.trim(), shortname.trim(), isPublic = isPublic)))

    suspend fun addPost(setId: Long, postId: Long) {
        val response = api.addPostsToSet(setId, SetPostIdsRequest(listOf(postId)))
        if (!response.isSuccessful) error("e621 rejected the change (HTTP ${response.code()})")
    }

    suspend fun removePost(setId: Long, postId: Long) {
        val response = api.removePostsFromSet(setId, SetPostIdsRequest(listOf(postId)))
        if (!response.isSuccessful) error("e621 rejected the change (HTTP ${response.code()})")
    }

    suspend fun deleteSet(setId: Long) {
        val response = api.deletePostSet(setId)
        if (!response.isSuccessful) error("e621 rejected the deletion (HTTP ${response.code()})")
    }

    private companion object {
        const val MAX_SET_POSTS = 320
    }
}
