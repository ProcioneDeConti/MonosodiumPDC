package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: Long,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("creator_id") val creatorId: Long? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    val body: String = "",
    val score: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_hidden") val isHidden: Boolean = false,
)

@Serializable
data class CreateCommentRequest(val comment: CreateCommentFields)

@Serializable
data class CreateCommentFields(
    @SerialName("post_id") val postId: Long,
    val body: String,
)
