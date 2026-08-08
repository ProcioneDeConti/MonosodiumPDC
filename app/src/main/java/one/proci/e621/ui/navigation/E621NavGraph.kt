package one.proci.e621.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
import one.proci.e621.ui.screens.profile.ProfileScreen
import one.proci.e621.ui.screens.profile.ProfileViewModel
import one.proci.e621.ui.screens.savedsearches.SavedSearchesScreen
import one.proci.e621.ui.screens.savedsearches.SavedSearchesViewModel
import one.proci.e621.ui.screens.settings.SettingsScreen
import one.proci.e621.ui.screens.settings.SettingsViewModel

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
}

private const val SOURCE_SEARCH = "search"
private const val SOURCE_FAVORITES = "favorites"
private const val NO_SEARCH_ID = -1

@Composable
fun E621NavGraph() {
    val context = LocalContext.current
    val app = context.applicationContext as E621Application
    val factory = remember { AppViewModelFactory(app) }
    val coroutineScope = rememberCoroutineScope()

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

    // Every search results screen (whether from the search bar or a post's tag menu) gets its own
    // small integer id and its own PostGridViewModel, registered here by id as each one composes.
    // A Detail screen pushed from a given search looks its parent's ViewModel up directly by that
    // id rather than by back-stack position - `previousBackStackEntry`-style positional lookups
    // turned out to be unreliable once several "search/{query}" entries (some sharing the same
    // query text) were interleaved with pushes/pops, occasionally resolving to the wrong search's
    // post list and showing an unrelated post after backing up.
    val nextSearchId = remember { AtomicInteger(0) }
    val searchViewModels = remember { mutableMapOf<Int, PostGridViewModel>() }
    val startRoute = remember { Routes.search(nextSearchId.incrementAndGet(), "") }

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
                onOpenProfile = { navigateToProfile(null) },
                onSetBlacklistDisabled = searchViewModel::setBlacklistDisabled,
                unreadMessageCount = notifications.unreadMessageCount,
                forumUnread = notifications.forumUnread,
                tagSuggestionRepository = app.tagSuggestionRepository,
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
            if (source == SOURCE_FAVORITES) {
                val state by favoritesViewModel.uiState.collectAsStateWithLifecycle()
                PostDetailScreen(
                    posts = state.posts,
                    initialIndex = index,
                    onBack = { navController.popBackStack() },
                    onLoadMore = favoritesViewModel::loadMore,
                    onPostUpdated = favoritesViewModel::updatePost,
                    postActionsRepository = app.postActionsRepository,
                    avatarRepository = app.avatarRepository,
                    onAddTagToBlacklist = ::addTagToBlacklist,
                    onSearchTag = ::navigateToSearch,
                    onAddTagToSearch = ::navigateToSearch,
                    onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                    onOpenProfile = { id -> navigateToProfile(id) },
                )
            } else {
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
                        avatarRepository = app.avatarRepository,
                        onAddTagToBlacklist = ::addTagToBlacklist,
                        onSearchTag = ::navigateToSearch,
                        onAddTagToSearch = { tag -> navigateToSearch("${state.activeQuery} $tag".trim()) },
                        onExcludeTagFromSearch = { tag -> navigateToSearch("${state.activeQuery} -$tag".trim()) },
                        onOpenProfile = { id -> navigateToProfile(id) },
                    )
                }
            }
        }
        composable(Routes.SETTINGS) {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val isSyncing by settingsViewModel.isSyncing.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                isSyncing = isSyncing,
                onBack = { navController.popBackStack() },
                onSaveAccount = settingsViewModel::saveAccount,
                onSetAdultModeEnabled = settingsViewModel::setAdultModeEnabled,
                onSetRatingEnabled = settingsViewModel::setRatingEnabled,
                onSaveBlacklist = settingsViewModel::saveBlacklist,
                onImportBlacklist = settingsViewModel::importBlacklistFromE621,
                onPushBlacklist = settingsViewModel::pushBlacklistToE621,
                onSetAccentColor = settingsViewModel::setAccentColor,
                onResetEula = settingsViewModel::resetEulaAcceptance,
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
                avatarRepository = app.avatarRepository,
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
                avatarRepository = app.avatarRepository,
                onBack = { navController.popBackStack() },
                onAddTagToBlacklist = ::addTagToBlacklist,
                onSearchTag = ::navigateToSearch,
                onAddTagToSearch = ::navigateToSearch,
                onExcludeTagFromSearch = { tag -> navigateToSearch("-$tag") },
                onOpenProfile = { id -> navigateToProfile(id) },
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
