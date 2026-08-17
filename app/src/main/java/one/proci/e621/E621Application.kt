package one.proci.e621

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import one.proci.e621.data.api.E621Client
import one.proci.e621.data.api.UserAgentInterceptor
import one.proci.e621.data.dtext.DTextLinkConfig
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.repository.ForumRepository
import one.proci.e621.data.repository.HealthCheckRepository
import one.proci.e621.data.repository.MessagesRepository
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.repository.TagSuggestionRepository
import one.proci.e621.data.repository.UserRepository
import one.proci.e621.data.settings.SavedSearchStore
import one.proci.e621.data.settings.UserPreferences
import one.proci.e621.data.util.ImageCacheLimits

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
    val healthCheckRepository by lazy { HealthCheckRepository(apiService) }
    val savedSearchStore by lazy { SavedSearchStore(this) }

    // Deliberately in onCreate(), not init{}: init{} runs during the Application's own
    // construction, before the framework calls attachBaseContext() - these coroutines run on
    // applicationScope's background dispatcher, and if one gets scheduled fast enough on a cold
    // start, it can reach userPreferences (which reads this.applicationContext) while that's
    // still null, crashing with an NPE. onCreate() is guaranteed to run after attachBaseContext().
    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            userPreferences.settingsState
                .map { it.site.webBaseUrl }
                .distinctUntilChanged()
                .collect { DTextLinkConfig.webBaseUrl = it }
        }

        // The Settings screen lets the user resize the image disk cache at runtime. Coil only
        // reads the limit when it (re)builds its singleton ImageLoader, so force a rebuild - on
        // the next image load - whenever the stored limit actually changes. `drop(1)` skips the
        // no-op transition from the StateFlow's eager placeholder default to the first real
        // DataStore read.
        @OptIn(DelicateCoilApi::class)
        applicationScope.launch {
            userPreferences.settingsState
                .map { it.imageCacheLimitMb }
                .distinctUntilChanged()
                .drop(1)
                .collect { SingletonImageLoader.reset() }
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        val imageOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor(userPreferences.settingsState))
            .build()

        val limitMb = userPreferences.settingsState.value.imageCacheLimitMb

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                // Animates GIFs via the platform ImageDecoder (minSdk 34 covers the API 28+
                // requirement). APNG isn't supported here and is handled separately in the
                // media viewer via com.linecorp:apng.
                add(AnimatedImageDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .apply {
                        if (limitMb == ImageCacheLimits.UNLIMITED) {
                            // "Unlimited" still needs *some* ceiling so the cache can't fill the
                            // device's storage outright - grow to most of whatever's free right
                            // now, capped at a generous 50GB no real image cache should reach.
                            maxSizePercent(0.9)
                            minimumMaxSizeBytes(ImageCacheLimits.MAX_MB * 1024L * 1024L)
                            maximumMaxSizeBytes(50L * 1024 * 1024 * 1024)
                        } else {
                            maxSizeBytes(limitMb * 1024L * 1024L)
                        }
                    }
                    .build()
            }
            .build()
    }
}
