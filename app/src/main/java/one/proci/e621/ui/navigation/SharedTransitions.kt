package one.proci.e621.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable

/**
 * Grid -> post viewer shared-element plumbing. The NavHost sits inside a SharedTransitionLayout
 * (see E621NavGraph), and each destination provides its own AnimatedContentScope, so any
 * thumbnail and the viewer page for the same post can morph into each other - including while a
 * predictive back gesture scrubs the transition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Whether the post viewer is currently showing a video. Videos don't take part in the shared
 * element (a video surface can't be drawn into the transition overlay) and keep the old instant
 * pop instead, so a paused frame never lingers on screen. Read by the nav transitions.
 */
object DetailTransition {
    var showingVideo by mutableStateOf(false)
}

/** Marks this element as the shared "post-[postId]" bounds; a no-op outside a nav destination. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.postSharedBounds(postId: Long, enabled: Boolean = true): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (!enabled || shared == null || animated == null) return this
    return with(shared) {
        this@postSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState("post-$postId"),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** [composable] that also exposes its AnimatedContentScope via [LocalNavAnimatedScope]. */
fun androidx.navigation.NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    enterTransition: (androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition?)? = null,
    exitTransition: (androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition?)? = null,
    popEnterTransition: (androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition?)? = enterTransition,
    popExitTransition: (androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition?)? = exitTransition,
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
    ) { entry ->
        androidx.compose.runtime.CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            content(entry)
        }
    }
}
