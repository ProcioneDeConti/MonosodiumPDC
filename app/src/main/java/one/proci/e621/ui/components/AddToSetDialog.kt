package one.proci.e621.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.model.PostSet
import one.proci.e621.data.model.suggestShortname
import one.proci.e621.data.repository.PostSetRepository

/**
 * Add one or many posts to a post set (or create a set inline and add them). Shared by the post
 * viewer and the grid multi-select bar - [postIds] is 1 post from the viewer, N from selection.
 */
@Composable
fun AddToSetDialog(
    postIds: List<Long>,
    repository: PostSetRepository,
    onDismiss: () -> Unit,
) {
    if (postIds.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sets by remember { mutableStateOf<List<PostSet>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val addedTemplate = stringResource(R.string.add_to_set_added)
    val failedTemplate = stringResource(R.string.add_to_set_failed)

    LaunchedEffect(Unit) {
        runCatching { repository.fetchMySets() }
            .onSuccess { sets = it }
            .onFailure { e -> error = e.message ?: e.toString() }
    }

    fun addAll(set: PostSet) {
        busyId = set.id
        scope.launch {
            val missing = postIds.filterNot { it in set.postIds }
            val result = runCatching { missing.forEach { repository.addPost(set.id, it) } }
            busyId = null
            result
                .onSuccess {
                    sets = sets?.map { if (it.id == set.id) it.copy(postIds = (it.postIds + postIds).distinct()) else it }
                    Toast.makeText(context, String.format(addedTemplate, set.name), Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, String.format(failedTemplate, e.message ?: e.toString()), Toast.LENGTH_SHORT).show()
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (postIds.size == 1) stringResource(R.string.add_to_set_action)
                else stringResource(R.string.add_to_set_action_n, postIds.size),
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error.orEmpty())
                    sets == null -> CircularProgressIndicator()
                    else -> {
                        if (sets!!.isEmpty()) {
                            Text(
                                stringResource(R.string.post_sets_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        sets!!.forEach { set ->
                            val allIn = postIds.all { it in set.postIds }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !allIn && busyId == null) { addAll(set) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(set.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                when {
                                    busyId == set.id -> CircularProgressIndicator(Modifier.size(18.dp))
                                    allIn -> Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(R.string.add_to_set_new)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (creating) {
                                    CircularProgressIndicator(Modifier.size(18.dp))
                                } else {
                                    IconButton(
                                        enabled = newName.isNotBlank(),
                                        onClick = {
                                            creating = true
                                            val name = newName.trim()
                                            val shortname = suggestShortname(name)
                                            scope.launch {
                                                val result = runCatching {
                                                    val created = repository.createSet(name, shortname, isPublic = false)
                                                    postIds.forEach { repository.addPost(created.id, it) }
                                                    created
                                                }
                                                creating = false
                                                result
                                                    .onSuccess { created ->
                                                        newName = ""
                                                        sets = (sets.orEmpty() + created.copy(postIds = postIds))
                                                        Toast.makeText(context, String.format(addedTemplate, created.name), Toast.LENGTH_SHORT).show()
                                                    }
                                                    .onFailure { e ->
                                                        Toast.makeText(context, String.format(failedTemplate, e.message ?: e.toString()), Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        },
                                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_to_set_new)) }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}
