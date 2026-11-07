package one.proci.e621.ui.screens.recovered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.recovery.RecoveredStore
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.messageOrDefault

data class RecoveredUiState(
    val posts: List<Post> = emptyList(),
    val blacklistedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
)

/**
 * "Recovered posts" (ported from the desktop's RecoveredPanel): every post un-deleted on the current
 * site, most recently recovered first. Ids come from the local cache ([RecoveredStore]); the posts
 * are fetched with `id:a,b,... status:any` in chunks of 100. A copy removed from the cache drops out
 * right away; not-found posts aren't listed.
 */
class RecoveredViewModel(
    private val postRepository: PostRepository,
    private val userPreferences: UserPreferences,
    private val postActionsRepository: PostActionsRepository,
) : ViewModel() {

    private data class Internal(
        val fetched: Map<Long, Post> = emptyMap(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val internal = MutableStateFlow(Internal())
    private var loadJob: Job? = null

    /** Recovered ids of the current site, newest recovery first. */
    private val orderedIds = combine(RecoveredStore.currentSite, RecoveredStore.thumbs) { site, thumbs ->
        thumbs[site].orEmpty().filterValues { it.hasCopy }.entries
            .sortedWith(compareByDescending<Map.Entry<Long, RecoveredStore.Thumb>> { it.value.recoveredAt ?: 0 }.thenByDescending { it.key })
            .map { it.key }
    }.distinctUntilChanged()

    val uiState: StateFlow<RecoveredUiState> = combine(
        internal,
        orderedIds,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, ids, settings, blacklistDisabled ->
        val posts = ids.mapNotNull { s.fetched[it] }
        val blacklistedIds = posts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        RecoveredUiState(
            posts = if (blacklistDisabled) posts else posts.filterNot { it.id in blacklistedIds },
            blacklistedIds = blacklistedIds,
            isLoading = s.isLoading,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecoveredUiState())

    private var started = false

    /**
     * Called when the page opens (the view model lives with the nav graph, so it isn't started at app
     * launch): fetches whatever isn't loaded yet, again whenever the recovered ids change.
     */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            orderedIds.map { it.toSet() }.distinctUntilChanged().collect { ids -> load(ids, refresh = false) }
        }
    }

    private fun load(ids: Set<Long>, refresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val missing = if (refresh) ids.toList() else ids.filter { it !in internal.value.fetched }
            if (missing.isEmpty()) {
                internal.update { it.copy(isLoading = false) }
                return@launch
            }
            internal.update { it.copy(isLoading = true, error = null) }
            try {
                val got = HashMap<Long, Post>()
                for (chunk in missing.chunked(100)) {
                    postRepository.fetchPosts("id:${chunk.joinToString(",")} status:any", limit = 100).forEach { got[it.id] = it }
                }
                internal.update { s -> s.copy(fetched = (if (refresh) emptyMap() else s.fetched) + got, isLoading = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                internal.update { it.copy(isLoading = false, error = e.messageOrDefault()) }
            }
        }
    }

    fun refresh() {
        val site = RecoveredStore.currentSite.value
        load(RecoveredStore.thumbs.value[site].orEmpty().filterValues { it.hasCopy }.keys, refresh = true)
    }

    fun setBlacklistDisabled(disabled: Boolean) = userPreferences.setBlacklistDisabled(disabled)

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    fun updatePost(updated: Post) {
        internal.update { s -> s.copy(fetched = s.fetched + (updated.id to updated)) }
    }

    fun quickUpvote(post: Post) {
        viewModelScope.launch { runCatching { postActionsRepository.vote(post, 1) }.onSuccess(::updatePost) }
    }

    fun quickToggleFavorite(post: Post) {
        viewModelScope.launch {
            runCatching {
                if (post.isFavorited) postActionsRepository.unfavorite(post) else postActionsRepository.favorite(post)
            }.onSuccess(::updatePost)
        }
    }
}
