package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * https://e621.net/notes.json?search[post_id]=<id> - a translation/annotation box overlaid on a
 * post's image. [x]/[y]/[width]/[height] are pixel coordinates against the post's *original*
 * full-size image ([Post.file] width/height), regardless of the resolution actually displayed -
 * the viewer scales them to the rendered image rect.
 *
 * View-only; creating/editing notes isn't implemented. Field names follow the Danbooru-family
 * note schema, same confidence caveat as [CreateTicketRequest].
 */
@Serializable
data class PostNote(
    val id: Long,
    @SerialName("post_id") val postId: Long = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val body: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)
