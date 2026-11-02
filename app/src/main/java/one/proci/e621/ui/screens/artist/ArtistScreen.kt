package one.proci.e621.ui.screens.artist

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.Artist
import one.proci.e621.data.model.ArtistDnp
import one.proci.e621.data.settings.Site
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    site: Site,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = state.artist?.displayName ?: state.name.replace('_', ' ')
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.artist == null -> LoadingState(Modifier.padding(padding))

            state.error != null && state.artist == null -> CenteredMessage(
                message = state.error,
                modifier = Modifier.padding(padding),
            ) { TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) } }

            // Plenty of artist tags have no artist record; still let the user see their posts.
            state.notFound || state.artist == null -> CenteredMessage(
                message = stringResource(R.string.artist_not_found),
                modifier = Modifier.padding(padding),
            ) { OutlinedButton(onClick = { onSearch(state.name) }) { Text(stringResource(R.string.artist_view_posts_anyway)) } }

            else -> ArtistContent(
                artist = state.artist,
                dnp = state.dnp,
                site = site,
                onSearch = onSearch,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistContent(
    artist: Artist,
    dnp: ArtistDnp?,
    site: Site,
    onSearch: (String) -> Unit,
    onOpenProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (dnp != null) DnpBanner(dnp, site)

        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(artist.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (artist.isLocked) StatusPill(stringResource(R.string.artist_locked), showLock = true)
                if (!artist.isActive) StatusPill(stringResource(R.string.artist_inactive))
            }
            if (artist.groupName.isNotBlank()) {
                Text(
                    stringResource(R.string.artist_group, artist.groupName.replace('_', ' ')),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSearch(artist.name) }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wiki_view_posts))
            }
            OutlinedButton(onClick = { uriHandler.openUri("${site.webBaseUrl}/artists/${artist.id}") }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.artist_open_on_site, site.displayName))
            }
            artist.linkedUserId?.let { userId ->
                OutlinedButton(onClick = { onOpenProfile(userId) }) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.artist_linked_account))
                }
            }
        }

        if (artist.otherNames.isNotEmpty()) {
            ArtistSection(stringResource(R.string.artist_other_names)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    artist.otherNames.forEach { other ->
                        Text(
                            other.replace('_', ' '),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                                .clickable { onSearch(other) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Live links first, dead ones after and dimmed - same as desktop.
        val urls = artist.urls.filter { it.url.isNotBlank() }.sortedBy { !it.isActive }
        if (urls.isNotEmpty()) {
            ArtistSection(stringResource(R.string.artist_links)) {
                Column {
                    urls.forEach { link ->
                        UrlRow(url = link.url, inactive = !link.isActive, onClick = { uriHandler.openUri(link.url) })
                    }
                }
            }
        }

        val notes = artist.notes?.trim().orEmpty()
        if (notes.isNotEmpty()) {
            ArtistSection(stringResource(R.string.artist_notes)) {
                DTextView(text = notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DnpBanner(dnp: ArtistDnp, site: Site) {
    val error = MaterialTheme.colorScheme.error
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(error.copy(alpha = 0.10f), shape)
            .border(1.dp, error.copy(alpha = 0.4f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = error, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.artist_dnp_title), color = error, fontWeight = FontWeight.Bold)
            Text(
                dnp.details?.takeIf { it.isNotBlank() } ?: stringResource(R.string.artist_dnp_default, site.displayName),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, showLock: Boolean = false) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLock) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtistSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun UrlRow(url: String, inactive: Boolean, onClick: () -> Unit) {
    val alpha = if (inactive) 0.45f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                urlSiteLabel(url),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                url.removePrefix("https://").removePrefix("http://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (inactive) {
            Text(
                stringResource(R.string.artist_link_dead),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A short, recognisable label for an off-site link, falling back to the bare host. */
private fun urlSiteLabel(url: String): String {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")?.lowercase()
        ?: return url
    return KNOWN_HOSTS.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value ?: host
}

private val KNOWN_HOSTS = linkedMapOf(
    "furaffinity.net" to "FurAffinity",
    "twitter.com" to "Twitter / X",
    "x.com" to "Twitter / X",
    "bsky.app" to "Bluesky",
    "deviantart.com" to "DeviantArt",
    "inkbunny.net" to "Inkbunny",
    "weasyl.com" to "Weasyl",
    "patreon.com" to "Patreon",
    "subscribestar.adult" to "SubscribeStar",
    "subscribestar.com" to "SubscribeStar",
    "ko-fi.com" to "Ko-fi",
    "pixiv.net" to "Pixiv",
    "fanbox.cc" to "Pixiv Fanbox",
    "tumblr.com" to "Tumblr",
    "instagram.com" to "Instagram",
    "youtube.com" to "YouTube",
    "itaku.ee" to "Itaku",
    "newgrounds.com" to "Newgrounds",
    "sofurry.com" to "SoFurry",
    "artstation.com" to "ArtStation",
    "linktr.ee" to "Linktree",
    "carrd.co" to "Carrd",
    "gumroad.com" to "Gumroad",
    "discord.gg" to "Discord",
    "discord.com" to "Discord",
    "t.me" to "Telegram",
    "e621.net" to "e621",
)
