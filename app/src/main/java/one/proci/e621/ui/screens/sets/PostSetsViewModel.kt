package one.proci.e621.ui.screens.sets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.data.model.PostSet
import one.proci.e621.data.repository.PostSetRepository
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.messageOrDefault

data class PostSetsUiState(
    val isAuthenticated: Boolean = false,
    val sets: List<PostSet> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * The signed-in user's own post sets - shared by the Post Sets screen and the viewer's
 * "add to set" picker (both need the same list + create action).
 */
class PostSetsViewModel(
    private val repository: PostSetRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostSetsUiState())
    val uiState: StateFlow<PostSetsUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        _uiState.update { it.copy(isAuthenticated = userPreferences.settingsState.value.isAuthenticated) }
        if (_uiState.value.isAuthenticated) refresh()
    }

    fun refresh() {
        if (!userPreferences.settingsState.value.isAuthenticated) {
            _uiState.update { it.copy(isAuthenticated = false, isLoading = false, sets = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticated = true, isLoading = true, error = null) }
            runCatching { repository.fetchMySets() }
                .onSuccess { sets -> loaded = true; _uiState.update { it.copy(sets = sets, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.messageOrDefault()) } }
        }
    }

    fun ensureLoaded() {
        if (!loaded && !_uiState.value.isLoading) refresh()
    }

    fun createSet(name: String, shortname: String, isPublic: Boolean, onResult: (Result<PostSet>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.createSet(name, shortname, isPublic) }
            result.onSuccess { created -> _uiState.update { it.copy(sets = (it.sets + created).sortedBy { s -> s.name.lowercase() }) } }
            onResult(result)
        }
    }

    fun deleteSet(setId: Long, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.deleteSet(setId) }
            result.onSuccess { _uiState.update { it.copy(sets = it.sets.filterNot { s -> s.id == setId }) } }
            onResult(result)
        }
    }

    /** Adds [postId] to [setId]; patches the local set's count on success. */
    fun addPostToSet(setId: Long, postId: Long, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.addPost(setId, postId) }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        sets = it.sets.map { s ->
                            if (s.id == setId && postId !in s.postIds) {
                                s.copy(postIds = s.postIds + postId, postCount = s.postCount + 1)
                            } else {
                                s
                            }
                        },
                    )
                }
            }
            onResult(result)
        }
    }
}
