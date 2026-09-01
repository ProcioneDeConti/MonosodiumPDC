package one.proci.e621.ui.screens.pool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PoolRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.messageOrDefault

data class PoolUiState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val blacklistedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    val truncated: Boolean = false,
)

/** A pool's posts as a fixed, non-paginated ordered list - see [PoolRepository]. */
class PoolViewModel(
    private val poolId: Long,
    private val repository: PoolRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private data class InternalState(
        val title: String = "",
        val rawPosts: List<Post> = emptyList(),
        val declaredCount: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val internalState = MutableStateFlow(InternalState())

    val uiState: StateFlow<PoolUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        val blacklistedIds = s.rawPosts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        PoolUiState(
            title = s.title,
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot { it.id in blacklistedIds },
            blacklistedIds = blacklistedIds,
            isLoading = s.isLoading,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
            truncated = s.declaredCount > s.rawPosts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PoolUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            internalState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.fetchPoolContent(poolId) }
                .onSuccess { content ->
                    internalState.update {
                        it.copy(
                            title = content.pool.displayName,
                            rawPosts = content.posts,
                            declaredCount = content.pool.postCount,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e -> internalState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun setBlacklistDisabled(disabled: Boolean) = userPreferences.setBlacklistDisabled(disabled)

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    fun updatePost(updated: Post) {
        internalState.update { s -> s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it }) }
    }
}
