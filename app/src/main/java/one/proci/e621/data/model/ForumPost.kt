package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForumPost(
    val id: Long,
    @SerialName("topic_id") val topicId: Long = 0,
    val body: String = "",
    @SerialName("creator_id") val creatorId: Long? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateForumPostRequest(@SerialName("forum_post") val forumPost: CreateForumPostFields)

@Serializable
data class CreateForumPostFields(
    @SerialName("topic_id") val topicId: Long,
    val body: String,
)
