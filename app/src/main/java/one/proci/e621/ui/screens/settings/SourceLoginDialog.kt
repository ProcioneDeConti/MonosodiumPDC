package one.proci.e621.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.recovery.SourceLogins
import one.proci.e621.ui.components.RainbowLinearIndicator

/**
 * Signs in to a source site on its own login page. The app never sees the password: once a page
 * load shows the site's session cookies, they're checked with a real request (the page must show
 * the logout link), saved by [SourceLogins], and the dialog closes itself. The WebView's cookies
 * and storage are wiped on close so nothing lingers outside the encrypted store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceLoginDialog(site: SourceLogins.Site, onDone: (signedIn: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    var loading by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // Reads the session out of the WebView; on a verified login, saves it and closes. `closing`
    // = the user closed the dialog by hand, so this is the last chance and it always finishes.
    fun capture(closing: Boolean) {
        if (finished || (checking && !closing)) return
        val cookie = SourceLogins.sessionCookie(site, CookieManager.getInstance().getCookie(site.checkUrl))
        if (cookie == null) {
            if (closing) { finished = true; currentOnDone(false) }
            return
        }
        checking = true
        scope.launch {
            val ok = SourceLogins.verify(site, cookie)
            if (ok) SourceLogins.save(site, cookie)
            checking = false
            if (ok || closing) {
                finished = true
                currentOnDone(ok)
            }
        }
    }

    Dialog(
        onDismissRequest = { capture(closing = true) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.source_login_title_fa)) },
                    navigationIcon = {
                        IconButton(onClick = { capture(closing = true) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                )
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            // Start from a clean slate: no other site's session may be lifted.
                            CookieManager.getInstance().removeAllCookies(null)
                            WebView(context).apply {
                                settings.javaScriptEnabled = true // login captchas need it
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        capture(closing = false)
                                    }
                                }
                                loadUrl(site.loginUrl)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            WebStorage.getInstance().deleteAllData()
                        },
                    )
                    if (loading || checking) {
                        RainbowLinearIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}
