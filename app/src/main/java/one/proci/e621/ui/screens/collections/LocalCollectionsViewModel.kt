package one.proci.e621.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.proci.e621.data.model.LocalCollection
import one.proci.e621.data.settings.LocalCollectionStore

/** The on-device collection list - shared by the Collections screen and the "add to collection" picker. */
class LocalCollectionsViewModel(private val store: LocalCollectionStore) : ViewModel() {

    val collections: StateFlow<List<LocalCollection>> =
        store.collectionsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { store.create(name) }
    }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun rename(id: String, name: String) = viewModelScope.launch { store.rename(id, name) }

    fun addPost(collectionId: String, postId: Long) = viewModelScope.launch { store.addPost(collectionId, postId) }

    /** Creates a collection and immediately adds [postId] to it. */
    fun createWithPost(name: String, postId: Long) = viewModelScope.launch {
        val created = store.create(name)
        store.addPost(created.id, postId)
    }
}
