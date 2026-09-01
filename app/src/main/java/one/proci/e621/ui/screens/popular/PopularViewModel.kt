package one.proci.e621.ui.screens.popular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PopularRepository
import one.proci.e621.data.repository.PopularScale
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.messageOrDefault

data class PopularUiState(
    val scale: PopularScale = PopularScale.DAY,
    val date: LocalDate = LocalDate.now(),
    val atNow: Boolean = true,
    val posts: List<Post> = emptyList(),
    val blacklistedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val blacklistDisabled: Boolean = false,
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
)

class PopularViewModel(
    private val repository: PopularRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private data class InternalState(
        val scale: PopularScale = PopularScale.DAY,
        val date: LocalDate = LocalDate.now(),
        val rawPosts: List<Post> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val internalState = MutableStateFlow(InternalState())
    private var loadJob: Job? = null

    val uiState: StateFlow<PopularUiState> = combine(
        internalState,
        userPreferences.settingsState,
        userPreferences.blacklistDisabled,
    ) { s, settings, blacklistDisabled ->
        val blacklistedIds = s.rawPosts.filter(settings::isBlacklisted).mapTo(mutableSetOf()) { it.id }
        PopularUiState(
            scale = s.scale,
            date = s.date,
            atNow = !s.date.isBefore(LocalDate.now()),
            posts = if (blacklistDisabled) s.rawPosts else s.rawPosts.filterNot { it.id in blacklistedIds },
            blacklistedIds = blacklistedIds,
            isLoading = s.isLoading,
            error = s.error,
            blacklistDisabled = blacklistDisabled,
            gridThumbnailSizeDp = settings.gridThumbnailSizeDp,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PopularUiState())

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val s = internalState.value
        loadJob = viewModelScope.launch {
            internalState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.fetchPopular(s.date, s.scale) }
                .onSuccess { posts -> internalState.update { it.copy(rawPosts = posts, isLoading = false) } }
                .onFailure { e -> internalState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun setScale(scale: PopularScale) {
        if (scale == internalState.value.scale) return
        internalState.update { it.copy(scale = scale) }
        load()
    }

    fun previousPeriod() {
        internalState.update { it.copy(date = it.scale.previous(it.date)) }
        load()
    }

    fun nextPeriod() {
        val s = internalState.value
        val next = s.scale.next(s.date)
        if (next.isAfter(LocalDate.now())) return
        internalState.update { it.copy(date = next) }
        load()
    }

    fun goToNow() {
        if (internalState.value.date == LocalDate.now()) {
            load()
        } else {
            internalState.update { it.copy(date = LocalDate.now()) }
            load()
        }
    }

    fun refresh() = load()

    fun setBlacklistDisabled(disabled: Boolean) = userPreferences.setBlacklistDisabled(disabled)

    fun setGridThumbnailSizeDp(dp: Int) {
        viewModelScope.launch { userPreferences.setGridThumbnailSizeDp(dp) }
    }

    fun updatePost(updated: Post) {
        internalState.update { s -> s.copy(rawPosts = s.rawPosts.map { if (it.id == updated.id) updated else it }) }
    }
}
