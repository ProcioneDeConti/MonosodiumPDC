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

/** Outcome of [SettingsViewModel.saveAccount], for the UI to report back to the user. */
sealed class AccountSaveOutcome {
    /** Credentials were blank (e.g. the user is signing out) - saved as-is, no auth check made. */
    data object Saved : AccountSaveOutcome()
    data object Authenticated : AccountSaveOutcome()
    data class AuthFailed(val message: String) : AccountSaveOutcome()
}

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = userPreferences.settingsState

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Persists the given credentials, then verifies them against e621 unless they're blank (signing out). */
    suspend fun saveAccount(username: String, apiKey: String): AccountSaveOutcome {
        userPreferences.updateAccount(username, apiKey)
        if (username.isBlank() || apiKey.isBlank()) return AccountSaveOutcome.Saved
        return try {
            userRepository.fetchProfile(null)
            AccountSaveOutcome.Authenticated
        } catch (e: Exception) {
            AccountSaveOutcome.AuthFailed(e.message ?: e.toString())
        }
    }

    fun setAdultModeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAdultModeEnabled(enabled) }
    }

    fun setUseE6Ai(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setUseE6Ai(enabled) }
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

    fun setImageCacheLimitMb(mb: Int) {
        viewModelScope.launch { userPreferences.setImageCacheLimitMb(mb) }
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
