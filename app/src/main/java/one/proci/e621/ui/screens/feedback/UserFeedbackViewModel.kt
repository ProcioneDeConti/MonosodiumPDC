package one.proci.e621.ui.screens.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.proci.e621.data.model.UserFeedback
import one.proci.e621.data.repository.UserRepository
import one.proci.e621.data.util.CursorPager

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

    private val pager = CursorPager<UserFeedback>(
        scope = viewModelScope,
        idOf = { it.id },
        fetchInitial = { userRepository.fetchFeedbacks(userId) },
        fetchMore = { cursor -> userRepository.fetchFeedbacks(userId, beforeId = cursor) },
    )

    val uiState: StateFlow<UserFeedbackUiState> = pager.state
        .map { s ->
            UserFeedbackUiState(
                username = initialUsername,
                feedbacks = s.items,
                isRefreshing = s.isRefreshing,
                isLoadingMore = s.isLoadingMore,
                endReached = s.endReached,
                error = s.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserFeedbackUiState(username = initialUsername))

    init {
        refresh()
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun dismissError() = pager.dismissError()
}
