package one.proci.e621.ui.screens.sets

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.PostSet
import one.proci.e621.data.model.isValidShortname
import one.proci.e621.data.model.suggestShortname

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostSetsScreen(
    state: PostSetsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onCreate: (name: String, shortname: String, isPublic: Boolean, onResult: (Result<PostSet>) -> Unit) -> Unit,
    onDelete: (Long, (Result<Unit>) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PostSet?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_sets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isAuthenticated) {
                ExtendedFloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.post_sets_create))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.isAuthenticated -> CenteredMessage(
                    stringResource(R.string.post_sets_needs_account),
                    actionLabel = stringResource(R.string.settings),
                    onAction = onOpenSettings,
                )
                state.isLoading && state.sets.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.sets.isEmpty() -> CenteredMessage(
                    state.error,
                    actionLabel = stringResource(R.string.error_retry),
                    onAction = onRefresh,
                )
                state.sets.isEmpty() -> CenteredMessage(stringResource(R.string.post_sets_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sets, key = { it.id }) { set ->
                        PostSetRow(set, onClick = { onOpenSet(set.id) }, onDelete = { pendingDelete = set })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSetDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, shortname, isPublic ->
                onCreate(name, shortname, isPublic) { result ->
                    result
                        .onSuccess { showCreate = false }
                        .onFailure { e ->
                            Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }

    pendingDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.post_sets_delete_title)) },
            text = { Text(stringResource(R.string.post_sets_delete_body, set.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(set.id) { result ->
                        result.onFailure { e -> Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show() }
                    }
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.comment_action_cancel)) } },
        )
    }
}

@Composable
private fun PostSetRow(set: PostSet, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (set.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(set.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.post_sets_row_meta, set.shortname, set.postCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CreateSetDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var shortname by remember { mutableStateOf("") }
    var shortnameEdited by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(false) }
    val effectiveShortname = if (shortnameEdited) shortname else suggestShortname(name)
    val valid = name.isNotBlank() && isValidShortname(effectiveShortname)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.post_sets_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.post_sets_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = effectiveShortname,
                    onValueChange = { shortnameEdited = true; shortname = it },
                    label = { Text(stringResource(R.string.post_sets_field_shortname)) },
                    supportingText = { Text(stringResource(R.string.post_sets_shortname_hint)) },
                    isError = effectiveShortname.isNotEmpty() && !isValidShortname(effectiveShortname),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.post_sets_field_public), Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onCreate(name.trim(), effectiveShortname, isPublic) }) {
                Text(stringResource(R.string.post_sets_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.comment_action_cancel)) } },
    )
}

@Composable
private fun CenteredMessage(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
