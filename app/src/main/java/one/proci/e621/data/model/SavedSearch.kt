package one.proci.e621.data.model

import kotlinx.serialization.Serializable

/** Local-only (e621 has no server-side saved-search feature); stored on-device via [one.proci.e621.data.settings.SavedSearchStore]. */
@Serializable
data class SavedSearch(
    val id: String,
    val label: String,
    val query: String,
    val createdAt: Long,
)
