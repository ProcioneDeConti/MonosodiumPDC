package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * https://e621.net/pools/<id>.json (public). [postIds] is authoritative for the pool's actual
 * sequence and order; e621 has no "fetch these posts, ordered" endpoint, so the app fetches by
 * `id:` search and re-sorts against this list client-side.
 */
@Serializable
data class Pool(
    val id: Long,
    val name: String = "",
    val description: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val category: String? = null,
    @SerialName("post_ids") val postIds: List<Long> = emptyList(),
    @SerialName("post_count") val postCount: Int = 0,
) {
    /** e621 pool names use underscores for spaces, like tags. */
    val displayName: String get() = name.replace('_', ' ')
}
