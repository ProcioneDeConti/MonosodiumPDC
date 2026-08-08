package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of https://e621.net/post_flags.json?search[post_id]=<id> - a post's flag history.
 * [reason] is the human-readable description and is always public; the separate `note` field
 * e621 also has isn't modeled here since its visibility depends on server config (staff/uploader/
 * logged-in users only in most configurations).
 */
@Serializable
data class PostFlag(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    val reason: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)
