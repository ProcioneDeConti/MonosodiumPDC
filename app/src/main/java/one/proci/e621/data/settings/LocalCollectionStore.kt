package one.proci.e621.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.proci.e621.data.model.LocalCollection

private val Context.localCollectionDataStore by preferencesDataStore(name = "local_collections")

/** On-device post collections - see [LocalCollection]. Same local-only pattern as [SavedSearchStore]. */
class LocalCollectionStore(context: Context) {

    private val dataStore = context.applicationContext.localCollectionDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
    }

    val collectionsFlow: Flow<List<LocalCollection>> = dataStore.data.map { prefs -> prefs.read() }

    private fun androidx.datastore.preferences.core.Preferences.read(): List<LocalCollection> =
        this[Keys.ENTRIES]?.let {
            runCatching { json.decodeFromString<List<LocalCollection>>(it) }.getOrDefault(emptyList())
        }.orEmpty()

    private suspend fun mutate(transform: (List<LocalCollection>) -> List<LocalCollection>) {
        dataStore.edit { prefs -> prefs[Keys.ENTRIES] = json.encodeToString(transform(prefs.read())) }
    }

    suspend fun create(name: String): LocalCollection {
        val entry = LocalCollection(UUID.randomUUID().toString(), name.trim(), emptyList(), System.currentTimeMillis())
        mutate { it + entry }
        return entry
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun rename(id: String, name: String) =
        mutate { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    suspend fun addPost(id: String, postId: Long) = mutate { list ->
        list.map { if (it.id == id && postId !in it.postIds) it.copy(postIds = it.postIds + postId) else it }
    }

    suspend fun removePost(id: String, postId: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(postIds = it.postIds - postId) else it }
    }
}
