package one.proci.e621.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.ImageCacheLimits
import one.proci.e621.data.util.VideoPlaybackSpeeds

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferences(context: Context, scope: CoroutineScope) {

    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        // e621's own credentials - kept under their original key names for backward compatibility.
        val USERNAME = stringPreferencesKey("username")
        val API_KEY = stringPreferencesKey("api_key")
        // e6AI is a separate account from e621, so it gets its own credential slot.
        val E6AI_USERNAME = stringPreferencesKey("e6ai_username")
        val E6AI_API_KEY = stringPreferencesKey("e6ai_api_key")
        val USE_E6AI = booleanPreferencesKey("use_e6ai")
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
        val IMAGE_CACHE_LIMIT_MB = intPreferencesKey("image_cache_limit_mb")
        val GRID_THUMBNAIL_SIZE_DP = intPreferencesKey("grid_thumbnail_size_dp")
        val VIDEO_LOOP_ENABLED = booleanPreferencesKey("video_loop_enabled")
        val VIDEO_PLAYBACK_SPEED = floatPreferencesKey("video_playback_speed")
        val VIDEO_AUTOPLAY_ENABLED = booleanPreferencesKey("video_autoplay_enabled")
    }

    val settingsFlow = dataStore.data.map { prefs ->
        val ratings = buildSet {
            if (prefs[Keys.RATING_SAFE] != false) add(Rating.SAFE)
            if (prefs[Keys.RATING_QUESTIONABLE] != false) add(Rating.QUESTIONABLE)
            if (prefs[Keys.RATING_EXPLICIT] != false) add(Rating.EXPLICIT)
        }
        UserSettings(
            useE6Ai = prefs[Keys.USE_E6AI] == true,
            e621Username = prefs[Keys.USERNAME].orEmpty(),
            e621ApiKey = prefs[Keys.API_KEY].orEmpty(),
            e6aiUsername = prefs[Keys.E6AI_USERNAME].orEmpty(),
            e6aiApiKey = prefs[Keys.E6AI_API_KEY].orEmpty(),
            enabledRatings = ratings.ifEmpty { setOf(Rating.SAFE) },
            adultModeEnabled = prefs[Keys.ADULT_MODE] == true,
            blacklist = prefs[Keys.BLACKLIST].orEmpty(),
            accentColor = prefs[Keys.ACCENT_COLOR],
            eulaAcceptedHash = prefs[Keys.EULA_ACCEPTED_HASH],
            imageCacheLimitMb = prefs[Keys.IMAGE_CACHE_LIMIT_MB]?.let { ImageCacheLimits.clamp(it) }
                ?: ImageCacheLimits.DEFAULT_MB,
            gridThumbnailSizeDp = prefs[Keys.GRID_THUMBNAIL_SIZE_DP]?.let { GridThumbnailSize.clamp(it) }
                ?: GridThumbnailSize.DEFAULT_DP,
            videoLoopEnabled = prefs[Keys.VIDEO_LOOP_ENABLED] ?: true,
            videoPlaybackSpeed = prefs[Keys.VIDEO_PLAYBACK_SPEED]?.let { VideoPlaybackSpeeds.clamp(it) }
                ?: VideoPlaybackSpeeds.DEFAULT_SPEED,
            videoAutoplayEnabled = prefs[Keys.VIDEO_AUTOPLAY_ENABLED] ?: true,
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

    /** Saves into whichever site (e621 or e6AI) is currently active - see [UserSettings.useE6Ai]. */
    suspend fun updateAccount(username: String, apiKey: String) {
        val useE6Ai = settingsState.value.useE6Ai
        dataStore.edit { prefs ->
            if (useE6Ai) {
                prefs[Keys.E6AI_USERNAME] = username.trim()
                prefs[Keys.E6AI_API_KEY] = apiKey.trim()
            } else {
                prefs[Keys.USERNAME] = username.trim()
                prefs[Keys.API_KEY] = apiKey.trim()
            }
        }
    }

    suspend fun setUseE6Ai(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.USE_E6AI] = enabled }
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

    suspend fun setImageCacheLimitMb(mb: Int) {
        dataStore.edit { prefs -> prefs[Keys.IMAGE_CACHE_LIMIT_MB] = ImageCacheLimits.clamp(mb) }
    }

    suspend fun setGridThumbnailSizeDp(dp: Int) {
        dataStore.edit { prefs -> prefs[Keys.GRID_THUMBNAIL_SIZE_DP] = GridThumbnailSize.clamp(dp) }
    }

    suspend fun setVideoLoopEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.VIDEO_LOOP_ENABLED] = enabled }
    }

    suspend fun setVideoPlaybackSpeed(speed: Float) {
        dataStore.edit { prefs -> prefs[Keys.VIDEO_PLAYBACK_SPEED] = VideoPlaybackSpeeds.clamp(speed) }
    }

    suspend fun setVideoAutoplayEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.VIDEO_AUTOPLAY_ENABLED] = enabled }
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
