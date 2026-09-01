package one.proci.e621.data.api

import okhttp3.ResponseBody
import one.proci.e621.data.model.Comment
import one.proci.e621.data.model.CreateCommentRequest
import one.proci.e621.data.model.CreateDmailRequest
import one.proci.e621.data.model.CreateForumPostRequest
import one.proci.e621.data.model.CreateTicketRequest
import one.proci.e621.data.model.UpdateCommentRequest
import one.proci.e621.data.model.Dmail
import one.proci.e621.data.model.FavoriteRequest
import one.proci.e621.data.model.FavoriteResponse
import one.proci.e621.data.model.ForumPost
import one.proci.e621.data.model.ForumTopic
import one.proci.e621.data.model.PostFlag
import one.proci.e621.data.model.PostsResponse
import one.proci.e621.data.model.TagSuggestion
import one.proci.e621.data.model.UpdateUserRequest
import one.proci.e621.data.model.UserFeedback
import one.proci.e621.data.model.UserProfile
import one.proci.e621.data.model.VoteRequest
import one.proci.e621.data.model.VoteResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface E621ApiService {

    /**
     * https://e621.net/help/api
     * `page` may be a plain page number, or "b<id>"/"a<id>" for keyset pagination
     * (before/after a given post id) which is what we use for infinite scroll.
     */
    @GET("posts.json")
    suspend fun getPosts(
        @Query("tags") tags: String?,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): PostsResponse

    /**
     * Public. Most-favorited posts for a period. [date] is `YYYY-MM-DD`; [scale] is
     * `day` / `week` / `month`. Returns the same shape as posts.json, already ranked.
     */
    @GET("popular.json")
    suspend fun getPopular(
        @Query("date") date: String,
        @Query("scale") scale: String,
    ): PostsResponse

    /** Requires Basic Auth; returns the authenticated user's own profile, including blacklisted_tags. */
    @GET("users/me.json")
    suspend fun getCurrentUser(): UserProfile

    /** Public; a pool's metadata + authoritative ordered `post_ids`. */
    @GET("pools/{id}.json")
    suspend fun getPool(@Path("id") id: Long): one.proci.e621.data.model.Pool

    /** Your own post sets require Basic Auth as their creator; public sets are visible to anyone. */
    @GET("post_sets.json")
    suspend fun getPostSets(
        @Query("search[creator_id]") creatorId: Long?,
        @Query("limit") limit: Int = 100,
    ): List<one.proci.e621.data.model.PostSet>

    @GET("post_sets/{id}.json")
    suspend fun getPostSet(@Path("id") id: Long): one.proci.e621.data.model.PostSet

    /** Requires Basic Auth + an e621 member account. */
    @POST("post_sets.json")
    suspend fun createPostSet(
        @Body body: one.proci.e621.data.model.CreatePostSetRequest,
    ): one.proci.e621.data.model.PostSet

    /** Requires Basic Auth as the set's owner/maintainer. Top-level `post_ids` array. */
    @POST("post_sets/{id}/add_posts.json")
    suspend fun addPostsToSet(
        @Path("id") id: Long,
        @Body body: one.proci.e621.data.model.SetPostIdsRequest,
    ): Response<ResponseBody>

    @POST("post_sets/{id}/remove_posts.json")
    suspend fun removePostsFromSet(
        @Path("id") id: Long,
        @Body body: one.proci.e621.data.model.SetPostIdsRequest,
    ): Response<ResponseBody>

    @DELETE("post_sets/{id}.json")
    suspend fun deletePostSet(@Path("id") id: Long): Response<ResponseBody>

    /** Public; a subset of fields (no blacklisted_tags/mail/etc, those are self-only) but includes avatar_id. */
    @GET("users/{id}.json")
    suspend fun getUser(@Path("id") id: Long): UserProfile

    /** Public; users whose name starts with [nameMatches] (already wildcarded, e.g. "fo*") - for `user:`/`fav:` autocomplete. */
    @GET("users.json")
    suspend fun autocompleteUsers(
        @Query("search[name_matches]") nameMatches: String,
        @Query("limit") limit: Int = 10,
    ): List<UserProfile>

    /** Public; pools whose name starts with [nameMatches] - for `pool:` autocomplete. */
    @GET("pools.json")
    suspend fun autocompletePools(
        @Query("search[name_matches]") nameMatches: String,
        @Query("limit") limit: Int = 10,
    ): List<one.proci.e621.data.model.Pool>

    /**
     * e621 responds 204 No Content on success (no body), so this returns the raw Response
     * rather than a typed body - a non-nullable typed return would make Retrofit throw on the
     * empty 204 body even though the update succeeded.
     */
    @PATCH("users/{id}.json")
    suspend fun updateUser(@Path("id") id: Long, @Body body: UpdateUserRequest): Response<ResponseBody>

    /** Voting the same direction again toggles the vote off (server-side), so `our_score` in the response is authoritative. */
    @POST("posts/{id}/votes.json")
    suspend fun vote(@Path("id") postId: Long, @Body body: VoteRequest): VoteResponse

    @POST("favorites.json")
    suspend fun addFavorite(@Body body: FavoriteRequest): FavoriteResponse

    @DELETE("favorites/{postId}.json")
    suspend fun removeFavorite(@Path("postId") postId: Long): FavoriteResponse

    /** Raw response body (rather than a parsed Post) so the app can display the JSON verbatim. */
    @GET("posts/{id}.json")
    suspend fun getPostRawJson(@Path("id") postId: Long): ResponseBody

    /** Public; a flagged post's flag history, most recent first - used to show why it was flagged. */
    @GET("post_flags.json")
    suspend fun getPostFlags(
        @Query("search[post_id]") postId: Long,
        @Query("limit") limit: Int = 1,
    ): List<PostFlag>

    @GET("comments.json")
    suspend fun getComments(@Query("search[post_id]") postId: Long): List<Comment>

    /** Public; a post's tag/rating/source/description edit history, newest first. */
    @GET("post_versions.json")
    suspend fun getPostVersions(
        @Query("search[post_id]") postId: Long,
        @Query("limit") limit: Int = 40,
    ): List<one.proci.e621.data.model.PostVersion>

    /** Public; a post's translation/annotation notes. Empty array (not 404) for a post with none. */
    @GET("notes.json")
    suspend fun getNotes(
        @Query("search[post_id]") postId: Long,
        @Query("limit") limit: Int = 100,
    ): List<one.proci.e621.data.model.PostNote>

    /** Public; a given user's comments across all posts, most recent first. */
    @GET("comments.json")
    suspend fun getCommentsByCreator(
        @Query("search[creator_id]") creatorId: Long,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): List<Comment>

    /** Requires Basic Auth; posting anonymously is rejected server-side. */
    @POST("comments.json")
    suspend fun createComment(@Body body: CreateCommentRequest): Comment

    /**
     * Requires Basic Auth; e621 rejects editing another user's comment server-side. Whether the
     * PATCH returns the updated comment JSON or an empty body is unconfirmed, so this takes the
     * raw Response and the caller patches its local copy from the body it already sent.
     */
    @PATCH("comments/{id}.json")
    suspend fun updateComment(@Path("id") commentId: Long, @Body body: UpdateCommentRequest): Response<ResponseBody>

    /** Requires Basic Auth; own comments only (or moderator). */
    @DELETE("comments/{id}.json")
    suspend fun deleteComment(@Path("id") commentId: Long): Response<ResponseBody>

    /** Shares e621's one voting controller with post votes - same [VoteRequest]/[VoteResponse] shape. */
    @POST("comments/{id}/votes.json")
    suspend fun voteComment(@Path("id") commentId: Long, @Body body: VoteRequest): VoteResponse

    /** Files a moderation report (comment or post - see [CreateTicketRequest]). Requires Basic Auth. */
    @POST("tickets.json")
    suspend fun createTicket(@Body body: CreateTicketRequest): Response<ResponseBody>

    /** Public; a user's feedback ("records") history - see [UserFeedback]. */
    @GET("user_feedbacks.json")
    suspend fun getUserFeedbacks(
        @Query("search[user_id]") userId: Long,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): List<UserFeedback>

    /** `name` is already wildcarded (e.g. "fo*") by the caller; e621 resolves aliases server-side. */
    @GET("tags/autocomplete.json")
    suspend fun autocompleteTags(@Query("search[name_matches]") name: String): List<TagSuggestion>

    /**
     * Tags statistically related to [query] (member-only server-side). The response shape has
     * drifted across e621ng versions, so this returns the raw body for defensive parsing - see
     * [one.proci.e621.data.repository.PostActionsRepository.fetchRelatedTags].
     */
    @GET("related_tag.json")
    suspend fun getRelatedTags(@Query("search[query]") query: String): ResponseBody

    /** Requires Basic Auth. `folder` defaults server-side to the inbox. */
    @GET("dmails.json")
    suspend fun getDmails(
        @Query("folder") folder: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): List<Dmail>

    /** Requires Basic Auth; e621 marks the dmail read as a side effect of this call if you're the owner. */
    @GET("dmails/{id}.json")
    suspend fun getDmail(@Path("id") id: Long): Dmail

    /**
     * Requires Basic Auth. e621ng soft-deletes (`is_deleted = true`) then renders a template-less
     * JSON response, so a success can come back non-2xx just like `createDmail` - the caller only
     * treats 401/403/404 as real failures.
     */
    @DELETE("dmails/{id}.json")
    suspend fun deleteDmail(@Path("id") id: Long): Response<ResponseBody>

    /** Requires Basic Auth; posting anonymously is rejected server-side. */
    @POST("dmails.json")
    suspend fun createDmail(@Body body: CreateDmailRequest): Dmail

    /** Public; no auth required to browse. */
    @GET("forum_topics.json")
    suspend fun getForumTopics(
        @Query("search[title_matches]") titleMatches: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): List<ForumTopic>

    /** Public; used to check is_locked/is_sticky before allowing a reply. */
    @GET("forum_topics/{id}.json")
    suspend fun getForumTopic(@Path("id") id: Long): ForumTopic

    /** Public; no auth required to browse. */
    @GET("forum_posts.json")
    suspend fun getForumPosts(
        @Query("search[topic_id]") topicId: Long,
        @Query("limit") limit: Int = 50,
        @Query("page") page: String? = null,
    ): List<ForumPost>

    /** Requires Basic Auth + an e621 member account; rejected on locked topics or for logged-out requests. */
    @POST("forum_posts.json")
    suspend fun createForumPost(@Body body: CreateForumPostRequest): ForumPost

    /** Public. Exact wiki page by title; empty list when the page doesn't exist. */
    @GET("wiki_pages.json")
    suspend fun getWikiPageByTitle(
        @Query("search[title]") title: String,
        @Query("limit") limit: Int = 1,
    ): List<one.proci.e621.data.model.WikiPage>

    /** Public. Wiki pages whose title matches [titleMatches] (wildcarded by the caller), for the wiki browser. */
    @GET("wiki_pages.json")
    suspend fun searchWikiPages(
        @Query("search[title]") titleMatches: String,
        @Query("limit") limit: Int = 40,
    ): List<one.proci.e621.data.model.WikiPage>

    /** Public. An artist record by exact name. */
    @GET("artists.json")
    suspend fun getArtistByName(
        @Query("search[name]") name: String,
        @Query("limit") limit: Int = 1,
    ): List<one.proci.e621.data.model.Artist>
}
