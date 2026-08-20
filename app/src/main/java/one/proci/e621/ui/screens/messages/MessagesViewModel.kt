package one.proci.e621.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Dmail
import one.proci.e621.data.repository.MessagesRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.CursorPager

data class MessagesUiState(
    val isAuthenticated: Boolean = false,
    val dmails: List<Dmail> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class MessagesViewModel(
    private val repository: MessagesRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val pager = CursorPager<Dmail>(
        scope = viewModelScope,
        idOf = { it.id },
        fetchInitial = { repository.fetchInbox() },
        fetchMore = { cursor -> repository.fetchInbox(beforeId = cursor) },
    )

    /** Set once the inbox has been fetched (attempted) for an authenticated user, so the settings collector below doesn't keep re-fetching. */
    private var loaded = false

    val uiState: StateFlow<MessagesUiState> = combine(
        pager.state,
        userPreferences.settingsState,
    ) { s, settings ->
        MessagesUiState(
            isAuthenticated = settings.isAuthenticated,
            dmails = s.items,
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MessagesUiState())

    init {
        viewModelScope.launch {
            userPreferences.settingsState.collect { settings ->
                if (settings.isAuthenticated && !loaded) fetch()
            }
        }
    }

    fun refresh() {
        if (userPreferences.settingsState.value.isAuthenticated) fetch()
    }

    /** Optimistic local update so the inbox row stops looking unread as soon as it's opened. */
    fun markReadLocally(id: Long) {
        pager.updateItems { list -> list.map { if (it.id == id) it.copy(isRead = true) else it } }
    }

    private fun fetch() {
        loaded = true
        pager.refresh()
    }

    fun loadMore() = pager.loadMore()

    fun dismissError() = pager.dismissError()
}
