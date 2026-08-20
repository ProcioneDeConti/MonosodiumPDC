package one.proci.e621.data.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagerState<T>(
    val items: List<T> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Shared cursor-based ("beforeId") pagination engine for the several screens - Forum, Messages,
 * User Feedback/Comments - that page through a flat list with no blacklist filtering. See
 * [accumulatePostsUntilVisibleOrEnd] for the separate, blacklist-aware variant the post grid and
 * favorites screens need instead, since a blacklisted-only page there must transparently fetch
 * the next one rather than surfacing as "end reached".
 */
class CursorPager<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> Long,
    private val fetchInitial: suspend () -> List<T>,
    private val fetchMore: suspend (cursor: Long) -> List<T>,
) {
    private val _state = MutableStateFlow(PagerState<T>())
    val state: StateFlow<PagerState<T>> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isRefreshing = true, error = null, items = emptyList(), endReached = false) }
            runCatching { fetchInitial() }
                .onSuccess { raw -> _state.update { it.copy(items = raw, isRefreshing = false, endReached = raw.isEmpty()) } }
                .onFailure { e -> _state.update { it.copy(isRefreshing = false, error = e.messageOrDefault()) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || current.isRefreshing || current.endReached) return
        val cursor = current.items.lastOrNull()?.let(idOf) ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            runCatching { fetchMore(cursor) }
                .onSuccess { page -> _state.update { it.copy(items = it.items + page, isLoadingMore = false, endReached = page.isEmpty()) } }
                .onFailure { e -> _state.update { it.copy(isLoadingMore = false, error = e.messageOrDefault()) } }
        }
    }

    fun updateItems(transform: (List<T>) -> List<T>) {
        _state.update { it.copy(items = transform(it.items)) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
