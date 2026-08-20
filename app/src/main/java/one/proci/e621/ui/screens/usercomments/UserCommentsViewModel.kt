package one.proci.e621.ui.screens.usercomments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.proci.e621.data.model.Comment
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.util.CursorPager

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

    private val pager = CursorPager<Comment>(
        scope = viewModelScope,
        idOf = { it.id },
        fetchInitial = { postActionsRepository.fetchCommentsByUser(userId) },
        fetchMore = { cursor -> postActionsRepository.fetchCommentsByUser(userId, beforeId = cursor) },
    )

    val uiState: StateFlow<UserCommentsUiState> = pager.state
        .map { s ->
            UserCommentsUiState(
                username = initialUsername,
                comments = s.items,
                isRefreshing = s.isRefreshing,
                isLoadingMore = s.isLoadingMore,
                endReached = s.endReached,
                error = s.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserCommentsUiState(username = initialUsername))

    init {
        refresh()
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun dismissError() = pager.dismissError()
}
