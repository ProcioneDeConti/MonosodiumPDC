package one.proci.e621.data.api

import android.util.Base64
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import one.proci.e621.data.settings.UserSettings

/**
 * e621 and e6AI (e6ai.net) share the same relative API paths (same underlying e621ng software) -
 * this just swaps the request's host per-call based on which site is currently active, so a
 * single Retrofit/OkHttp client (built once, against e621's base URL) transparently serves both.
 */
class SiteInterceptor(private val settings: StateFlow<UserSettings>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = settings.value.site.apiHost
        if (original.url.host == host) return chain.proceed(original)
        val request = original.newBuilder()
            .url(original.url.newBuilder().host(host).build())
            .build()
        return chain.proceed(request)
    }
}

/** e621 blocks requests with generic/default User-Agents (403), so every request must identify the app. */
class UserAgentInterceptor(private val settings: StateFlow<UserSettings>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", settings.value.userAgent)
            .build()
        return chain.proceed(request)
    }
}

/** Adds HTTP Basic auth (username + API key) when the user has configured credentials. */
class AuthInterceptor(private val settings: StateFlow<UserSettings>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val current = settings.value
        val request = if (current.isAuthenticated) {
            val credential = Base64.encodeToString(
                "${current.username}:${current.apiKey}".toByteArray(),
                Base64.NO_WRAP,
            )
            chain.request().newBuilder()
                .header("Authorization", "Basic $credential")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * Keeps requests to e621 spaced out as a courtesy to their infrastructure — the API docs ask
 * clients not to hammer the service. This is a simple fixed minimum gap between requests.
 */
class RateLimitInterceptor(private val minIntervalMs: Long = 500L) : Interceptor {
    private var lastRequestAt = 0L
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val wait = minIntervalMs - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}
