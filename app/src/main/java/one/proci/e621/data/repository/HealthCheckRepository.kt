package one.proci.e621.data.repository

import java.io.IOException
import one.proci.e621.data.api.E621ApiService
import retrofit2.HttpException

sealed class HealthStatus {
    data object Checking : HealthStatus()
    data object Ok : HealthStatus()
    data class Error(val message: String) : HealthStatus()
}

/** Codes Cloudflare (or a similar edge proxy) commonly answers with when the origin is unreachable or a challenge is triggered. */
private val EdgeErrorCodes = setOf(403, 429, 502, 503, 520, 521, 522, 523, 524, 525, 526, 530)

/**
 * A minimal, cheap ping against whichever site (e621 or e6AI - see [one.proci.e621.data.api.SiteInterceptor])
 * is currently active, for the drawer's connectivity indicator. Reuses the same typed
 * `posts.json` call the rest of the app already makes rather than a dedicated endpoint, since
 * that's the actual functionality being checked.
 */
class HealthCheckRepository(private val api: E621ApiService) {

    suspend fun check(): HealthStatus = try {
        api.getPosts(tags = null, limit = 1)
        HealthStatus.Ok
    } catch (e: HttpException) {
        val raw = e.response()?.raw()
        val fromEdge = e.code() in EdgeErrorCodes &&
            (raw?.header("cf-ray") != null || raw?.header("server")?.contains("cloudflare", ignoreCase = true) == true)
        HealthStatus.Error(if (fromEdge) "Cloudflare error (HTTP ${e.code()})" else "HTTP ${e.code()} ${e.message()}".trim())
    } catch (e: IOException) {
        HealthStatus.Error(e.message ?: e.toString())
    } catch (e: Exception) {
        // Most likely a JSON-parse failure - e.g. Cloudflare returning an HTML challenge page
        // with a 200 status, which wouldn't surface as an HttpException above.
        HealthStatus.Error("Unexpected response - ${e.message ?: e.toString()}")
    }
}
