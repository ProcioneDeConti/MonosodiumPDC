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
import one.proci.e621.data.util.CursorPager

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

    private data class ExtraState(
        val title: String,
        val isLocked: Boolean = false,
        val isReplying: Boolean = false,
    )

    private val pager = CursorPager<ForumPost>(
        scope = viewModelScope,
        idOf = { it.id },
        fetchInitial = { repository.fetchPosts(topicId) },
        fetchMore = { cursor -> repository.fetchPosts(topicId, beforeId = cursor) },
    )
    private val extraState = MutableStateFlow(ExtraState(title = initialTitle))

    val uiState: StateFlow<ForumTopicUiState> = combine(
        pager.state,
        extraState,
        userPreferences.settingsState,
    ) { s, extra, settings ->
        ForumTopicUiState(
            title = extra.title,
            isLocked = extra.isLocked,
            isAuthenticated = settings.isAuthenticated,
            posts = s.items,
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
            isReplying = extra.isReplying,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForumTopicUiState(title = initialTitle))

    init {
        refresh()
        viewModelScope.launch {
            runCatching { repository.fetchTopic(topicId) }
                .onSuccess { topic -> extraState.update { it.copy(title = topic.title, isLocked = topic.isLocked) } }
        }
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun reply(body: String, onDone: (Boolean) -> Unit) {
        if (body.isBlank() || extraState.value.isReplying) return
        extraState.update { it.copy(isReplying = true) }
        viewModelScope.launch {
            runCatching { repository.reply(topicId, body) }
                .onSuccess { created ->
                    pager.updateItems { it + created }
                    extraState.update { it.copy(isReplying = false) }
                    onDone(true)
                }
                .onFailure {
                    extraState.update { it.copy(isReplying = false) }
                    onDone(false)
                }
        }
    }

    fun dismissError() = pager.dismissError()
}
