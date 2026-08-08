package one.proci.e621

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import one.proci.e621.data.api.E621Client
import one.proci.e621.data.api.UserAgentInterceptor
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.repository.ForumRepository
import one.proci.e621.data.repository.MessagesRepository
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.repository.TagSuggestionRepository
import one.proci.e621.data.repository.UserRepository
import one.proci.e621.data.settings.SavedSearchStore
import one.proci.e621.data.settings.UserPreferences

class E621Application : Application(), SingletonImageLoader.Factory {

    val applicationScope = CoroutineScope(SupervisorJob())

    val userPreferences by lazy { UserPreferences(this, applicationScope) }
    private val apiService by lazy { E621Client.create(userPreferences.settingsState) }
    val postRepository by lazy { PostRepository(apiService, userPreferences.settingsState) }
    val userRepository by lazy { UserRepository(apiService) }
    val postActionsRepository by lazy { PostActionsRepository(apiService) }
    val tagSuggestionRepository by lazy { TagSuggestionRepository(apiService) }
    val messagesRepository by lazy { MessagesRepository(apiService) }
    val forumRepository by lazy { ForumRepository(apiService) }
    val avatarRepository by lazy { AvatarRepository(apiService) }
    val savedSearchStore by lazy { SavedSearchStore(this) }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor(userPreferences.settingsState))
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                // Animates GIFs via the platform ImageDecoder (minSdk 34 covers the API 28+
                // requirement). APNG isn't supported here and is handled separately in the
                // media viewer via com.linecorp:apng.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
}
