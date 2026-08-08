package one.proci.e621.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Dmail
import one.proci.e621.data.repository.MessagesRepository
import one.proci.e621.data.settings.UserPreferences

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

    private data class InternalState(
        val dmails: List<Dmail> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val loaded: Boolean = false,
    )

    private val internalState = MutableStateFlow(InternalState())
    private var loadJob: Job? = null

    val uiState: StateFlow<MessagesUiState> = combine(
        internalState,
        userPreferences.settingsState,
    ) { s, settings ->
        MessagesUiState(
            isAuthenticated = settings.isAuthenticated,
            dmails = s.dmails,
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MessagesUiState())

    init {
        viewModelScope.launch {
            userPreferences.settingsState.collect { settings ->
                if (settings.isAuthenticated && !internalState.value.loaded) fetch()
            }
        }
    }

    fun refresh() {
        if (userPreferences.settingsState.value.isAuthenticated) fetch()
    }

    /** Optimistic local update so the inbox row stops looking unread as soon as it's opened. */
    fun markReadLocally(id: Long) {
        internalState.update { s ->
            s.copy(dmails = s.dmails.map { if (it.id == id) it.copy(isRead = true) else it })
        }
    }

    private fun fetch() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isRefreshing = true, error = null, dmails = emptyList(), endReached = false, loaded = true) }
            runCatching { repository.fetchInbox() }
                .onSuccess { raw -> internalState.update { it.copy(dmails = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> internalState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = internalState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.dmails.lastOrNull()?.id ?: return
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isLoadingMore = true) }
            runCatching { repository.fetchInbox(beforeId = cursor) }
                .onSuccess { page ->
                    internalState.update {
                        it.copy(dmails = it.dmails + page, isLoadingMore = false, endReached = page.isEmpty())
                    }
                }
                .onFailure { e -> internalState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun dismissError() {
        internalState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
