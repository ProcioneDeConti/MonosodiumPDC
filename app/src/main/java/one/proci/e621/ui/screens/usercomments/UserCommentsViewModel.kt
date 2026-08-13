package one.proci.e621.ui.screens.usercomments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Comment
import one.proci.e621.data.repository.PostActionsRepository

data class UserCommentsUiState(
    val username: String = "",
    val comments: List<Comment> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/** One instance per back-stack entry, like [one.proci.e621.ui.screens.profile.ProfileViewModel]. */
class UserCommentsViewModel(
    private val userId: Long,
    initialUsername: String,
    private val postActionsRepository: PostActionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserCommentsUiState(username = initialUsername))
    val uiState: StateFlow<UserCommentsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, error = null, comments = emptyList(), endReached = false) }
        viewModelScope.launch {
            runCatching { postActionsRepository.fetchCommentsByUser(userId) }
                .onSuccess { raw -> _uiState.update { it.copy(comments = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> _uiState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.comments.lastOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching { postActionsRepository.fetchCommentsByUser(userId, beforeId = cursor) }
                .onSuccess { page ->
                    _uiState.update { it.copy(comments = it.comments + page, isLoadingMore = false, endReached = page.isEmpty()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
