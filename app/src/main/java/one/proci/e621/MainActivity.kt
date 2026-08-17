package one.proci.e621

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import one.proci.e621.data.util.eulaHash
import one.proci.e621.data.util.loadEulaText
import one.proci.e621.ui.navigation.E621NavGraph
import one.proci.e621.ui.screens.eula.EulaScreen
import one.proci.e621.ui.theme.E621Theme

class MainActivity : ComponentActivity() {
    // Set from the launching/onNewIntent Intent and consumed once E621NavGraph acts on it, so a
    // link tapped before the EULA is accepted still navigates once EulaScreen hands off to the
    // graph, but a later recomposition (e.g. rotation) doesn't re-trigger the same navigation.
    private var pendingDeepLinkPostId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkPostId = postIdFromIntent(intent)
        val app = application as E621Application
        setContent {
            val settings by app.userPreferences.settingsState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            // Recomputed only if this Activity is recreated - if res/raw/eula.txt is edited, a
            // fresh process picks up the new text and, since its hash won't match whatever was
            // last accepted, re-prompts automatically.
            val currentEulaHash = remember { eulaHash(loadEulaText(this)) }
            E621Theme(accentColor = settings.accentColor?.let { Color(it) }) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        // DataStore's first real read hasn't landed yet - settings is still the
                        // stateIn placeholder here, whose eulaAcceptedHash is always null. Without
                        // this check, that placeholder looked identical to "genuinely never
                        // agreed," flashing the EULA screen for a frame on every launch even for
                        // an already-agreed user, before the real value arrived and swapped it out.
                        !settings.isLoaded -> Unit
                        settings.eulaAcceptedHash == currentEulaHash -> E621NavGraph(
                            pendingDeepLinkPostId = pendingDeepLinkPostId,
                            onDeepLinkConsumed = { pendingDeepLinkPostId = null },
                        )
                        else -> EulaScreen(
                            onAgree = { scope.launch { app.userPreferences.setEulaAccepted(currentEulaHash) } },
                            onDisagree = { finishAffinity() },
                        )
                    }
                }
            }
        }
    }

    // singleTask (see the manifest's launchMode) routes a link tapped while the app is already
    // running here instead of spawning a second MainActivity instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkPostId = postIdFromIntent(intent)
    }
}

private val postIdRegex = Regex("""/posts/(\d+)""")

private fun postIdFromIntent(intent: Intent?): Long? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val path = intent.data?.path ?: return null
    return postIdRegex.find(path)?.groupValues?.get(1)?.toLongOrNull()
}
