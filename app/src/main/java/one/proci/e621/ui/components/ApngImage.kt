package one.proci.e621.ui.components

import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.linecorp.apng.ApngDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Android's ImageDecoder doesn't animate APNG, so it's decoded manually with com.linecorp:apng.
private val apngHttpClient by lazy { OkHttpClient() }

@Composable
fun ApngImage(url: String, modifier: Modifier = Modifier) {
    var drawable by remember(url) { mutableStateOf<ApngDrawable?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        drawable = null
        failed = false
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", "e621ForAndroid/1.0").build()
                apngHttpClient.newCall(request).execute().use { response ->
                    response.body?.byteStream()?.let { ApngDrawable.decode(it) }
                }
            }.getOrNull()
        }
        if (decoded != null) drawable = decoded else failed = true
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val current = drawable
        when {
            current != null -> {
                // Without this, a decoded APNG keeps its frame-advance callbacks scheduled (and
                // its decoded frame buffers alive) even after this leaves the screen - e.g.
                // swiped past in the pager, or the viewer closed - burning CPU/battery and memory
                // for an animation nobody can see until the whole node is GC'd.
                DisposableEffect(current) {
                    onDispose { current.stop() }
                }
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            current.loopCount = ApngDrawable.LOOP_FOREVER
                            setImageDrawable(current)
                            current.start()
                        }
                    },
                )
            }
            failed -> Text("Couldn't load image")
            else -> CircularProgressIndicator()
        }
    }
}
