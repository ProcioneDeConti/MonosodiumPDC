package one.proci.e621.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import one.proci.e621.R

/** The four bottom-bar destinations. [route] is null for Browse, whose root is the start search. */
enum class AppTab(
    val route: String?,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    BROWSE(null, R.string.tab_browse, Icons.Filled.Search, Icons.Filled.Search),
    POPULAR(Routes.POPULAR, R.string.tab_popular, Icons.AutoMirrored.Filled.TrendingUp, Icons.AutoMirrored.Filled.TrendingUp),
    FAVORITES(Routes.FAVORITES, R.string.tab_favorites, Icons.Filled.FavoriteBorder, Icons.Filled.Favorite),
    YOU(Routes.YOU, R.string.tab_you, Icons.Outlined.Person, Icons.Filled.Person),
    ;

    companion object {
        /**
         * The tab whose bar should show for a destination route, or null for every other screen
         * (post viewer, settings, ...), which hide the bar. Every search results page counts as
         * Browse, so the bar stays while you hop between tag searches.
         */
        fun forRoute(route: String?): AppTab? = when (route) {
            Routes.SEARCH -> BROWSE
            Routes.POPULAR -> POPULAR
            Routes.FAVORITES -> FAVORITES
            Routes.YOU -> YOU
            else -> null
        }
    }
}

@Composable
fun AppBottomBar(
    current: AppTab,
    youBadgeCount: Int,
    youBadgeDot: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        AppTab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                label = { Text(stringResource(tab.label)) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == AppTab.YOU) {
                                when {
                                    youBadgeCount > 0 -> Badge { Text(if (youBadgeCount > 99) "99+" else youBadgeCount.toString()) }
                                    youBadgeDot -> Badge()
                                }
                            }
                        },
                    ) {
                        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                    }
                },
            )
        }
    }
}
