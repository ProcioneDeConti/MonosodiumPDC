package one.proci.e621.ui.screens.sets

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
import one.proci.e621.data.repository.PostSetRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.messageOrDefault

data class PostSetContentUiState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val blacklistedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    val truncated: Boolean = false,
)

/** One post set's posts as a fixed, non-paginated ordered list (mirrors PoolViewModel). */
class PostSetContentViewModel(
    private val setId: Long,
    private val repository: PostSetRepository,
    private val userPreferences: UserPreferences,
    private val postActionsRepository: one.proci.e621.data.repository.PostActionsRepository,
) : ViewModel() {

    private data class InternalState(
        val title: String = "",
        val rawPosts: List<Post> = emptyList(),
        val declaredCount: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val internalState = MutableStateFlow(InternalState())

    val uiState: StateFlow<PostSetContentUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        val blacklistedIds = s.rawPosts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        PostSetContentUiState(
            title = s.title,
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot { it.id in blacklistedIds },
            blacklistedIds = blacklistedIds,
            isLoading = s.isLoading,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
            truncated = s.declaredCount > s.rawPosts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PostSetContentUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            internalState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.fetchSetContent(setId) }
                .onSuccess { content ->
                    internalState.update {
                        it.copy(
                            title = content.set.name,
                            rawPosts = content.posts,
                            declaredCount = content.set.postCount,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e -> internalState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun removePost(postId: Long) {
        viewModelScope.launch {
            runCatching { repository.removePost(setId, postId) }
                .onSuccess {
                    internalState.update { s -> s.copy(rawPosts = s.rawPosts.filterNot { it.id == postId }) }
                }
        }
    }

    fun setBlacklistDisabled(disabled: Boolean) = userPreferences.setBlacklistDisabled(disabled)

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    fun updatePost(updated: Post) {
        internalState.update { s -> s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it }) }
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
