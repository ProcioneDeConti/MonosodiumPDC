package one.proci.e621.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import one.proci.e621.E621Application
import one.proci.e621.ui.screens.favorites.FavoritesViewModel
import one.proci.e621.ui.screens.forum.ForumTopicViewModel
import one.proci.e621.ui.screens.forum.ForumViewModel
import one.proci.e621.ui.screens.grid.PostGridViewModel
import one.proci.e621.ui.screens.messages.MessagesViewModel
import one.proci.e621.ui.screens.profile.ProfileViewModel
import one.proci.e621.ui.screens.savedsearches.SavedSearchesViewModel
import one.proci.e621.ui.screens.settings.SettingsViewModel

class AppViewModelFactory(private val app: E621Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(PostGridViewModel::class.java) ->
            PostGridViewModel(app.postRepository, app.userPreferences) as T
        modelClass.isAssignableFrom(FavoritesViewModel::class.java) ->
            FavoritesViewModel(app.postRepository, app.userPreferences) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app.userPreferences, app.userRepository) as T
        modelClass.isAssignableFrom(MessagesViewModel::class.java) ->
            MessagesViewModel(app.messagesRepository, app.userPreferences) as T
        modelClass.isAssignableFrom(ForumViewModel::class.java) ->
            ForumViewModel(app.forumRepository) as T
        modelClass.isAssignableFrom(SavedSearchesViewModel::class.java) ->
            SavedSearchesViewModel(app.savedSearchStore) as T
        modelClass.isAssignableFrom(NotificationsViewModel::class.java) ->
            NotificationsViewModel(app.userRepository, app.userPreferences) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    /**
     * Each search results screen is its own back-stack entry with its own [PostGridViewModel]
     * instance (rather than one shared/hoisted instance), so that navigating back to an earlier
     * search shows exactly what was there when you left it - like a browser history entry -
     * instead of every "grid" screen on the stack reflecting the same live/mutable state.
     */
    fun searchViewModelFactory(initialQuery: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PostGridViewModel(app.postRepository, app.userPreferences, initialQuery) as T
        }

    /** Same per-back-stack-entry idea as [searchViewModelFactory], for a single forum topic's posts. */
    fun forumTopicViewModelFactory(topicId: Long, initialTitle: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ForumTopicViewModel(topicId, initialTitle, app.forumRepository, app.userPreferences) as T
        }

    /** Same per-back-stack-entry idea as [searchViewModelFactory]; null [userId] means "the signed-in user's own profile." */
    fun profileViewModelFactory(userId: Long?): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(userId, app.userRepository) as T
        }
}
