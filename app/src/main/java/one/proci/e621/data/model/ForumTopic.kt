package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForumTopic(
    val id: Long,
    val title: String = "",
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("response_count") val responseCount: Int = 0,
    @SerialName("is_sticky") val isSticky: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("creator_id") val creatorId: Long? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
