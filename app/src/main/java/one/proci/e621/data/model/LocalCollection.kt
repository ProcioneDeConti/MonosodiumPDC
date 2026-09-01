package one.proci.e621.data.model

import kotlinx.serialization.Serializable

/**
 * A purely client-side, on-device collection of posts - no e621 account, no API, distinct from
 * post sets and favorites. Stored as JSON via [one.proci.e621.data.settings.LocalCollectionStore].
 */
@Serializable
data class LocalCollection(
    val id: String,
    val name: String,
    val postIds: List<Long> = emptyList(),
    val createdAt: Long = 0L,
)
