package one.proci.e621.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import one.proci.e621.E621Application
import one.proci.e621.ui.AppViewModelFactory
import one.proci.e621.ui.NotificationsViewModel
import one.proci.e621.ui.screens.detail.PostByIdScreen
import one.proci.e621.ui.screens.detail.PostDetailScreen
import one.proci.e621.ui.screens.favorites.FavoritesScreen
import one.proci.e621.ui.screens.favorites.FavoritesViewModel
import one.proci.e621.ui.screens.feedback.UserFeedbackScreen
import one.proci.e621.ui.screens.feedback.UserFeedbackViewModel
import one.proci.e621.ui.screens.forum.ForumScreen
import one.proci.e621.ui.screens.forum.ForumTopicScreen
import one.proci.e621.ui.screens.forum.ForumTopicViewModel
import one.proci.e621.ui.screens.forum.ForumViewModel
import one.proci.e621.ui.screens.grid.PostGridScreen
import one.proci.e621.ui.screens.grid.PostGridViewModel
import one.proci.e621.ui.screens.messages.MessageComposeScreen
import one.proci.e621.ui.screens.messages.MessageDetailScreen
import one.proci.e621.ui.screens.messages.MessagesScreen
import one.proci.e621.ui.screens.messages.MessagesViewModel
import one.proci.e621.ui.screens.collections.LocalCollectionContentScreen
import one.proci.e621.ui.screens.collections.LocalCollectionContentViewModel
import one.proci.e621.ui.screens.collections.LocalCollectionsScreen
import one.proci.e621.ui.screens.collections.LocalCollectionsViewModel
import one.proci.e621.ui.screens.pool.PoolScreen
import one.proci.e621.ui.screens.pool.PoolViewModel
import one.proci.e621.ui.screens.popular.PopularScreen
import one.proci.e621.ui.screens.popular.PopularViewModel
import one.proci.e621.ui.screens.sets.PostSetContentViewModel
import one.proci.e621.ui.screens.sets.PostSetContentScreen
import one.proci.e621.ui.screens.sets.PostSetsScreen
import one.proci.e621.ui.screens.sets.PostSetsViewModel
import one.proci.e621.ui.screens.profile.ProfileScreen
import one.proci.e621.ui.screens.profile.ProfileViewModel
import one.proci.e621.ui.screens.savedsearches.SavedSearchesScreen
import one.proci.e621.ui.screens.savedsearches.SavedSearchesViewModel
import one.proci.e621.ui.screens.settings.SettingsScreen
import one.proci.e621.ui.screens.settings.SettingsViewModel
import one.proci.e621.ui.screens.usercomments.UserCommentsScreen
import one.proci.e621.ui.screens.usercomments.UserCommentsViewModel
import one.proci.e621.ui.screens.wiki.WikiScreen
import one.proci.e621.ui.screens.wiki.WikiViewModel

private object Routes {
    const val SEARCH = "search/{id}/{query}"
    const val FAVORITES = "favorites"
    const val DETAIL = "detail/{source}/{searchId}/{index}"
    const val SETTINGS = "settings"
    const val MESSAGES = "messages"
    const val MESSAGE_DETAIL = "message_detail/{id}"
    const val MESSAGE_COMPOSE = "message_compose?toName={toName}&respondToId={respondToId}&subject={subject}&toEditable={toEditable}"
    const val FORUM = "forum"
    const val FORUM_TOPIC = "forum_topic/{id}/{title}"
    const val SAVED_SEARCHES = "saved_searches/{query}"
    const val PROFILE = "profile?id={id}"
    const val POST_DETAIL = "post_detail/{postId}"
    const val POOL = "pool/{poolId}"
    const val POST_SETS = "post_sets"
    const val POPULAR = "popular"
    const val COLLECTIONS = "local_collections"
    const val WIKI = "wiki"
    const val COLLECTION_CONTENT = "local_collection/{collectionId}"
    const val COLLECTION_DETAIL = "collection_detail/{collectionId}/{index}"
    const val POST_SET_CONTENT = "post_set/{setId}"
    const val USER_FEEDBACK = "user_feedback/{id}/{username}"
    const val USER_COMMENTS = "user_comments/{id}/{username}"

    fun search(id: Int, query: String) = "search/$id/${Uri.encode(query)}"
    fun detail(source: String, searchId: Int, index: Int) = "detail/$source/$searchId/$index"
    fun messageDetail(id: Long) = "message_detail/$id"
    fun messageCompose(toName: String = "", respondToId: Long = -1L, subject: String = "", toEditable: Boolean = true) =
        "message_compose?toName=${Uri.encode(toName)}&respondToId=$respondToId&subject=${Uri.encode(subject)}&toEditable=$toEditable"
    fun forumTopic(id: Long, title: String) = "forum_topic/$id/${Uri.encode(title)}"
    fun savedSearches(query: String) = "saved_searches/${Uri.encode(query)}"
    /** Null [id] means "the signed-in user's own profile" - encoded as -1, since Nav route args can't be nullable. */
    fun profile(id: Long? = null) = "profile?id=${id ?: -1L}"
    fun postDetail(postId: Long) = "post_detail/$postId"
    fun pool(poolId: Long) = "pool/$poolId"
    fun postSetContent(setId: Long) = "post_set/$setId"
    fun collectionContent(id: String) = "local_collection/$id"
    fun collectionDetail(id: String, index: Int) = "collection_detail/$id/$index"
    fun userFeedback(id: Long, username: String) = "user_feedback/$id/${Uri.encode(username)}"
    fun userComments(id: Long, username: String) = "user_comments/$id/${Uri.encode(username)}"
}

private const val SOURCE_SEARCH = "search"
private const val SOURCE_FAVORITES = "favorites"
private const val SOURCE_POOL = "pool"
private const val SOURCE_POST_SET = "post_set"
private const val SOURCE_POPULAR = "popular"
private const val NO_SEARCH_ID = -1

@Composable
fun E621NavGraph(
    pendingDeepLinkPostId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as E621Application
    val factory = remember { AppViewModelFactory(app) }
    val coroutineScope = rememberCoroutineScope()
    // Which site (e621 or e6AI) is currently active - read here rather than per-screen so
    // "open in browser"/"share post link" always point at the right domain.
    val userSettings by app.userPreferences.settingsState.collectAsStateWithLifecycle()
    val activeSite = userSettings.site

    val navController = rememberNavController()
    // Favorites, Settings, Messages, Forum, Saved Searches, and Notifications are all hoisted
    // (one shared instance for the whole app) since there's only ever one meaningful "current"
    // state for each, however you navigated back to them. Search results screens are different -
    // see below.
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val messagesViewModel: MessagesViewModel = viewModel(factory = factory)
    val forumViewModel: ForumViewModel = viewModel(factory = factory)
    val savedSearchesViewModel: SavedSearchesViewModel = viewModel(factory = factory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = factory)
    val postSetsViewModel: PostSetsViewModel = viewModel(factory = factory)
    val popularViewModel: PopularViewModel = viewModel(factory = factory)
    val localCollectionsViewModel: LocalCollectionsViewModel = viewModel(factory = factory)
    val wikiViewModel: WikiViewModel = viewModel(factory = factory)

    // Every search results screen (whether from the search bar or a post's tag menu) gets its own
    // small integer id and its own PostGridViewModel, registered here by id as each one composes.
    // A Detail screen pushed from a given search looks its parent's ViewModel up directly by that
    // id rather than by back-stack position - `previousBackStackEntry`-style positional lookups
    // turned out to be unreliable once several "search/{query}" entries (some sharing the same
    // query text) were interleaved with pushes/pops, occasionally resolving to the wrong search's
    // post list and showing an unrelated post after backing up.
    val nextSearchId = remember { AtomicInteger(0) }
    val searchViewModels = remember { mutableMapOf<Int, PostGridViewModel>() }
    val poolViewModels = remember { mutableMapOf<Long, PoolViewModel>() }
    val postSetContentViewModels = remember { mutableMapOf<Long, PostSetContentViewModel>() }
    val collectionContentViewModels = remember { mutableMapOf<String, LocalCollectionContentViewModel>() }
    val startRoute = remember { Routes.search(nextSearchId.incrementAndGet(), "") }

    // Handles a /posts/{id} link (e621.net, e926.net, e6ai.net) that launched or resumed the
    // app - see MainActivity.postIdFromIntent. Consuming it immediately (rather than waiting on
    // the navigate call to settle) means a config change before the nav transition finishes can't
    // fire it a second time.
    LaunchedEffect(pendingDeepLinkPostId) {
        val postId = pendingDeepLinkPostId ?: return@LaunchedEffect
        onDeepLinkConsumed()
        navController.navigate(Routes.postDetail(postId))
    }

    fun addTagToBlacklist(tag: String) {
        coroutineScope.launch {
            val current = app.userPreferences.settingsState.value.blacklist
            val updated = if (current.isBlank()) tag else "$current\n$tag"
            app.userPreferences.updateBlacklist(updated)
        }
    }

    fun navigateToSearch(query: String) {
        navController.navigate(Routes.search(nextSearchId.incrementAndGet(), query))
    }

    /** Null [id] opens the signed-in user's own profile. */
    fun navigateToProfile(id: Long?) {
        navController.navigate(Routes.profile(id))
    }

    fun navigateToPool(poolId: Long) {
        navController.navigate(Routes.pool(poolId))
    }

    fun navigateToPostSetContent(setId: Long) {
        navController.navigate(Routes.postSetContent(setId))
    }

    fun navigateToCollectionContent(id: String) {
        navController.navigate(Routes.collectionContent(id))
    }

    // Start destination is a search for everything (empty query) - the app's "home page". Every
    // subsequent search pushes a brand new "search/{id}/{query}" entry rather than mutating a
    // shared/hoisted screen, so no matter how many tag-search hops you make, Back always steps
    // back through exactly what you saw, one page at a time - like browser history.
    NavHost(navController = navController, startDestination = startRoute) {
        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("query") { type = NavType.StringType },
            ),
            // Detail's popExitTransition is instant (see below), but AnimatedContent keeps both
            // screens composed until the SLOWER of the two sides finishes - without a matching
            // instant enter here, this screen's default ~300ms fade-in would still govern the
            // whole transition, leaving the exiting Detail screen frozen on top for that entire
            // stretch before it vanishes. That reads as the back button doing nothing for a beat.
            popEnterTransition = {
                if (initialState.destination.route == Routes.DETAIL) EnterTransition.None else null
            },
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            val query = Uri.decode(backStackEntry.arguments?.getString("query") ?: "")
            val searchFactory = remember(backStackEntry) { factory.searchViewModelFactory(query) }
            val searchViewModel: PostGridViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = searchFactory)
            SideEffect { searchViewModels[id] = searchViewModel }
            // searchViewModels is remembered at the graph root, so nothing else ever drops an
            // entry - without this, every search/tag-hop leaks its PostGridViewModel (and its
            // full post list) for the rest of the process's life. ON_DESTROY (not just leaving
            // composition) is the right signal: this entry keeps living on the back stack, and
            // this composable keeps leaving/re-entering composition, whenever Detail is pushed on
            // top of it - only a real pop should evict it.
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) searchViewModels.remove(id)
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            val state by searchViewModel.uiState.collectAsStateWithLifecycle()
            val notifications by notificationsViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { notificationsViewModel.refresh() }
            PostGridScreen(
                state = state,
                onQueryChange = searchViewModel::onQueryChange,
                onSearchSubmit = { newQuery ->
                    if (newQuery != state.activeQuery) {
                        navigateToSearch(newQuery)
                    } else {
                        searchViewModel.refresh()
                    }
                },
                onRefresh = searchViewModel::refresh,
                onLoadMore = searchViewModel::loadMore,
                onDismissError = searchViewModel::dismissError,
                onPostClick = { index -> navController.navigate(Routes.detail(SOURCE_SEARCH, id, index)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenMessages = { navController.navigate(Routes.MESSAGES) },
                onOpenForum = { navController.navigate(Routes.FORUM) },
                onOpenSavedSearches = { currentQuery -> navController.navigate(Routes.savedSearches(currentQuery)) },
                onOpenPostSets = { navController.navigate(Routes.POST_SETS) },
                onOpenPopular = { navController.navigate(Routes.POPULAR) },
                onOpenCollections = { navController.navigate(Routes.COLLECTIONS) },
                onOpenWiki = { navController.navigate(Routes.WIKI) },
                onOpenProfile = { navigateToProfile(null) },
                onSetBlacklistDisabled = searchViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = searchViewModel::setGridThumbnailSizeDp,
                onQuickFavorite = searchViewModel::quickToggleFavorite,
                onQuickUpvote = searchViewModel::quickUpvote,
                onBulkFavorite = searchViewModel::bulkSetFavorite,
                downloadLocationUri = userSettings.downloadLocationUri,
                unreadMessageCount = notifications.unreadMessageCount,
                forumUnread = notifications.forumUnread,
                tagSuggestionRepository = app.tagSuggestionRepository,
                healthCheckRepository = app.healthCheckRepository,
                useE6Ai = userSettings.useE6Ai,
                onSetUseE6Ai = { enabled ->
                    // Awaited (not fire-and-forget) before refreshing - SiteInterceptor reads the
                    // settings StateFlow synchronously per-request, so refreshing before the
                    // DataStore write actually lands would still fetch from the old site.
                    coroutineScope.launch {
                        app.userPreferences.setUseE6Ai(enabled)
                        searchViewModel.refresh()
                    }
                },
            )
        }
        composable(
            route = Routes.FAVORITES,
            // Same reasoning as Routes.SEARCH above - Detail can also be opened from Favorites.
            popEnterTransition = {
                if (initialState.destination.route == Routes.DETAIL) EnterTransition.None else null
            },
        ) {
            val state by favoritesViewModel.uiState.collectAsStateWithLifecycle()
            FavoritesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = favoritesViewModel::refresh,
                onLoadMore = favoritesViewModel::loadMore,
                onDismissError = favoritesViewModel::dismissError,
                onPostClick = { index -> navController.navigate(Routes.detail(SOURCE_FAVORITES, NO_SEARCH_ID, index)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSetBlacklistDisabled = favoritesViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = favoritesViewModel::setGridThumbnailSizeDp,
                onQuickFavorite = favoritesViewModel::quickToggleFavorite,
                onQuickUpvote = favoritesViewModel::quickUpvote,
                onBulkFavorite = favoritesViewModel::bulkSetFavorite,
                bulkProgress = state.bulkProgress,
                downloadLocationUri = userSettings.downloadLocationUri,
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("searchId") { type = NavType.IntType },
                navArgument("index") { type = NavType.IntType },
            ),
            // The default pop transition fades this screen out over the transition's duration -
            // fine for a static image, but a paused video's last frame stays visible throughout,
            // reading as "the video is still there" for a moment after backing out. Popping (and
            // leaving, for symmetry) is instant instead.
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: SOURCE_SEARCH
            val searchId = backStackEntry.arguments?.getInt("searchId") ?: NO_SEARCH_ID
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            when {
                source == SOURCE_POPULAR -> {
                    val state by popularViewModel.uiState.collectAsStateWithLifecycle()
                    PostDetailScreen(
                        posts = state.posts,
                        initialIndex = index,
                        onBack = { navController.popBackStack() },
                        onLoadMore = {},
                        onPostUpdated = popularViewModel::updatePost,
                        postActionsRepository = app.postActionsRepository,
                        postSetRepository = app.postSetRepository,
                        localCollectionStore = app.localCollectionStore,
                        wikiRepository = app.wikiRepository,
                        avatarRepository = app.avatarRepository,
                        onAddTagToBlacklist = ::addTagToBlacklist,
                        onSearchTag = ::navigateToSearch,
                        onAddTagToSearch = ::navigateToSearch,
                        onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                        onOpenProfile = { id -> navigateToProfile(id) },
                        onOpenPool = ::navigateToPool,
                        site = activeSite,
                        videoLoopEnabled = userSettings.videoLoopEnabled,
                        videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                        videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                        downloadLocationUri = userSettings.downloadLocationUri,
                        matchingBlacklistTags = { post ->
                            if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                        },
                    )
                }
                source == SOURCE_FAVORITES -> {
                    val state by favoritesViewModel.uiState.collectAsStateWithLifecycle()
                    PostDetailScreen(
                        posts = state.posts,
                        initialIndex = index,
                        onBack = { navController.popBackStack() },
                        onLoadMore = favoritesViewModel::loadMore,
                        onPostUpdated = favoritesViewModel::updatePost,
                        postActionsRepository = app.postActionsRepository,
                        postSetRepository = app.postSetRepository,
                        localCollectionStore = app.localCollectionStore,
                        wikiRepository = app.wikiRepository,
                        avatarRepository = app.avatarRepository,
                        onAddTagToBlacklist = ::addTagToBlacklist,
                        onSearchTag = ::navigateToSearch,
                        onAddTagToSearch = ::navigateToSearch,
                        onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                        onOpenProfile = { id -> navigateToProfile(id) },
                        onOpenPool = ::navigateToPool,
                        site = activeSite,
                        videoLoopEnabled = userSettings.videoLoopEnabled,
                        videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                        videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                        downloadLocationUri = userSettings.downloadLocationUri,
                        matchingBlacklistTags = { post ->
                            if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                        },
                    )
                }
                source == SOURCE_POST_SET -> {
                    val setViewModel = postSetContentViewModels[searchId.toLong()]
                    if (setViewModel != null) {
                        val state by setViewModel.uiState.collectAsStateWithLifecycle()
                        PostDetailScreen(
                            posts = state.posts,
                            initialIndex = index,
                            onBack = { navController.popBackStack() },
                            onLoadMore = {},
                            onPostUpdated = setViewModel::updatePost,
                            postActionsRepository = app.postActionsRepository,
                            postSetRepository = app.postSetRepository,
                            localCollectionStore = app.localCollectionStore,
                            wikiRepository = app.wikiRepository,
                            avatarRepository = app.avatarRepository,
                            onAddTagToBlacklist = ::addTagToBlacklist,
                            onSearchTag = ::navigateToSearch,
                            onAddTagToSearch = ::navigateToSearch,
                            onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                            onOpenProfile = { id -> navigateToProfile(id) },
                            onOpenPool = ::navigateToPool,
                            site = activeSite,
                            videoLoopEnabled = userSettings.videoLoopEnabled,
                            videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                            videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                            downloadLocationUri = userSettings.downloadLocationUri,
                            matchingBlacklistTags = { post ->
                                if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                            },
                        )
                    }
                }
                source == SOURCE_POOL -> {
                    val poolViewModel = poolViewModels[searchId.toLong()]
                    if (poolViewModel != null) {
                        val state by poolViewModel.uiState.collectAsStateWithLifecycle()
                        PostDetailScreen(
                            posts = state.posts,
                            initialIndex = index,
                            onBack = { navController.popBackStack() },
                            onLoadMore = {},
                            onPostUpdated = poolViewModel::updatePost,
                            postActionsRepository = app.postActionsRepository,
                            postSetRepository = app.postSetRepository,
                            localCollectionStore = app.localCollectionStore,
                            wikiRepository = app.wikiRepository,
                            avatarRepository = app.avatarRepository,
                            onAddTagToBlacklist = ::addTagToBlacklist,
                            onSearchTag = ::navigateToSearch,
                            onAddTagToSearch = ::navigateToSearch,
                            onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                            onOpenProfile = { id -> navigateToProfile(id) },
                            onOpenPool = ::navigateToPool,
                            site = activeSite,
                            videoLoopEnabled = userSettings.videoLoopEnabled,
                            videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                            videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                            downloadLocationUri = userSettings.downloadLocationUri,
                            matchingBlacklistTags = { post ->
                                if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                            },
                        )
                    }
                }
                else -> {
                    val searchViewModel = searchViewModels[searchId]
                    if (searchViewModel != null) {
                        val state by searchViewModel.uiState.collectAsStateWithLifecycle()
                        PostDetailScreen(
                            posts = state.posts,
                            initialIndex = index,
                            onBack = { navController.popBackStack() },
                            onLoadMore = searchViewModel::loadMore,
                            onPostUpdated = searchViewModel::updatePost,
                            postActionsRepository = app.postActionsRepository,
                            postSetRepository = app.postSetRepository,
                            localCollectionStore = app.localCollectionStore,
                            wikiRepository = app.wikiRepository,
                            avatarRepository = app.avatarRepository,
                            onAddTagToBlacklist = ::addTagToBlacklist,
                            onSearchTag = ::navigateToSearch,
                            onAddTagToSearch = { tag -> navigateToSearch("${state.activeQuery} $tag".trim()) },
                            onExcludeTagFromSearch = { tag -> navigateToSearch("${state.activeQuery} -$tag".trim()) },
                            onOpenProfile = { id -> navigateToProfile(id) },
                            onOpenPool = ::navigateToPool,
                            site = activeSite,
                            videoLoopEnabled = userSettings.videoLoopEnabled,
                            videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                            videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                            downloadLocationUri = userSettings.downloadLocationUri,
                            matchingBlacklistTags = { post ->
                                if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                            },
                        )
                    }
                }
            }
        }
        composable(Routes.SETTINGS) {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val isSyncing by settingsViewModel.isSyncing.collectAsStateWithLifecycle()
            val updateCheckStatus by settingsViewModel.updateCheckStatus.collectAsStateWithLifecycle()
            val rateLimitInfo by settingsViewModel.rateLimitInfo.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                isSyncing = isSyncing,
                updateCheckStatus = updateCheckStatus,
                rateLimitInfo = rateLimitInfo,
                onCheckForUpdate = settingsViewModel::checkForUpdate,
                onBack = { navController.popBackStack() },
                onSaveAccount = settingsViewModel::saveAccount,
                onSetAdultModeEnabled = settingsViewModel::setAdultModeEnabled,
                onSetRatingEnabled = settingsViewModel::setRatingEnabled,
                onSaveBlacklist = settingsViewModel::saveBlacklist,
                onImportBlacklist = settingsViewModel::importBlacklistFromE621,
                onPushBlacklist = settingsViewModel::pushBlacklistToE621,
                onTestBlacklist = settingsViewModel::testBlacklist,
                onSetAccentColor = settingsViewModel::setAccentColor,
                onSetThemePreference = settingsViewModel::setThemePreference,
                onSetImageCacheLimitMb = settingsViewModel::setImageCacheLimitMb,
                onSetVideoLoopEnabled = settingsViewModel::setVideoLoopEnabled,
                onSetVideoPlaybackSpeed = settingsViewModel::setVideoPlaybackSpeed,
                onSetVideoAutoplayEnabled = settingsViewModel::setVideoAutoplayEnabled,
                onSetDownloadLocationUri = settingsViewModel::setDownloadLocationUri,
                onSetCloudBackupEnabled = settingsViewModel::setCloudBackupEnabled,
                onExportBackupJson = settingsViewModel::exportBackupJson,
                onIsBackupEncrypted = settingsViewModel::isBackupEncrypted,
                onImportBackup = settingsViewModel::importBackup,
            )
        }
        composable(Routes.MESSAGES) {
            val state by messagesViewModel.uiState.collectAsStateWithLifecycle()
            MessagesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = messagesViewModel::refresh,
                onLoadMore = messagesViewModel::loadMore,
                onOpenDmail = { dmail -> navController.navigate(Routes.messageDetail(dmail.id)) },
                onCompose = { navController.navigate(Routes.messageCompose()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProfile = { id -> navigateToProfile(id) },
                avatarRepository = app.avatarRepository,
            )
        }
        composable(
            route = Routes.MESSAGE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            MessageDetailScreen(
                dmailId = id,
                messagesRepository = app.messagesRepository,
                avatarRepository = app.avatarRepository,
                onBack = { navController.popBackStack() },
                onOpened = messagesViewModel::markReadLocally,
                onDeleted = messagesViewModel::removeLocally,
                onOpenProfile = { id -> navigateToProfile(id) },
                onReply = { dmail ->
                    navController.navigate(
                        Routes.messageCompose(
                            toName = dmail.fromName.orEmpty(),
                            respondToId = dmail.id,
                            subject = "Re: ${dmail.title}",
                            toEditable = false,
                        ),
                    )
                },
            )
        }
        composable(
            route = Routes.MESSAGE_COMPOSE,
            arguments = listOf(
                navArgument("toName") { type = NavType.StringType; defaultValue = "" },
                navArgument("respondToId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                navArgument("toEditable") { type = NavType.BoolType; defaultValue = true },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val toName = Uri.decode(args?.getString("toName").orEmpty())
            val respondToId = args?.getLong("respondToId")?.takeIf { it >= 0 }
            val subject = Uri.decode(args?.getString("subject").orEmpty())
            val toEditable = args?.getBoolean("toEditable") ?: true
            MessageComposeScreen(
                initialToName = toName,
                toEditable = toEditable,
                initialSubject = subject,
                respondToId = respondToId,
                messagesRepository = app.messagesRepository,
                onBack = { navController.popBackStack() },
                onSent = {
                    messagesViewModel.refresh()
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.FORUM) {
            val state by forumViewModel.uiState.collectAsStateWithLifecycle()
            ForumScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = forumViewModel::refresh,
                onLoadMore = forumViewModel::loadMore,
                onOpenTopic = { topic -> navController.navigate(Routes.forumTopic(topic.id, topic.title)) },
            )
        }
        composable(
            route = Routes.FORUM_TOPIC,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
            val topicFactory = remember(backStackEntry) { factory.forumTopicViewModelFactory(id, title) }
            val topicViewModel: ForumTopicViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = topicFactory)
            val state by topicViewModel.uiState.collectAsStateWithLifecycle()
            ForumTopicScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = topicViewModel::refresh,
                onLoadMore = topicViewModel::loadMore,
                onReply = topicViewModel::reply,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProfile = { id -> navigateToProfile(id) },
                avatarRepository = app.avatarRepository,
            )
        }
        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            // Mirrors Routes.SEARCH's reasoning above - Post-by-id (opened from here, via the
            // avatar) also gets an instant popExitTransition to avoid a lingering video frame, so
            // this needs a matching instant enter when returning from it, or the animation-duration
            // mismatch makes backing out of that screen feel laggy.
            popEnterTransition = {
                if (initialState.destination.route == Routes.POST_DETAIL) EnterTransition.None else null
            },
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong("id") ?: -1L
            val userId = rawId.takeIf { it >= 0 }
            val profileFactory = remember(backStackEntry) { factory.profileViewModelFactory(userId) }
            val profileViewModel: ProfileViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = profileFactory)
            val state by profileViewModel.uiState.collectAsStateWithLifecycle()
            ProfileScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = profileViewModel::refresh,
                onOpenPost = { postId -> navController.navigate(Routes.postDetail(postId)) },
                onOpenPosts = { username -> navigateToSearch("user:$username") },
                onOpenFavorites = { username -> navigateToSearch("fav:$username") },
                onOpenComments = { id, username -> navController.navigate(Routes.userComments(id, username)) },
                onOpenFeedback = { id, username -> navController.navigate(Routes.userFeedback(id, username)) },
                avatarRepository = app.avatarRepository,
            )
        }
        composable(
            route = Routes.USER_FEEDBACK,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("username") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val username = Uri.decode(backStackEntry.arguments?.getString("username") ?: "")
            val feedbackFactory = remember(backStackEntry) { factory.userFeedbackViewModelFactory(id, username) }
            val feedbackViewModel: UserFeedbackViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = feedbackFactory)
            val state by feedbackViewModel.uiState.collectAsStateWithLifecycle()
            UserFeedbackScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = feedbackViewModel::refresh,
                onLoadMore = feedbackViewModel::loadMore,
                onOpenProfile = { profileId -> navigateToProfile(profileId) },
                avatarRepository = app.avatarRepository,
            )
        }
        composable(
            route = Routes.USER_COMMENTS,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("username") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val username = Uri.decode(backStackEntry.arguments?.getString("username") ?: "")
            val commentsFactory = remember(backStackEntry) { factory.userCommentsViewModelFactory(id, username) }
            val commentsViewModel: UserCommentsViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = commentsFactory)
            val state by commentsViewModel.uiState.collectAsStateWithLifecycle()
            UserCommentsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = commentsViewModel::refresh,
                onLoadMore = commentsViewModel::loadMore,
                onOpenPost = { postId -> navController.navigate(Routes.postDetail(postId)) },
            )
        }
        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.LongType }),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getLong("postId") ?: 0L
            PostByIdScreen(
                postId = postId,
                postRepository = app.postRepository,
                postActionsRepository = app.postActionsRepository,
                postSetRepository = app.postSetRepository,
                localCollectionStore = app.localCollectionStore,
                wikiRepository = app.wikiRepository,
                avatarRepository = app.avatarRepository,
                onBack = { navController.popBackStack() },
                onAddTagToBlacklist = ::addTagToBlacklist,
                onSearchTag = ::navigateToSearch,
                onAddTagToSearch = ::navigateToSearch,
                onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                onOpenProfile = { id -> navigateToProfile(id) },
                onOpenPool = ::navigateToPool,
                site = activeSite,
                videoLoopEnabled = userSettings.videoLoopEnabled,
                videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                downloadLocationUri = userSettings.downloadLocationUri,
            )
        }
        composable(
            route = Routes.POOL,
            arguments = listOf(navArgument("poolId") { type = NavType.LongType }),
            popEnterTransition = {
                if (initialState.destination.route == Routes.DETAIL) EnterTransition.None else null
            },
        ) { backStackEntry ->
            val poolId = backStackEntry.arguments?.getLong("poolId") ?: 0L
            val poolFactory = remember(backStackEntry) { factory.poolViewModelFactory(poolId) }
            val poolViewModel: PoolViewModel = viewModel(viewModelStoreOwner = backStackEntry, factory = poolFactory)
            SideEffect { poolViewModels[poolId] = poolViewModel }
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) poolViewModels.remove(poolId)
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            val state by poolViewModel.uiState.collectAsStateWithLifecycle()
            PoolScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = poolViewModel::refresh,
                onPostClick = { index -> navController.navigate(Routes.detail(SOURCE_POOL, poolId.toInt(), index)) },
                onSetBlacklistDisabled = poolViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = poolViewModel::setGridThumbnailSizeDp,
            )
        }
        composable(Routes.POPULAR) {
            val state by popularViewModel.uiState.collectAsStateWithLifecycle()
            PopularScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSetScale = popularViewModel::setScale,
                onPrevious = popularViewModel::previousPeriod,
                onNext = popularViewModel::nextPeriod,
                onNow = popularViewModel::goToNow,
                onRefresh = popularViewModel::refresh,
                onPostClick = { index -> navController.navigate(Routes.detail(SOURCE_POPULAR, NO_SEARCH_ID, index)) },
                onSetBlacklistDisabled = popularViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = popularViewModel::setGridThumbnailSizeDp,
            )
        }
        composable(Routes.WIKI) {
            val state by wikiViewModel.uiState.collectAsStateWithLifecycle()
            WikiScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onQueryChange = wikiViewModel::onQueryChange,
                onOpen = wikiViewModel::open,
                onCloseSelected = wikiViewModel::closeSelected,
                onSearchTag = ::navigateToSearch,
                wikiPreview = { title -> app.wikiRepository.fetchPage(title)?.body },
            )
        }
        composable(Routes.COLLECTIONS) {
            val collections by localCollectionsViewModel.collections.collectAsStateWithLifecycle()
            LocalCollectionsScreen(
                collections = collections,
                onBack = { navController.popBackStack() },
                onOpen = ::navigateToCollectionContent,
                onCreate = localCollectionsViewModel::create,
                onDelete = localCollectionsViewModel::delete,
            )
        }
        composable(
            route = Routes.COLLECTION_CONTENT,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
            popEnterTransition = {
                if (initialState.destination.route == Routes.DETAIL) EnterTransition.None else null
            },
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
            val collectionFactory = remember(backStackEntry) { factory.localCollectionContentViewModelFactory(collectionId) }
            val collectionViewModel: LocalCollectionContentViewModel =
                viewModel(viewModelStoreOwner = backStackEntry, factory = collectionFactory)
            SideEffect { collectionContentViewModels[collectionId] = collectionViewModel }
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) collectionContentViewModels.remove(collectionId)
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            val state by collectionViewModel.uiState.collectAsStateWithLifecycle()
            LocalCollectionContentScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = collectionViewModel::refresh,
                onPostClick = { index -> navController.navigate(Routes.collectionDetail(collectionId, index)) },
                onSetBlacklistDisabled = collectionViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = collectionViewModel::setGridThumbnailSizeDp,
            )
        }
        composable(
            route = Routes.COLLECTION_DETAIL,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType },
            ),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val vm = collectionContentViewModels[collectionId]
            if (vm != null) {
                val state by vm.uiState.collectAsStateWithLifecycle()
                PostDetailScreen(
                    posts = state.posts,
                    initialIndex = index,
                    onBack = { navController.popBackStack() },
                    onLoadMore = {},
                    onPostUpdated = vm::updatePost,
                    postActionsRepository = app.postActionsRepository,
                    postSetRepository = app.postSetRepository,
                    localCollectionStore = app.localCollectionStore,
                    wikiRepository = app.wikiRepository,
                    avatarRepository = app.avatarRepository,
                    onAddTagToBlacklist = ::addTagToBlacklist,
                    onSearchTag = ::navigateToSearch,
                    onAddTagToSearch = ::navigateToSearch,
                    onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                    onOpenProfile = { id -> navigateToProfile(id) },
                    onOpenPool = ::navigateToPool,
                    site = activeSite,
                    videoLoopEnabled = userSettings.videoLoopEnabled,
                    videoPlaybackSpeed = userSettings.videoPlaybackSpeed,
                    videoAutoplayEnabled = userSettings.videoAutoplayEnabled,
                    downloadLocationUri = userSettings.downloadLocationUri,
                    matchingBlacklistTags = { post ->
                        if (state.blacklistDisabled) userSettings.matchingBlacklistTags(post) else emptySet()
                    },
                )
            }
        }
        composable(Routes.POST_SETS) {
            val state by postSetsViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { postSetsViewModel.ensureLoaded() }
            PostSetsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = postSetsViewModel::refresh,
                onOpenSet = ::navigateToPostSetContent,
                onCreate = postSetsViewModel::createSet,
                onDelete = postSetsViewModel::deleteSet,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.POST_SET_CONTENT,
            arguments = listOf(navArgument("setId") { type = NavType.LongType }),
            popEnterTransition = {
                if (initialState.destination.route == Routes.DETAIL) EnterTransition.None else null
            },
        ) { backStackEntry ->
            val setId = backStackEntry.arguments?.getLong("setId") ?: 0L
            val setFactory = remember(backStackEntry) { factory.postSetContentViewModelFactory(setId) }
            val setViewModel: PostSetContentViewModel = viewModel(viewModelStoreOwner = backStackEntry, factory = setFactory)
            SideEffect { postSetContentViewModels[setId] = setViewModel }
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) postSetContentViewModels.remove(setId)
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }
            val state by setViewModel.uiState.collectAsStateWithLifecycle()
            PostSetContentScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = setViewModel::refresh,
                onPostClick = { index -> navController.navigate(Routes.detail(SOURCE_POST_SET, setId.toInt(), index)) },
                onSetBlacklistDisabled = setViewModel::setBlacklistDisabled,
                onThumbnailSizeChange = setViewModel::setGridThumbnailSizeDp,
            )
        }
        composable(
            route = Routes.SAVED_SEARCHES,
            arguments = listOf(navArgument("query") { type = NavType.StringType }),
        ) { backStackEntry ->
            val currentQuery = Uri.decode(backStackEntry.arguments?.getString("query") ?: "")
            val savedSearches by savedSearchesViewModel.savedSearches.collectAsStateWithLifecycle()
            SavedSearchesScreen(
                currentQuery = currentQuery,
                savedSearches = savedSearches,
                onBack = { navController.popBackStack() },
                onSave = savedSearchesViewModel::save,
                onApply = ::navigateToSearch,
                onDelete = savedSearchesViewModel::remove,
            )
        }
    }
}
