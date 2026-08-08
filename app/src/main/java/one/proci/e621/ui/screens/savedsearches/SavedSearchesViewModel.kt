package one.proci.e621.ui.screens.savedsearches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.proci.e621.data.model.SavedSearch
import one.proci.e621.data.settings.SavedSearchStore

class SavedSearchesViewModel(private val store: SavedSearchStore) : ViewModel() {

    val savedSearches: StateFlow<List<SavedSearch>> =
        store.savedSearchesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(label: String, query: String) {
        if (label.isBlank() || query.isBlank()) return
        viewModelScope.launch { store.add(label.trim(), query.trim()) }
    }

    fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }
}
