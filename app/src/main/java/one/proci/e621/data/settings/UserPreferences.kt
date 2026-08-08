package one.proci.e621.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.proci.e621.data.model.Rating

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferences(context: Context, scope: CoroutineScope) {

    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val API_KEY = stringPreferencesKey("api_key")
        val RATING_SAFE = booleanPreferencesKey("rating_safe")
        val RATING_QUESTIONABLE = booleanPreferencesKey("rating_questionable")
        val RATING_EXPLICIT = booleanPreferencesKey("rating_explicit")
        val ADULT_MODE = booleanPreferencesKey("adult_mode_enabled")
        val BLACKLIST = stringPreferencesKey("blacklist")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        // A distinct name from any earlier boolean "eula_accepted" key - DataStore ties a key's
        // type to its name, so reusing the old name with a new (string) type could crash reading
        // stale data written under the old type.
        val EULA_ACCEPTED_HASH = stringPreferencesKey("eula_accepted_hash")
    }

    val settingsFlow = dataStore.data.map { prefs ->
        val ratings = buildSet {
            if (prefs[Keys.RATING_SAFE] != false) add(Rating.SAFE)
            if (prefs[Keys.RATING_QUESTIONABLE] != false) add(Rating.QUESTIONABLE)
            if (prefs[Keys.RATING_EXPLICIT] != false) add(Rating.EXPLICIT)
        }
        UserSettings(
            username = prefs[Keys.USERNAME].orEmpty(),
            apiKey = prefs[Keys.API_KEY].orEmpty(),
            enabledRatings = ratings.ifEmpty { setOf(Rating.SAFE) },
            adultModeEnabled = prefs[Keys.ADULT_MODE] == true,
            blacklist = prefs[Keys.BLACKLIST].orEmpty(),
            accentColor = prefs[Keys.ACCENT_COLOR],
            eulaAcceptedHash = prefs[Keys.EULA_ACCEPTED_HASH],
            isLoaded = true,
        )
    }

    /**
     * Kept in sync for OkHttp interceptors, which run on background dispatcher threads and
     * need synchronous access to the current credentials/User-Agent rather than a suspend read.
     */
    val settingsState: StateFlow<UserSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings(),
    )

    suspend fun updateAccount(username: String, apiKey: String) {
        dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateRatings(ratings: Set<Rating>) {
        dataStore.edit { prefs ->
            prefs[Keys.RATING_SAFE] = Rating.SAFE in ratings
            prefs[Keys.RATING_QUESTIONABLE] = Rating.QUESTIONABLE in ratings
            prefs[Keys.RATING_EXPLICIT] = Rating.EXPLICIT in ratings
        }
    }

    suspend fun setAdultModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ADULT_MODE] = enabled }
    }

    suspend fun updateBlacklist(blacklist: String) {
        dataStore.edit { prefs -> prefs[Keys.BLACKLIST] = blacklist }
    }

    suspend fun updateAccentColor(color: Int?) {
        dataStore.edit { prefs ->
            if (color == null) prefs.remove(Keys.ACCENT_COLOR) else prefs[Keys.ACCENT_COLOR] = color
        }
    }

    /** Pass the hash of the EULA text just agreed to, or null to clear (un-accept). */
    suspend fun setEulaAccepted(hash: String?) {
        dataStore.edit { prefs ->
            if (hash == null) prefs.remove(Keys.EULA_ACCEPTED_HASH) else prefs[Keys.EULA_ACCEPTED_HASH] = hash
        }
    }

    /**
     * Session-only (not persisted) override to show every post regardless of the blacklist.
     * Deliberately in-memory rather than in DataStore: it's meant to be a temporary "show me
     * everything for a moment" switch, not a setting that quietly stays flipped between sessions.
     */
    private val _blacklistDisabled = MutableStateFlow(false)
    val blacklistDisabled: StateFlow<Boolean> = _blacklistDisabled.asStateFlow()

    fun setBlacklistDisabled(disabled: Boolean) {
        _blacklistDisabled.value = disabled
    }
}
