package one.proci.e621.data.repository

import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import one.proci.e621.data.model.CategorizedTag
import one.proci.e621.data.model.TagCategory
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Comment
import one.proci.e621.data.model.CreateCommentFields
import one.proci.e621.data.model.CreateCommentRequest
import one.proci.e621.data.model.CreateTicketFields
import one.proci.e621.data.model.CreateTicketRequest
import one.proci.e621.data.model.FavoriteRequest
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.PostNote
import one.proci.e621.data.model.UpdateCommentFields
import one.proci.e621.data.model.UpdateCommentRequest
import one.proci.e621.data.model.VoteRequest

/** Casts votes and favorites against the user's own e621 account. */
class PostActionsRepository(
    private val api: E621ApiService,
    private val usageStats: one.proci.e621.data.settings.UsageStatsStore? = null,
    private val appScope: kotlinx.coroutines.CoroutineScope? = null,
) {

    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun stat(block: suspend one.proci.e621.data.settings.UsageStatsStore.() -> Unit) {
        val store = usageStats ?: return
        appScope?.launch { runCatching { store.block() } }
    }

    /** Records that [post] was viewed (for the on-device Dashboard). No-op when stats are off. */
    fun recordPostView(post: Post, siteName: String) =
        stat { recordPostView(siteName, post.tags.artist, post.tags.character) }

    /** [direction] is always the button's fixed intent (1 or -1); the server toggles it off if already at that value. */
    suspend fun vote(post: Post, direction: Int): Post {
        val response = api.vote(post.id, VoteRequest(direction))
        stat { recordVote(direction) }
        return post.copy(
            score = post.score.copy(up = response.up, down = response.down, total = response.score),
            voteBy = response.ourScore,
        )
    }

    suspend fun favorite(post: Post): Post {
        val response = api.addFavorite(FavoriteRequest(post.id))
        stat { recordFavorite(added = true) }
        return post.copy(isFavorited = true, favCount = response.favoriteCount)
    }

    suspend fun unfavorite(post: Post): Post {
        val response = api.removeFavorite(post.id)
        stat { recordFavorite(added = false) }
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

    /** A post's edit history (tag/rating/source/description changes), newest first. */
    suspend fun fetchPostVersions(postId: Long): List<one.proci.e621.data.model.PostVersion> =
        api.getPostVersions(postId)

    /** A post's active translation/annotation notes (inactive ones are filtered out). */
    suspend fun fetchNotes(postId: Long): List<PostNote> =
        api.getNotes(postId).filter { it.isActive && it.width > 0 && it.height > 0 }

    /**
     * Tags statistically related to [tag], category-tagged. Parses whichever shape e621ng's
     * `related_tag.json` currently returns: a bare array of `{name, category_id}`, a
     * `{"related_tags": [...]}` wrapper, `[name, category]` pairs, or the oldest form keyed by
     * the query string. Returns empty on any parse failure or when the user isn't a member.
     */
    suspend fun fetchRelatedTags(tag: String): List<CategorizedTag> {
        val raw = api.getRelatedTags(tag).use { it.string() }
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val array: JsonArray? = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["related_tags"] as? JsonArray)
                ?: (root[tag] as? JsonArray)
                ?: (root[tag.lowercase()] as? JsonArray)
                ?: root.values.filterIsInstance<JsonArray>().firstOrNull()
            else -> null
        }
        return array.orEmpty().mapNotNull(::parseRelatedPair)
    }

    private fun parseRelatedPair(element: JsonElement): CategorizedTag? {
        val (name, categoryId) = when (element) {
            is JsonArray -> {
                val n = element.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return null
                n to (element.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0)
            }
            is JsonObject -> {
                val obj = (element["tag"] as? JsonObject) ?: element
                val n = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
                val c = (obj["category_id"] ?: obj["category"])?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                n to c
            }
            else -> return null
        }
        return CategorizedTag(name, tagCategoryFromId(categoryId))
    }

    private fun tagCategoryFromId(id: Int): TagCategory = when (id) {
        1 -> TagCategory.ARTIST
        3 -> TagCategory.COPYRIGHT
        4 -> TagCategory.CHARACTER
        5 -> TagCategory.SPECIES
        7 -> TagCategory.META
        8 -> TagCategory.LORE
        else -> TagCategory.GENERAL
    }

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

    /**
     * Votes on a comment. e621's comment index doesn't reliably serialize `vote_by`/`score`, so
     * the returned copy is patched from the vote response (which is authoritative - voting the
     * same way again toggles the vote off server-side).
     */
    suspend fun voteComment(comment: Comment, direction: Int): Comment {
        val response = api.voteComment(comment.id, VoteRequest(direction))
        return comment.copy(score = response.score, voteBy = response.ourScore)
    }

    /** Edits one of the signed-in user's own comments. Returns the local copy patched with [body]. */
    suspend fun editComment(comment: Comment, body: String): Comment {
        val response = api.updateComment(comment.id, UpdateCommentRequest(UpdateCommentFields(body)))
        if (!response.isSuccessful) error("e621 rejected the edit (HTTP ${response.code()})")
        return comment.copy(body = body)
    }

    suspend fun deleteComment(commentId: Long) {
        val response = api.deleteComment(commentId)
        if (!response.isSuccessful) error("e621 rejected the deletion (HTTP ${response.code()})")
    }

    suspend fun reportComment(commentId: Long, reason: String) = fileTicket(commentId, "comment", reason)

    suspend fun reportPost(postId: Long, reason: String) = fileTicket(postId, "post", reason)

    private suspend fun fileTicket(dispId: Long, qtype: String, reason: String) {
        val response = api.createTicket(CreateTicketRequest(CreateTicketFields(dispId, qtype, reason)))
        if (!response.isSuccessful) error("e621 rejected the report (HTTP ${response.code()})")
    }

    /**
     * The signed-in account's own user id, or null when signed out. Not cached (accounts differ
     * per site) - fetched once per comments-sheet open, only to decide whether to show the
     * edit/delete controls on a comment.
     */
    suspend fun currentUserId(): Long? = runCatching { api.getCurrentUser().id }.getOrNull()
}
