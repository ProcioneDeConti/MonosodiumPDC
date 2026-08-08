package one.proci.e621.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Rating
import one.proci.e621.data.repository.UserRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.settings.UserSettings

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = userPreferences.settingsState

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun saveAccount(username: String, apiKey: String) {
        viewModelScope.launch { userPreferences.updateAccount(username, apiKey) }
    }

    fun setAdultModeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAdultModeEnabled(enabled) }
    }

    fun setRatingEnabled(rating: Rating, enabled: Boolean) {
        val current = settings.value.enabledRatings
        val updated = if (enabled) current + rating else current - rating
        if (updated.isEmpty()) return
        viewModelScope.launch { userPreferences.updateRatings(updated) }
    }

    fun saveBlacklist(blacklist: String) {
        viewModelScope.launch { userPreferences.updateBlacklist(blacklist) }
    }

    fun setAccentColor(color: Int?) {
        viewModelScope.launch { userPreferences.updateAccentColor(color) }
    }

    /** Pulls the blacklist saved on the user's e621 account and persists it locally. */
    suspend fun importBlacklistFromE621(): Result<String> {
        _isSyncing.value = true
        return try {
            val remote = userRepository.fetchRemoteBlacklist()
            userPreferences.updateBlacklist(remote)
            Result.success(remote)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /** Saves the given blacklist locally, then overwrites the one stored on the user's e621 account. */
    suspend fun pushBlacklistToE621(blacklist: String): Result<Unit> {
        _isSyncing.value = true
        return try {
            userPreferences.updateBlacklist(blacklist)
            userRepository.pushBlacklist(blacklist)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
