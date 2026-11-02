package one.proci.e621.ui.screens.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import one.proci.e621.ui.theme.CardShape
import one.proci.e621.ui.components.ListCardSpacing
import one.proci.e621.ui.components.ListCardContentPadding
import one.proci.e621.ui.components.ListCard
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.data.model.LocalCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalCollectionsScreen(
    collections: List<LocalCollection>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LocalCollection?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.collections_create))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (collections.isEmpty()) {
                EmptyState(icon = Icons.Filled.Collections, title = stringResource(R.string.collections_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ListCardContentPadding,
                    verticalArrangement = ListCardSpacing,
                ) {
                    items(collections, key = { it.id }) { collection ->
                        ListCard(
                            onClick = { onOpen(collection.id) },
                            contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        ) { Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(40.dp)
                                    .clip(CardShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Collections,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(collection.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.collections_row_count, collection.postIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { pendingDelete = collection }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        } }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.collections_create)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.collections_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim()); showCreate = false }) {
                    Text(stringResource(R.string.collections_create))
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.comment_action_cancel)) } },
        )
    }

    pendingDelete?.let { collection ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.collections_delete_title)) },
            text = { Text(stringResource(R.string.collections_delete_body, collection.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(collection.id); pendingDelete = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.comment_action_cancel)) } },
        )
    }
}
