package one.proci.e621.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UsageStats(
    val postsViewed: Int = 0,
    val searches: Int = 0,
    val favoritesAdded: Int = 0,
    val favoritesRemoved: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val downloads: Int = 0,
    val perSitePostsViewed: Map<String, Int> = emptyMap(),
    /** date (YYYY-MM-DD) -> posts viewed that day. */
    val dailyPostsViewed: Map<String, Int> = emptyMap(),
    val topArtists: Map<String, Int> = emptyMap(),
    val topCharacters: Map<String, Int> = emptyMap(),
)

private val Context.usageStatsDataStore by preferencesDataStore(name = "usage_stats")

/**
 * On-device usage analytics for the Dashboard. Nothing leaves the device. Recording is gated on
 * [UserSettings]-style opt-in stored here too (default on, one toggle in Settings).
 */
class UsageStatsStore(context: Context) {

    private val dataStore = context.applicationContext.usageStatsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val STATS = stringPreferencesKey("stats")
        val ENABLED = stringPreferencesKey("enabled")
    }

    val statsFlow: Flow<UsageStats> = dataStore.data.map { prefs ->
        prefs[Keys.STATS]?.let { runCatching { json.decodeFromString<UsageStats>(it) }.getOrNull() } ?: UsageStats()
    }

    val enabledFlow: Flow<Boolean> = dataStore.data.map { it[Keys.ENABLED] != "false" }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled.toString() }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(Keys.STATS) }
    }

    private suspend fun update(transform: (UsageStats) -> UsageStats) {
        dataStore.edit { prefs ->
            if (prefs[Keys.ENABLED] == "false") return@edit
            val current = prefs[Keys.STATS]?.let {
                runCatching { json.decodeFromString<UsageStats>(it) }.getOrNull()
            } ?: UsageStats()
            prefs[Keys.STATS] = json.encodeToString(transform(current))
        }
    }

    suspend fun recordSearch() = update { it.copy(searches = it.searches + 1) }

    suspend fun recordFavorite(added: Boolean) = update {
        if (added) it.copy(favoritesAdded = it.favoritesAdded + 1) else it.copy(favoritesRemoved = it.favoritesRemoved + 1)
    }

    suspend fun recordVote(direction: Int) = update {
        when {
            direction > 0 -> it.copy(upvotes = it.upvotes + 1)
            direction < 0 -> it.copy(downvotes = it.downvotes + 1)
            else -> it
        }
    }

    suspend fun recordDownload() = update { it.copy(downloads = it.downloads + 1) }

    suspend fun recordPostView(site: String, artists: List<String>, characters: List<String>) = update { s ->
        val today = LocalDate.now().toString()
        s.copy(
            postsViewed = s.postsViewed + 1,
            perSitePostsViewed = s.perSitePostsViewed.plusCount(site),
            dailyPostsViewed = s.dailyPostsViewed.plusCount(today),
            topArtists = artists.fold(s.topArtists) { m, a -> m.plusCount(a) },
            topCharacters = characters.fold(s.topCharacters) { m, c -> m.plusCount(c) },
        )
    }

    private fun Map<String, Int>.plusCount(key: String): Map<String, Int> =
        toMutableMap().apply { this[key] = (this[key] ?: 0) + 1 }
}
