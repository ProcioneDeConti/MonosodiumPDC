package one.proci.e621.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.util.messageOrDefault

data class FavoritesAnalysis(
    val total: Int = 0,
    val ratings: Map<String, Int> = emptyMap(),
    val filetypes: Map<String, Int> = emptyMap(),
    val scoreBuckets: Map<String, Int> = emptyMap(),
    val years: Map<String, Int> = emptyMap(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topCharacters: List<Pair<String, Int>> = emptyList(),
)

data class FavoritesAnalysisUiState(
    val username: String = "",
    val running: Boolean = false,
    val fetched: Int = 0,
    val error: String? = null,
    val analysis: FavoritesAnalysis? = null,
)

class FavoritesAnalysisViewModel(
    private val username: String,
    private val repository: PostRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesAnalysisUiState(username = username))
    val uiState: StateFlow<FavoritesAnalysisUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private val collected = mutableListOf<Post>()

    init {
        if (username.isNotBlank()) start()
    }

    fun start() {
        if (_uiState.value.running || username.isBlank()) return
        collected.clear()
        _uiState.update { it.copy(running = true, fetched = 0, error = null, analysis = null) }
        job = viewModelScope.launch {
            // API courtesy: a small gap before the first request.
            delay(START_GAP_MS)
            var page = 1
            try {
                while (isActive && page <= MAX_PAGES) {
                    val batch = repository.fetchPosts(tags = "fav:$username", pageNumber = page, limit = PAGE_SIZE)
                    if (batch.isEmpty()) break
                    collected += batch
                    page++
                    _uiState.update { it.copy(fetched = collected.size) }
                    delay(PAGE_GAP_MS)
                }
                _uiState.update { it.copy(running = false, analysis = rollUp(collected)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(running = false, error = e.messageOrDefault(), analysis = if (collected.isNotEmpty()) rollUp(collected) else null) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.update { it.copy(running = false, analysis = if (collected.isNotEmpty()) rollUp(collected) else it.analysis) }
    }

    override fun onCleared() {
        job?.cancel()
    }

    private fun rollUp(posts: List<Post>): FavoritesAnalysis {
        val ratings = posts.groupingBy {
            when (it.rating) { "s" -> "Safe"; "q" -> "Questionable"; else -> "Explicit" }
        }.eachCount()
        val filetypes = posts.groupingBy { it.extension.uppercase() }.eachCount()
        val scoreBuckets = posts.groupingBy { scoreBucket(it.score.total) }.eachCount()
        val years = posts.mapNotNull { it.createdAt?.take(4) }.groupingBy { it }.eachCount()
        val artists = posts.flatMap { it.tags.artist }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(15).map { it.key to it.value }
        val characters = posts.flatMap { it.tags.character }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(15).map { it.key to it.value }
        return FavoritesAnalysis(
            total = posts.size,
            ratings = ratings,
            filetypes = filetypes.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            scoreBuckets = scoreBuckets,
            years = years.entries.sortedBy { it.key }.associate { it.key to it.value },
            topArtists = artists,
            topCharacters = characters,
        )
    }

    private fun scoreBucket(score: Int): String = when {
        score < 0 -> "< 0"
        score < 50 -> "0–49"
        score < 100 -> "50–99"
        score < 250 -> "100–249"
        score < 500 -> "250–499"
        score < 1000 -> "500–999"
        else -> "1000+"
    }

    private companion object {
        const val PAGE_SIZE = 320
        const val MAX_PAGES = 40
        const val START_GAP_MS = 800L
        const val PAGE_GAP_MS = 350L
    }
}
