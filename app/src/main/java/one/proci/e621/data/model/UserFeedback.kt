package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single "record" left on a user's account (e621's `/user_feedbacks`) - a positive, neutral, or
 * negative note left by another user (usually a mod/admin), distinct from the aggregate counts on
 * [UserProfile].
 */
@Serializable
data class UserFeedback(
    val id: Long,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("creator_id") val creatorId: Long? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    val category: String = "neutral",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)
