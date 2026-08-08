package one.proci.e621

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                        settings.eulaAcceptedHash == currentEulaHash -> E621NavGraph()
                        else -> EulaScreen(
                            onAgree = { scope.launch { app.userPreferences.setEulaAccepted(currentEulaHash) } },
                            onDisagree = { finishAffinity() },
                        )
                    }
                }
            }
        }
    }
}
