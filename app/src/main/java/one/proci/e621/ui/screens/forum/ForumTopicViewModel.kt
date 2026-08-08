package one.proci.e621.ui.screens.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.ForumPost
import one.proci.e621.data.repository.ForumRepository
import one.proci.e621.data.settings.UserPreferences

data class ForumTopicUiState(
    val title: String = "",
    val isLocked: Boolean = false,
    val isAuthenticated: Boolean = false,
    val posts: List<ForumPost> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val isReplying: Boolean = false,
)

class ForumTopicViewModel(
    private val topicId: Long,
    initialTitle: String,
    private val repository: ForumRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private data class InternalState(
        val title: String,
        val isLocked: Boolean = false,
        val posts: List<ForumPost> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val isReplying: Boolean = false,
    )

    private val internalState = MutableStateFlow(InternalState(title = initialTitle))

    val uiState: StateFlow<ForumTopicUiState> = combine(
        internalState,
        userPreferences.settingsState,
    ) { s, settings ->
        ForumTopicUiState(
            title = s.title,
            isLocked = s.isLocked,
            isAuthenticated = settings.isAuthenticated,
            posts = s.posts,
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
            isReplying = s.isReplying,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForumTopicUiState(title = initialTitle))

    init {
        refresh()
        viewModelScope.launch {
            runCatching { repository.fetchTopic(topicId) }
                .onSuccess { topic -> internalState.update { it.copy(title = topic.title, isLocked = topic.isLocked) } }
        }
    }

    fun refresh() {
        internalState.update { it.copy(isRefreshing = true, error = null, posts = emptyList(), endReached = false) }
        viewModelScope.launch {
            runCatching { repository.fetchPosts(topicId) }
                .onSuccess { raw -> internalState.update { it.copy(posts = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> internalState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = internalState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.posts.lastOrNull()?.id ?: return
        viewModelScope.launch {
            internalState.update { it.copy(isLoadingMore = true) }
            runCatching { repository.fetchPosts(topicId, beforeId = cursor) }
                .onSuccess { page ->
                    internalState.update { it.copy(posts = it.posts + page, isLoadingMore = false, endReached = page.isEmpty()) }
                }
                .onFailure { e -> internalState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun reply(body: String, onDone: (Boolean) -> Unit) {
        if (body.isBlank() || internalState.value.isReplying) return
        internalState.update { it.copy(isReplying = true) }
        viewModelScope.launch {
            runCatching { repository.reply(topicId, body) }
                .onSuccess { created ->
                    internalState.update { it.copy(posts = it.posts + created, isReplying = false) }
                    onDone(true)
                }
                .onFailure {
                    internalState.update { it.copy(isReplying = false) }
                    onDone(false)
                }
        }
    }

    fun dismissError() {
        internalState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
