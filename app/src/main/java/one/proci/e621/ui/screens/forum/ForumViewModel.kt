package one.proci.e621.ui.screens.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.proci.e621.data.model.ForumTopic
import one.proci.e621.data.repository.ForumRepository
import one.proci.e621.data.util.CursorPager

data class ForumUiState(
    val topics: List<ForumTopic> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/** Browsing is public - no e621 account required, unlike Messages. */
class ForumViewModel(private val repository: ForumRepository) : ViewModel() {

    private val pager = CursorPager<ForumTopic>(
        scope = viewModelScope,
        idOf = { it.id },
        fetchInitial = { repository.fetchTopics() },
        fetchMore = { cursor -> repository.fetchTopics(beforeId = cursor) },
    )

    val uiState: StateFlow<ForumUiState> = pager.state
        .map { s ->
            ForumUiState(
                topics = s.items,
                isRefreshing = s.isRefreshing,
                isLoadingMore = s.isLoadingMore,
                endReached = s.endReached,
                error = s.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ForumUiState())

    init {
        refresh()
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun dismissError() = pager.dismissError()
}
