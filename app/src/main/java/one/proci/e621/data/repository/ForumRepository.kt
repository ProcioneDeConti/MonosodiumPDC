package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.CreateForumPostFields
import one.proci.e621.data.model.CreateForumPostRequest
import one.proci.e621.data.model.ForumPost
import one.proci.e621.data.model.ForumTopic

class ForumRepository(private val api: E621ApiService) {

    /** @param beforeId when set, fetches topics older (by id) than this, for infinite scroll. */
    suspend fun fetchTopics(beforeId: Long? = null, limit: Int = 50): List<ForumTopic> {
        val page = beforeId?.let { "b$it" }
        return api.getForumTopics(limit = limit, page = page)
    }

    suspend fun fetchTopic(topicId: Long): ForumTopic = api.getForumTopic(topicId)

    /** @param beforeId when set, fetches posts older (by id) than this, for infinite scroll. */
    suspend fun fetchPosts(topicId: Long, beforeId: Long? = null, limit: Int = 50): List<ForumPost> {
        val page = beforeId?.let { "b$it" }
        return api.getForumPosts(topicId = topicId, limit = limit, page = page)
    }

    suspend fun reply(topicId: Long, body: String): ForumPost =
        api.createForumPost(CreateForumPostRequest(CreateForumPostFields(topicId, body)))
}
