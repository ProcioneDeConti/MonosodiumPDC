package one.proci.e621.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.proci.e621.data.model.SavedSearch
import java.util.UUID

private val Context.savedSearchDataStore by preferencesDataStore(name = "saved_searches")

/**
 * e621 has no server-side saved-search feature, so this is purely local/on-device, unlike
 * [UserPreferences] which mirrors an e621 account setting.
 */
class SavedSearchStore(context: Context) {

    private val dataStore = context.applicationContext.savedSearchDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
    }

    val savedSearchesFlow: Flow<List<SavedSearch>> = dataStore.data.map { prefs ->
        prefs[Keys.ENTRIES]?.let {
            runCatching { json.decodeFromString<List<SavedSearch>>(it) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun add(label: String, query: String) {
        val entry = SavedSearch(
            id = UUID.randomUUID().toString(),
            label = label,
            query = query,
            createdAt = System.currentTimeMillis(),
        )
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENTRIES]?.let {
                runCatching { json.decodeFromString<List<SavedSearch>>(it) }.getOrDefault(emptyList())
            }.orEmpty()
            prefs[Keys.ENTRIES] = json.encodeToString(current + entry)
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENTRIES]?.let {
                runCatching { json.decodeFromString<List<SavedSearch>>(it) }.getOrDefault(emptyList())
            }.orEmpty()
            prefs[Keys.ENTRIES] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
