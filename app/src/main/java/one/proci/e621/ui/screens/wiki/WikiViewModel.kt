package one.proci.e621.ui.screens.wiki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.WikiPage
import one.proci.e621.data.repository.WikiRepository
import one.proci.e621.data.util.messageOrDefault

data class WikiUiState(
    val query: String = "",
    val results: List<WikiPage> = emptyList(),
    val selected: WikiPage? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class WikiViewModel(private val repository: WikiRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WikiUiState())
    val uiState: StateFlow<WikiUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.search(query) }
                .onSuccess { pages -> _uiState.update { it.copy(results = pages, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun open(page: WikiPage) = _uiState.update { it.copy(selected = page) }

    /** Open a page by title (e.g. from a deep tap) - fetches it if not already in results. */
    fun openByTitle(title: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val page = runCatching { repository.fetchPage(title) }.getOrNull()
            _uiState.update { it.copy(isLoading = false, selected = page) }
        }
    }

    fun closeSelected() = _uiState.update { it.copy(selected = null) }
}
