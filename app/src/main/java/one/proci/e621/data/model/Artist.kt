package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://e621.net/artists.json (public) - an artist's canonical name, aliases, and off-site links. */
@Serializable
data class Artist(
    val id: Long,
    val name: String = "",
    @SerialName("other_names") val otherNames: List<String> = emptyList(),
    @SerialName("group_name") val groupName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val urls: List<ArtistUrl> = emptyList(),
) {
    val displayName: String get() = name.replace('_', ' ')
}

@Serializable
data class ArtistUrl(
    val url: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)
