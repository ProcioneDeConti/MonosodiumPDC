package one.proci.e621.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Comment
import one.proci.e621.data.model.CreateCommentFields
import one.proci.e621.data.model.CreateCommentRequest
import one.proci.e621.data.model.FavoriteRequest
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.VoteRequest

/** Casts votes and favorites against the user's own e621 account. */
class PostActionsRepository(private val api: E621ApiService) {

    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** [direction] is always the button's fixed intent (1 or -1); the server toggles it off if already at that value. */
    suspend fun vote(post: Post, direction: Int): Post {
        val response = api.vote(post.id, VoteRequest(direction))
        return post.copy(
            score = post.score.copy(up = response.up, down = response.down, total = response.score),
            voteBy = response.ourScore,
        )
    }

    suspend fun favorite(post: Post): Post {
        val response = api.addFavorite(FavoriteRequest(post.id))
        return post.copy(isFavorited = true, favCount = response.favoriteCount)
    }

    suspend fun unfavorite(post: Post): Post {
        val response = api.removeFavorite(post.id)
        return post.copy(isFavorited = false, favCount = response.favoriteCount)
    }

    suspend fun fetchRawJson(postId: Long): String {
        val raw = api.getPostRawJson(postId).use { it.string() }
        return runCatching {
            prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
        }.getOrDefault(raw)
    }

    /** Hidden comments are moderation-only, so they're filtered out client-side. */
    suspend fun fetchComments(postId: Long): List<Comment> =
        api.getComments(postId).filterNot { it.isHidden }

    /** A given user's comments across all posts, most recent first; @param beforeId for infinite scroll. */
    suspend fun fetchCommentsByUser(userId: Long, beforeId: Long? = null, limit: Int = 50): List<Comment> {
        val page = beforeId?.let { "b$it" }
        return api.getCommentsByCreator(creatorId = userId, limit = limit, page = page).filterNot { it.isHidden }
    }

    /** Most recent reason a post was flagged for, or null if e621 has no (visible) flag record for it. */
    suspend fun fetchFlagReason(postId: Long): String? =
        api.getPostFlags(postId, limit = 1).firstOrNull()?.reason?.takeIf { it.isNotBlank() }

    suspend fun postComment(postId: Long, body: String): Comment =
        api.createComment(CreateCommentRequest(CreateCommentFields(postId, body)))
}
