package one.proci.e621.ui.screens.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.ForumTopic
import one.proci.e621.data.repository.ForumRepository

data class ForumUiState(
    val topics: List<ForumTopic> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/** Browsing is public - no e621 account required, unlike Messages. */
class ForumViewModel(private val repository: ForumRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, topics = emptyList(), endReached = false) }
            runCatching { repository.fetchTopics() }
                .onSuccess { raw -> _uiState.update { it.copy(topics = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> _uiState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.topics.lastOrNull()?.id ?: return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching { repository.fetchTopics(beforeId = cursor) }
                .onSuccess { page ->
                    _uiState.update { it.copy(topics = it.topics + page, isLoadingMore = false, endReached = page.isEmpty()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
