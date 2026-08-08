package one.proci.e621.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.proci.e621.data.repository.UserRepository
import one.proci.e621.data.settings.UserPreferences

data class NotificationsUiState(
    val unreadMessageCount: Int = 0,
    val forumUnread: Boolean = false,
)

/** Backs the top bar's Messages/Forum badges - a single shared source rather than each feature polling its own count. */
class NotificationsViewModel(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    fun refresh() {
        if (!userPreferences.settingsState.value.isAuthenticated) {
            _uiState.value = NotificationsUiState()
            return
        }
        viewModelScope.launch {
            runCatching { userRepository.fetchNotificationStatus() }
                .onSuccess { status ->
                    _uiState.value = NotificationsUiState(status.unreadDmailCount, status.forumUnread)
                }
            // Silently ignored on failure - the badges just keep showing their last known state.
        }
    }
}
