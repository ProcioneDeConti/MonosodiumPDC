package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://e621.net/wiki_pages.json (public). [body] is DText. */
@Serializable
data class WikiPage(
    val id: Long,
    val title: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("category_id") val categoryId: Int? = null,
) {
    val displayTitle: String get() = title.replace('_', ' ')
}
