package one.proci.e621.ui.screens.favorites

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
import one.proci.e621.data.util.GridThumbnailSize

data class FavoritesUiState(
    val username: String = "",
    val posts: List<Post> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
)

/** Favorites are just posts.json tagged fav:<username> — no separate endpoint needed. */
class FavoritesViewModel(
    private val repository: PostRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private data class InternalState(
        val rawPosts: List<Post> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val loadedForUsername: String? = null,
    )

    private val internalState = MutableStateFlow(InternalState())
    private var loadJob: Job? = null

    val uiState: StateFlow<FavoritesUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        FavoritesUiState(
            username = settings.username,
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot(settings::isBlacklisted),
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FavoritesUiState())

    init {
        // Load automatically once a username is configured, and reload if it changes.
        viewModelScope.launch {
            userPreferences.settingsState.collect { settings ->
                if (settings.username.isNotBlank() && settings.username != internalState.value.loadedForUsername) {
                    fetch(settings.username)
                }
            }
        }
    }

    fun refresh() {
        val username = userPreferences.settingsState.value.username
        if (username.isNotBlank()) fetch(username)
    }

    fun setBlacklistDisabled(disabled: Boolean) {
        userPreferences.setBlacklistDisabled(disabled)
    }

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    /** Patches a single post (after voting/favoriting); doesn't remove it from the list on unfavorite mid-browse. */
    fun updatePost(updated: Post) {
        internalState.update { s ->
            s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it })
        }
    }

    private fun fetch(username: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            internalState.update {
                it.copy(isRefreshing = true, error = null, rawPosts = emptyList(), endReached = false, loadedForUsername = username)
            }
            runCatching { repository.fetchPosts(tags = "fav:$username") }
                .onSuccess { raw ->
                    internalState.update { it.copy(rawPosts = raw, isRefreshing = false, endReached = raw.isEmpty()) }
                }
                .onFailure { e ->
                    internalState.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) }
                }
        }
    }

    fun loadMore() {
        val username = userPreferences.settingsState.value.username
        if (username.isBlank()) return
        val current = internalState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        var cursor = current.rawPosts.lastOrNull()?.id ?: return
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isLoadingMore = true) }
            val settings = userPreferences.settingsState.value
            val blacklistDisabled = userPreferences.blacklistDisabled.value
            val seenIds = current.rawPosts.mapTo(mutableSetOf()) { it.id }
            val accumulated = mutableListOf<Post>()
            var reachedEnd = false
            var attempts = 0
            try {
                while (attempts < 5) {
                    attempts++
                    val page = repository.fetchPosts(tags = "fav:$username", beforeId = cursor)
                    if (page.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    cursor = page.last().id
                    val newPosts = page.filter { seenIds.add(it.id) }
                    accumulated += newPosts
                    if (blacklistDisabled || newPosts.any { !settings.isBlacklisted(it) }) break
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
