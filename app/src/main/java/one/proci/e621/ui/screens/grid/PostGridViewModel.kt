package one.proci.e621.ui.screens.grid

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
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.settings.UserPreferences

data class PostGridUiState(
    val query: String = "",
    val activeQuery: String = "",
    val posts: List<Post> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val username: String = "",
)

class PostGridViewModel(
    private val repository: PostRepository,
    private val userPreferences: UserPreferences,
    initialQuery: String = "",
) : ViewModel() {

    /** Everything actually fetched from the API, unfiltered; blacklist filtering happens reactively below. */
    private data class InternalState(
        val query: String = "",
        val activeQuery: String = "",
        val rawPosts: List<Post> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
    )

    private val internalState = MutableStateFlow(InternalState(query = initialQuery))

    // Recomputes visible posts whenever raw results OR the blacklist/rating settings change,
    // so editing the blacklist in Settings hides matching posts immediately without a refetch.
    val uiState: StateFlow<PostGridUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        PostGridUiState(
            query = s.query,
            activeQuery = s.activeQuery,
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot(settings::isBlacklisted),
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            username = settings.username,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PostGridUiState())

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onQueryChange(query: String) {
        internalState.update { it.copy(query = query) }
    }

    fun setBlacklistDisabled(disabled: Boolean) {
        userPreferences.setBlacklistDisabled(disabled)
    }

    /** Patches a single post (after voting/favoriting) so the grid/pager reflect the change without a refetch. */
    fun updatePost(updated: Post) {
        internalState.update { s ->
            s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it })
        }
    }

    fun refresh() {
        loadJob?.cancel()
        val query = internalState.value.query
        loadJob = viewModelScope.launch {
            internalState.update {
                it.copy(isRefreshing = true, error = null, activeQuery = query, rawPosts = emptyList(), endReached = false)
            }
            runCatching { repository.fetchPosts(tags = query) }
                .onSuccess { raw ->
                    internalState.update { it.copy(rawPosts = raw, isRefreshing = false, endReached = raw.isEmpty()) }
                }
                .onFailure { e ->
                    internalState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) }
                }
        }
    }

    /**
     * Pages forward using the id of the last post actually fetched (not the last visible one),
     * so blacklisted posts don't throw off pagination. If an entire fetched page ends up hidden,
     * keeps fetching (bounded) until something becomes visible or the feed truly ends.
     */
    fun loadMore() {
        val current = internalState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        var cursor = current.rawPosts.lastOrNull()?.id ?: return
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isLoadingMore = true) }
            val settings = userPreferences.settingsState.value
            val blacklistDisabled = userPreferences.blacklistDisabled.value
            val accumulated = mutableListOf<Post>()
            var reachedEnd = false
            var attempts = 0
            try {
                while (attempts < 5) {
                    attempts++
                    val page = repository.fetchPosts(tags = current.activeQuery, beforeId = cursor)
                    if (page.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    cursor = page.last().id
                    accumulated += page
                    if (blacklistDisabled || page.any { !settings.isBlacklisted(it) }) break
                }
                internalState.update {
                    it.copy(rawPosts = it.rawPosts + accumulated, isLoadingMore = false, endReached = reachedEnd)
                }
            } catch (e: Exception) {
                internalState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) }
            }
        }
    }

    fun dismissError() {
        internalState.update { it.copy(error = null) }
    }
}

private fun Throwable.messageOrDefault(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
