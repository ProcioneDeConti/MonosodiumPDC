package one.proci.e621.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.Artist
import one.proci.e621.data.model.ArtistDnp
import one.proci.e621.data.repository.WikiRepository
import one.proci.e621.data.util.messageOrDefault

data class ArtistUiState(
    val name: String,
    val artist: Artist? = null,
    val dnp: ArtistDnp? = null,
    val isLoading: Boolean = true,
    /** True once loading finished and the site has no artist record under [name]. */
    val notFound: Boolean = false,
    val error: String? = null,
)

/** Mirrors desktop's ArtistPanel: the artist record plus its "do not post" status. */
class ArtistViewModel(
    name: String,
    private val repository: WikiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState(name = name))
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val name = _uiState.value.name
        _uiState.update { it.copy(isLoading = true, error = null, notFound = false) }
        viewModelScope.launch {
            runCatching { repository.fetchArtist(name) }
                .onSuccess { artist ->
                    _uiState.update { it.copy(artist = artist, isLoading = false, notFound = artist == null) }
                    // The DNP lookup is a nice-to-have: if it fails, the page still shows.
                    if (artist != null) {
                        val dnp = runCatching { repository.fetchArtistDnp(artist.id) }.getOrNull()
                        _uiState.update { it.copy(dnp = dnp) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }
}
