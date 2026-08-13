package one.proci.e621.ui.screens.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.UserFeedback
import one.proci.e621.data.repository.UserRepository

data class UserFeedbackUiState(
    val username: String = "",
    val feedbacks: List<UserFeedback> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/** One instance per back-stack entry, like [one.proci.e621.ui.screens.profile.ProfileViewModel]. */
class UserFeedbackViewModel(
    private val userId: Long,
    initialUsername: String,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserFeedbackUiState(username = initialUsername))
    val uiState: StateFlow<UserFeedbackUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, error = null, feedbacks = emptyList(), endReached = false) }
        viewModelScope.launch {
            runCatching { userRepository.fetchFeedbacks(userId) }
                .onSuccess { raw -> _uiState.update { it.copy(feedbacks = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> _uiState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.feedbacks.lastOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching { userRepository.fetchFeedbacks(userId, beforeId = cursor) }
                .onSuccess { page ->
                    _uiState.update { it.copy(feedbacks = it.feedbacks + page, isLoadingMore = false, endReached = page.isEmpty()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
