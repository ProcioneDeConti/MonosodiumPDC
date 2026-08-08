package one.proci.e621.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.UserProfile
import one.proci.e621.data.repository.UserRepository

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * One instance per back-stack entry (like [one.proci.e621.ui.screens.forum.ForumTopicViewModel]),
 * since a viewed profile is tied to whichever id was navigated to rather than being shared/hoisted
 * app-wide state. Null [userId] means "the signed-in user's own profile."
 */
class ProfileViewModel(
    private val userId: Long?,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { userRepository.fetchProfile(userId) }
                .onSuccess { profile -> _uiState.update { it.copy(profile = profile, isLoading = false) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message?.takeIf { m -> m.isNotBlank() } ?: "Something went wrong") }
                }
        }
    }
}
