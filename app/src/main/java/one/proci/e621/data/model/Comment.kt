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
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    /**
     * The authenticated user's own vote on this comment: 1 up, -1 down, 0 none. e621's comment
     * index doesn't always serialize this, so it's defaulted and patched locally from a
     * [VoteResponse] after voting (same as [Post.voteBy]).
     */
    @SerialName("vote_by") val voteBy: Int = 0,
)

@Serializable
data class CreateCommentRequest(val comment: CreateCommentFields)

@Serializable
data class CreateCommentFields(
    @SerialName("post_id") val postId: Long,
    val body: String,
)

@Serializable
data class UpdateCommentRequest(val comment: UpdateCommentFields)

@Serializable
data class UpdateCommentFields(val body: String)

/**
 * `tickets.json` moderation report. Field names inferred from the Danbooru-family ticket
 * convention (`disp_id` = the reported object's id, `qtype` = its type) - not verified live; the
 * desktop app carries the same caveat.
 */
@Serializable
data class CreateTicketRequest(val ticket: CreateTicketFields)

@Serializable
data class CreateTicketFields(
    @SerialName("disp_id") val dispId: Long,
    val qtype: String,
    val reason: String,
)
