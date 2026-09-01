package one.proci.e621.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.settings.LocalCollectionStore
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.messageOrDefault

data class LocalCollectionContentUiState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val blacklistedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    val truncated: Boolean = false,
)

class LocalCollectionContentViewModel(
    private val collectionId: String,
    private val store: LocalCollectionStore,
    private val postRepository: PostRepository,
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

    val uiState: StateFlow<LocalCollectionContentUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        val blacklistedIds = s.rawPosts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        LocalCollectionContentUiState(
            title = s.title,
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot { it.id in blacklistedIds },
            blacklistedIds = blacklistedIds,
            isLoading = s.isLoading,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
            truncated = s.declaredCount > s.rawPosts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalCollectionContentUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            internalState.update { it.copy(isLoading = true, error = null) }
            val collection = store.collectionsFlow.first().firstOrNull { it.id == collectionId }
            if (collection == null) {
                internalState.update { it.copy(isLoading = false, error = "Collection not found") }
                return@launch
            }
            val ids = collection.postIds.take(320)
            internalState.update { it.copy(title = collection.name, declaredCount = collection.postIds.size) }
            if (ids.isEmpty()) {
                internalState.update { it.copy(rawPosts = emptyList(), isLoading = false) }
                return@launch
            }
            val order = ids.withIndex().associate { (i, id) -> id to i }
            runCatching { postRepository.fetchPosts(tags = "id:${ids.joinToString(",")}", limit = ids.size) }
                .onSuccess { posts ->
                    internalState.update {
                        it.copy(rawPosts = posts.sortedBy { p -> order[p.id] ?: Int.MAX_VALUE }, isLoading = false)
                    }
                }
                .onFailure { e -> internalState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun removePost(postId: Long) {
        viewModelScope.launch {
            store.removePost(collectionId, postId)
            internalState.update { s -> s.copy(rawPosts = s.rawPosts.filterNot { it.id == postId }) }
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
