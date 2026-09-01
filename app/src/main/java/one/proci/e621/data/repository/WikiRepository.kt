package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Artist
import one.proci.e621.data.model.WikiPage

class WikiRepository(private val api: E621ApiService) {

    /** The wiki page for [title] (tags use underscores), or null if it doesn't exist. */
    suspend fun fetchPage(title: String): WikiPage? =
        api.getWikiPageByTitle(title.trim().replace(' ', '_')).firstOrNull()

    /** Wiki pages whose title starts with [query], for the standalone browser. */
    suspend fun search(query: String): List<WikiPage> {
        val q = query.trim().replace(' ', '_')
        if (q.isBlank()) return emptyList()
        return api.searchWikiPages("$q*")
    }

    /** The artist record for [name], or null. */
    suspend fun fetchArtist(name: String): Artist? =
        api.getArtistByName(name.trim().replace(' ', '_')).firstOrNull()
}
