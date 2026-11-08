package one.proci.e621.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.recovery.RecoveryQueue
import one.proci.e621.data.util.BulkProgress
import one.proci.e621.ui.theme.FavoriteGold
import one.proci.e621.ui.theme.tabular

/**
 * Grid multi-select state, shared by the screen (contextual top bar) and [PostGridBody] (tiles,
 * drag-to-select, action bar). Long-pressing a tile enters selection; dragging on from there
 * sweeps a range in or out.
 */
@Stable
class GridSelectionState {
    var active by mutableStateOf(false)
        private set
    var ids by mutableStateOf<Set<Long>>(emptySet())

    fun start() { active = true }
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun exit() { active = false; ids = emptySet() }
}

@Composable
fun rememberGridSelectionState(): GridSelectionState = remember { GridSelectionState() }

/** Replaces a grid screen's normal top bar while selecting: close, a rolling count, select-all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selection: GridSelectionState,
    allIds: List<Long>,
    bulkProgress: BulkProgress?,
) {
    val count = selection.ids.size
    val allSelected = allIds.isNotEmpty() && selection.ids.containsAll(allIds)
    val running = bulkProgress != null && !bulkProgress.finished
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = selection::exit) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_exit))
            }
        },
        title = {
            if (running) {
                Text(
                    stringResource(R.string.bulk_progress, bulkProgress!!.done, bulkProgress.total),
                    style = MaterialTheme.typography.titleLarge.tabular(),
                )
            } else {
                // The number rolls up or down as tiles are added or removed.
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { h -> if (up) h else -h } + fadeIn()) togetherWith
                            (slideOutVertically { h -> if (up) -h else h } + fadeOut()) using
                            SizeTransform(clip = false)
                    },
                    label = "selectionCount",
                ) { n ->
                    Text(
                        stringResource(R.string.bulk_selected_count, n),
                        style = MaterialTheme.typography.titleLarge.tabular(),
                    )
                }
            }
        },
        actions = {
            IconButton(
                enabled = allIds.isNotEmpty(),
                onClick = { selection.ids = if (allSelected) emptySet() else selection.ids + allIds },
            ) {
                Icon(
                    if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                    contentDescription = stringResource(if (allSelected) R.string.selection_deselect_all else R.string.selection_select_all),
                )
            }
        },
    )
}

/**
 * The floating one-row action pill shown along the bottom of the grid while selecting. Each action
 * enables only when it would do something for the current selection (e.g. Favorite greys out once
 * everything selected is already a favorite). Bulk progress runs as a rainbow line along its top.
 */
@Composable
fun SelectionActionBar(
    selected: List<Post>,
    bulkProgress: BulkProgress?,
    onFavorite: (List<Post>) -> Unit,
    onUnfavorite: (List<Post>) -> Unit,
    onUpvote: ((List<Post>) -> Unit)?,
    onAddToSet: ((List<Post>) -> Unit)?,
    onDownload: (List<Post>) -> Unit,
    onRecover: (List<Post>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmUnfavorite by remember { mutableStateOf<List<Post>?>(null) }
    val idle = bulkProgress == null
    val toFavorite = selected.filter { !it.isFavorited }
    val toUnfavorite = selected.filter { it.isFavorited }
    val toUpvote = selected.filter { it.voteBy != 1 }
    val deleted = selected.filter { it.isDeleted }

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarAction(
                    icon = Icons.Filled.Star,
                    tint = FavoriteGold,
                    label = stringResource(R.string.bulk_favorite),
                    enabled = idle && toFavorite.isNotEmpty(),
                    onClick = { onFavorite(toFavorite) },
                )
                BarAction(
                    icon = Icons.Filled.StarBorder,
                    label = stringResource(R.string.bulk_unfavorite),
                    enabled = idle && toUnfavorite.isNotEmpty(),
                    onClick = { confirmUnfavorite = toUnfavorite },
                )
                if (onUpvote != null) {
                    BarAction(
                        icon = Icons.Filled.ArrowUpward,
                        label = stringResource(R.string.quick_action_upvote),
                        enabled = idle && toUpvote.isNotEmpty(),
                        onClick = { onUpvote(toUpvote) },
                    )
                }
                if (onAddToSet != null) {
                    BarAction(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = stringResource(R.string.selection_add_to_set),
                        enabled = idle && selected.isNotEmpty(),
                        onClick = { onAddToSet(selected) },
                    )
                }
                BarAction(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.bulk_download),
                    enabled = idle && selected.isNotEmpty(),
                    onClick = { onDownload(selected) },
                )
                // Bulk un-delete: only when the selection holds deleted posts (searched one at a time).
                if (deleted.isNotEmpty()) {
                    BarAction(
                        icon = Icons.Filled.Recycling,
                        label = stringResource(R.string.selection_recover),
                        badge = deleted.size,
                        enabled = deleted.size <= RecoveryQueue.MAX_BATCH,
                        onClick = { onRecover(deleted) },
                    )
                }
            }
            if (bulkProgress != null && !bulkProgress.finished) {
                RainbowLinearIndicator(
                    progress = { bulkProgress.fraction },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }

    confirmUnfavorite?.let { posts ->
        AlertDialog(
            onDismissRequest = { confirmUnfavorite = null },
            icon = { Icon(Icons.Filled.StarBorder, contentDescription = null) },
            title = { Text(pluralStringResource(R.plurals.selection_unfavorite_title, posts.size, posts.size)) },
            text = { Text(stringResource(R.string.selection_unfavorite_message)) },
            confirmButton = {
                TextButton(onClick = { confirmUnfavorite = null; onUnfavorite(posts) }) {
                    Text(stringResource(R.string.bulk_unfavorite), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnfavorite = null }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun RowScope.BarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    badge: Int? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (badge != null) {
            BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        BasicText(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
