package one.proci.e621.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.repository.HealthCheckRepository
import one.proci.e621.data.repository.HealthStatus
import one.proci.e621.data.settings.Site
import one.proci.e621.data.util.loadGreetings
import one.proci.e621.ui.theme.CardShape
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingQuestionable
import one.proci.e621.ui.theme.RatingSafe

/**
 * The "You" tab: everything that used to live in the grid's slide-out drawer - the greeting,
 * account/community shortcuts as tiles, your library, tools, and the site switch + health check.
 */
@Composable
fun YouScreen(
    username: String,
    site: Site,
    unreadMessageCount: Int,
    forumUnread: Boolean,
    healthCheckRepository: HealthCheckRepository,
    useE6Ai: Boolean,
    e6aiPermanentlyDisabled: Boolean,
    onSetUseE6Ai: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenForum: () -> Unit,
    onOpenSavedSearches: () -> Unit,
    onOpenPostSets: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenWiki: () -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Picked once per visit to the tab, same as the drawer picked one per opening.
    val greeting = remember { loadGreetings(context).randomOrNull() }

    var healthStatus by remember { mutableStateOf<HealthStatus>(HealthStatus.Checking) }
    val healthScope = rememberCoroutineScope()
    fun recheckHealth() {
        healthScope.launch {
            healthStatus = HealthStatus.Checking
            healthStatus = healthCheckRepository.check()
        }
    }
    // Re-checked whenever the tab opens or the site changes, so it always reflects "just now".
    LaunchedEffect(site) { recheckHealth() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Greeting header; tapping your name opens your profile.
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                text = if (greeting != null) "$greeting," else stringResource(R.string.you_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                // Always left-aligned so the greeting hugs the same edge in any language.
                textAlign = TextAlign.Left,
                modifier = Modifier.fillMaxWidth(),
            )
            if (username.isNotBlank()) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile),
                )
            }
        }

        TileGroup(title = stringResource(R.string.you_section_account)) {
            TileRow {
                Tile(Icons.Filled.AccountCircle, stringResource(R.string.profile_title), onOpenProfile, Modifier.weight(1f))
                Tile(
                    Icons.Filled.Email, stringResource(R.string.messages_title), onOpenMessages, Modifier.weight(1f),
                    badgeCount = unreadMessageCount,
                )
                Tile(
                    Icons.Filled.Forum, stringResource(R.string.forum_title), onOpenForum, Modifier.weight(1f),
                    badgeDot = forumUnread,
                )
            }
        }

        TileGroup(title = stringResource(R.string.you_section_library)) {
            TileRow {
                Tile(Icons.Filled.Bookmark, stringResource(R.string.saved_searches_title), onOpenSavedSearches, Modifier.weight(1f))
                Tile(Icons.Filled.PhotoLibrary, stringResource(R.string.post_sets_title), onOpenPostSets, Modifier.weight(1f))
                Tile(Icons.Filled.Collections, stringResource(R.string.collections_title), onOpenCollections, Modifier.weight(1f))
            }
            TileRow {
                Tile(Icons.Filled.Download, stringResource(R.string.downloads_title), onOpenDownloads, Modifier.weight(1f))
                Tile(Icons.Filled.Insights, stringResource(R.string.dashboard_title), onOpenDashboard, Modifier.weight(1f))
                Tile(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.wiki_title), onOpenWiki, Modifier.weight(1f))
            }
        }

        TileGroup(title = stringResource(R.string.you_section_more)) {
            Column(
                Modifier
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                ListRow(Icons.Filled.Tune, stringResource(R.string.advanced_search_title), onOpenAdvancedSearch)
                ListRow(Icons.Filled.Settings, stringResource(R.string.action_settings), onOpenSettings)
            }
        }

        Column(
            Modifier
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (!e6aiPermanentlyDisabled) {
                SiteSwitchRow(useE6Ai = useE6Ai, onSetUseE6Ai = onSetUseE6Ai)
            }
            HealthCheckRow(site = site, status = healthStatus, onRecheck = ::recheckHealth)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TileGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        content()
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    badgeDot: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BadgedBox(
            badge = {
                when {
                    badgeCount > 0 -> Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                    badgeDot -> Badge()
                }
            },
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        // One line on every tile so a long label ("Saved searches") can't make its tile - and
        // its whole row - taller than the others; shrinks the text to fit instead of wrapping.
        androidx.compose.foundation.text.BasicText(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
            ),
        )
    }
}

@Composable
private fun ListRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SiteSwitchRow(useE6Ai: Boolean, onSetUseE6Ai: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.drawer_use_e6ai), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.drawer_use_e6ai_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = useE6Ai, onCheckedChange = onSetUseE6Ai)
    }
}

/** Tap to re-ping. Green/yellow/red dot mirrors the rating-color language used elsewhere in the app. */
@Composable
private fun HealthCheckRow(site: Site, status: HealthStatus, onRecheck: () -> Unit) {
    val dotColor = when (status) {
        is HealthStatus.Checking -> RatingQuestionable
        is HealthStatus.Ok -> RatingSafe
        is HealthStatus.Error -> RatingExplicit
    }
    val detail = when (status) {
        is HealthStatus.Checking -> stringResource(R.string.health_checking)
        is HealthStatus.Ok -> stringResource(R.string.health_ok)
        is HealthStatus.Error -> status.message
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRecheck)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.health_check_title, site.displayName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is HealthStatus.Error) RatingExplicit else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
