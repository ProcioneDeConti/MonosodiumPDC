package one.proci.e621.ui.screens.grid

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.settings.SeenMode
import one.proci.e621.data.settings.SeenPostsStore
import one.proci.e621.data.settings.Site
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.BulkProgress
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.accumulatePostsUntilVisibleOrEnd
import one.proci.e621.data.util.messageOrDefault

// Without this, Compose infers this unstable (List<Post>/Set<Long> fields), making every
// composable reading uiState non-skippable - even a settings-screen edit unrelated to the grid
// re-emits a structurally-equal PostGridUiState that would otherwise still force recomposition.
@Immutable
data class PostGridUiState(
    val query: String = "",
    val activeQuery: String = "",
    val posts: List<Post> = emptyList(),
    /** Ids within [posts] that match the blacklist - only non-empty while [blacklistDisabled] is showing them anyway; see PostThumbnail's caution-stripe border. */
    val blacklistedIds: Set<Long> = emptySet(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val username: String = "",
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    val site: Site = Site.E621,
    val bulkProgress: BulkProgress? = null,
    /** Ids to draw dimmed - only non-empty in [SeenMode.DIM]. */
    val seenIds: Set<Long> = emptySet(),
    val seenMode: SeenMode = SeenMode.DIM,
    /** Results exist but every one was hidden as already seen (the "seen it all" empty state). */
    val allSeen: Boolean = false,
)

class PostGridViewModel(
    private val repository: PostRepository,
    private val userPreferences: UserPreferences,
    private val postActionsRepository: PostActionsRepository,
    private val usageStats: one.proci.e621.data.settings.UsageStatsStore? = null,
    initialQuery: String = "",
    private val seenPosts: SeenPostsStore? = null,
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
        val nextPage: Int = 2,
        val bulkProgress: BulkProgress? = null,
        /**
         * Seen ids hidden in [SeenMode.HIDE], snapshotted per search/mode change - filtering
         * against the live set would yank a just-opened post out from under the viewer.
         */
        val hiddenSeen: Set<Long> = emptySet(),
    )

    private val internalState = MutableStateFlow(InternalState(query = initialQuery))

    // Recomputes visible posts whenever raw results OR the blacklist/rating settings change,
    // so editing the blacklist in Settings hides matching posts immediately without a refetch.
    val uiState: StateFlow<PostGridUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
        seenPosts?.seen ?: MutableStateFlow(emptyMap()),
        seenPosts?.mode ?: MutableStateFlow(SeenMode.OFF),
    ) { s, settings, blacklistDisabled, seen, seenMode ->
        val blacklistedIds = s.rawPosts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        val unblacklisted = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot { it.id in blacklistedIds }
        val visible = if (s.hiddenSeen.isEmpty()) unblacklisted else unblacklisted.filterNot { it.id in s.hiddenSeen }
        PostGridUiState(
            query = s.query,
            activeQuery = s.activeQuery,
            posts = visible,
            blacklistedIds = blacklistedIds,
            isRefreshing = s.isRefreshing,
            isLoadingMore = s.isLoadingMore,
            endReached = s.endReached,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            username = settings.username,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
            site = settings.site,
            bulkProgress = s.bulkProgress,
            seenIds = if (seenMode == SeenMode.DIM) seen[settings.site].orEmpty() else emptySet(),
            seenMode = seenMode,
            allSeen = visible.isEmpty() && unblacklisted.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PostGridUiState())

    private var loadJob: Job? = null

    init {
        refresh()
        seenPosts?.let { store ->
            viewModelScope.launch {
                store.mode.drop(1).collect { internalState.update { it.copy(hiddenSeen = seenSnapshot()) } }
            }
        }
    }

    private fun seenSnapshot(): Set<Long> {
        val store = seenPosts ?: return emptySet()
        if (store.mode.value != SeenMode.HIDE) return emptySet()
        return store.seen.value[userPreferences.settingsState.value.site].orEmpty()
    }

    fun setSeenMode(mode: SeenMode) {
        seenPosts?.setMode(mode)
    }

    fun onQueryChange(query: String) {
        internalState.update { it.copy(query = query) }
    }

    fun setBlacklistDisabled(disabled: Boolean) {
        userPreferences.setBlacklistDisabled(disabled)
    }

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    /** Patches a single post (after voting/favoriting) so the grid/pager reflect the change without a refetch. */
    fun updatePost(updated: Post) {
        internalState.update { s ->
            s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it })
        }
    }

    /**
     * Bulk-favorites (or unfavorites) [ids], one request at a time so the rate limiter paces
     * them, publishing N/total progress. [favorite] false = unfavorite.
     */
    fun bulkSetFavorite(ids: Set<Long>, favorite: Boolean) {
        if (ids.isEmpty() || internalState.value.bulkProgress != null) return
        viewModelScope.launch {
            val targets = internalState.value.rawPosts.filter { it.id in ids }
            var done = 0
            var failures = 0
            internalState.update { it.copy(bulkProgress = BulkProgress(0, targets.size)) }
            for (post in targets) {
                runCatching {
                    if (favorite && !post.isFavorited) postActionsRepository.favorite(post)
                    else if (!favorite && post.isFavorited) postActionsRepository.unfavorite(post)
                    else post
                }.onSuccess(::updatePost).onFailure { failures++ }
                done++
                internalState.update { it.copy(bulkProgress = BulkProgress(done, targets.size, failures)) }
            }
            internalState.update { it.copy(bulkProgress = null) }
        }
    }

    fun beginBulkProgress(total: Int) =
        internalState.update { it.copy(bulkProgress = BulkProgress(0, total)) }

    fun updateBulkProgress(done: Int, total: Int, failures: Int) =
        internalState.update { it.copy(bulkProgress = BulkProgress(done, total, failures)) }

    fun endBulkProgress() = internalState.update { it.copy(bulkProgress = null) }

    /** Grid long-press quick action: toggle upvote. */
    fun quickUpvote(post: Post) {
        viewModelScope.launch { runCatching { postActionsRepository.vote(post, 1) }.onSuccess(::updatePost) }
    }

    /** Grid long-press quick action: toggle favorite. */
    fun quickToggleFavorite(post: Post) {
        viewModelScope.launch {
            runCatching {
                if (post.isFavorited) postActionsRepository.unfavorite(post) else postActionsRepository.favorite(post)
            }.onSuccess(::updatePost)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        val query = internalState.value.query
        viewModelScope.launch { runCatching { usageStats?.recordSearch() } }
        loadJob = viewModelScope.launch {
            internalState.update {
                it.copy(
                    isRefreshing = true, error = null, activeQuery = query, rawPosts = emptyList(),
                    endReached = false, nextPage = 2, hiddenSeen = seenSnapshot(),
                )
            }
            runCatching { repository.fetchPosts(tags = query) }
                .onSuccess { raw ->
                    internalState.update { it.copy(rawPosts = raw, isRefreshing = false, endReached = raw.isEmpty()) }
                    // A fully-seen first page: hop forward (loadMore caps itself at a few pages)
                    // rather than showing an empty grid while unseen results may be just beyond.
                    val hidden = internalState.value.hiddenSeen
                    if (raw.isNotEmpty() && hidden.isNotEmpty() && raw.all { it.id in hidden }) {
                        loadJob = null
                        loadMore()
                    }
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
     *
     * A custom `order:` (e.g. order:score_asc) sorts results by something other than id, so the
     * id-based cursor doesn't bound the next page correctly and can re-return already-seen posts -
     * plain page-number pagination is used for those queries instead, capped at e621's hard limit
     * of page 750 (requesting beyond it is a server error, so we surface it as "end reached").
     * Either way, results are de-duplicated by id before being appended, since Compose's grid keys
     * items by post id and throws on a duplicate key.
     */
    fun loadMore() {
        val current = internalState.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val useCustomOrder = current.activeQuery.contains("order:", ignoreCase = true)
        var cursor = current.rawPosts.lastOrNull()?.id ?: return
        var pageNumber = current.nextPage
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isLoadingMore = true) }
            val settings = userPreferences.settingsState.value
            val blacklistDisabled = userPreferences.blacklistDisabled.value
            val seenIds = current.rawPosts.mapTo(mutableSetOf()) { it.id }
            try {
                val (accumulated, reachedEnd) = accumulatePostsUntilVisibleOrEnd(
                    seenIds = seenIds,
                    blacklistDisabled = false,
                    isBlacklisted = { post ->
                        (!blacklistDisabled && settings.isBlacklisted(post)) || post.id in current.hiddenSeen
                    },
                ) {
                    val page = if (useCustomOrder) {
                        if (pageNumber > MAX_NUMBERED_PAGE) emptyList()
                        else repository.fetchPosts(tags = current.activeQuery, pageNumber = pageNumber)
                    } else {
                        repository.fetchPosts(tags = current.activeQuery, beforeId = cursor)
                    }
                    if (page.isNotEmpty()) {
                        pageNumber++
                        cursor = page.last().id
                    }
                    page
                }
                internalState.update {
                    it.copy(
                        rawPosts = it.rawPosts + accumulated,
                        isLoadingMore = false,
                        endReached = reachedEnd,
                        nextPage = pageNumber,
                    )
                }
            } catch (e: Exception) {
                internalState.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) }
            }
        }
    }

    fun dismissError() {
        internalState.update { it.copy(error = null) }
    }

    private companion object {
        /** e621's hard ceiling on numbered pagination; requesting a higher page is a server error. */
        const val MAX_NUMBERED_PAGE = 750
    }
}
