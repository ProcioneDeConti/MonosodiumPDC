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
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("linked_user_id") val linkedUserId: Long? = null,
    val notes: String? = null,
    val urls: List<ArtistUrl> = emptyList(),
) {
    val displayName: String get() = name.replace('_', ' ')
}

@Serializable
data class ArtistUrl(
    val url: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

/** https://e621.net/avoid_postings.json (public) - an artist's "do not post" (DNP) record. */
@Serializable
data class ArtistDnp(
    val id: Long,
    val details: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)
