package one.proci.e621.ui.screens.messages

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.Dmail
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.repository.MessagesRepository
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    dmailId: Long,
    messagesRepository: MessagesRepository,
    avatarRepository: AvatarRepository,
    onBack: () -> Unit,
    onOpened: (Long) -> Unit,
    onReply: (Dmail) -> Unit,
    onDeleted: (Long) -> Unit,
    onOpenProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dmail by remember(dmailId) { mutableStateOf<Dmail?>(null) }
    var error by remember(dmailId) { mutableStateOf<String?>(null) }
    var confirmingDelete by remember(dmailId) { mutableStateOf(false) }
    var deleting by remember(dmailId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loadFailedTemplate = stringResource(R.string.message_load_failed)
    val deleteFailedTemplate = stringResource(R.string.message_delete_failed)

    LaunchedEffect(dmailId) {
        runCatching { messagesRepository.fetchDmail(dmailId) }
            .onSuccess {
                dmail = it
                onOpened(dmailId)
            }
            .onFailure { e -> error = String.format(loadFailedTemplate, e.message ?: e.toString()) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(dmail?.title ?: stringResource(R.string.messages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (dmail != null) {
                        // Two-tap: first tap arms (button turns into "Delete?"), second confirms.
                        TextButton(
                            enabled = !deleting,
                            onClick = {
                                if (!confirmingDelete) {
                                    confirmingDelete = true
                                    return@TextButton
                                }
                                deleting = true
                                scope.launch {
                                    runCatching { messagesRepository.deleteDmail(dmailId) }
                                        .onSuccess {
                                            onDeleted(dmailId)
                                            onBack()
                                        }
                                        .onFailure { e ->
                                            deleting = false
                                            confirmingDelete = false
                                            Toast.makeText(
                                                context,
                                                String.format(deleteFailedTemplate, e.message ?: e.toString()),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (confirmingDelete) R.string.message_delete_confirm else R.string.message_delete,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            dmail?.let { d ->
                ExtendedFloatingActionButton(onClick = { onReply(d) }) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.message_reply))
                }
            }
        },
    ) { padding ->
        when {
            error != null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            dmail == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                val d = dmail!!
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    val fromId = d.fromId
                    val profileModifier = if (fromId != null) Modifier.clickable { onOpenProfile(fromId) } else Modifier
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            userId = d.fromId,
                            name = d.fromName,
                            avatarRepository = avatarRepository,
                            size = 40.dp,
                            modifier = profileModifier,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = d.fromName ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = profileModifier,
                            )
                            d.createdAt?.let {
                                Text(
                                    formatRelativeTime(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    DTextView(text = d.body, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(72.dp)) // room for the FAB
                }
            }
        }
    }
}
