package one.proci.e621.ui.screens.messages

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.repository.MessagesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageComposeScreen(
    initialToName: String,
    toEditable: Boolean,
    initialSubject: String,
    respondToId: Long?,
    messagesRepository: MessagesRepository,
    onBack: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var toName by remember { mutableStateOf(initialToName) }
    var subject by remember { mutableStateOf(initialSubject) }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sendFailedTemplate = stringResource(R.string.message_send_failed)

    fun send() {
        if (toName.isBlank() || subject.isBlank() || body.isBlank() || sending) return
        sending = true
        scope.launch {
            runCatching { messagesRepository.sendDmail(toName.trim(), subject.trim(), body.trim(), respondToId) }
                .onSuccess { onSent() }
                .onFailure { e ->
                    Toast.makeText(context, String.format(sendFailedTemplate, e.message ?: e.toString()), Toast.LENGTH_SHORT).show()
                }
            sending = false
        }
    }

    val canSend = toName.isNotBlank() && subject.isNotBlank() && body.isNotBlank() && !sending

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).height(24.dp))
                    } else {
                        IconButton(onClick = ::send, enabled = canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.message_send))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = toName,
                onValueChange = { toName = it },
                label = { Text(stringResource(R.string.message_to_hint)) },
                enabled = toEditable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.message_subject_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.message_body_hint)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
