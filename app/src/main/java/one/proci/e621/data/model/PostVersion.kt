package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry from https://e621.net/post_versions.json?search[post_id]=<id> (public), newest first. */
@Serializable
data class PostVersion(
    val id: Long,
    val version: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("added_tags") val addedTags: List<String> = emptyList(),
    @SerialName("removed_tags") val removedTags: List<String> = emptyList(),
    val rating: String = "",
    @SerialName("rating_changed") val ratingChanged: Boolean = false,
    @SerialName("parent_changed") val parentChanged: Boolean = false,
    @SerialName("source_changed") val sourceChanged: Boolean = false,
    @SerialName("description_changed") val descriptionChanged: Boolean = false,
    val reason: String? = null,
    @SerialName("updater_id") val updaterId: Long? = null,
    @SerialName("updater_name") val updaterName: String? = null,
)
