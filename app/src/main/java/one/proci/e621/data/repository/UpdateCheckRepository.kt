package one.proci.e621.data.repository

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Headers
import one.proci.e621.data.api.GitHubApiService
import one.proci.e621.data.util.isNewerVersion

sealed class UpdateCheckStatus {
    data object Idle : UpdateCheckStatus()
    data object Checking : UpdateCheckStatus()
    data class UpToDate(val currentVersion: String) : UpdateCheckStatus()
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : UpdateCheckStatus()
    data class Error(val message: String) : UpdateCheckStatus()
}

/**
 * How many update checks are left this hour. [fromServer] true means [remaining]/[limit] are
 * GitHub's own X-RateLimit-* header values (authoritative); false means GitHub didn't return
 * them (shouldn't normally happen, but not guaranteed) and this is a local estimate instead,
 * assuming GitHub's documented default of 60/hour for unauthenticated requests.
 */
data class RateLimitInfo(val remaining: Int, val limit: Int, val fromServer: Boolean)

private const val REPO_OWNER = "ProcioneDeConti"
private const val REPO_NAME = "MonosodiumPDC"
private const val FALLBACK_HOURLY_LIMIT = 60
private const val HOUR_MILLIS = 60 * 60 * 1000L

/**
 * Manual-only (Settings > Updates > Check for Updates) check against GitHub's "latest release"
 * endpoint - never called automatically, since unauthenticated GitHub API requests are rate
 * limited at 60/hour *per IP*, not per app install, and many users can share one IP behind
 * carrier-grade NAT.
 */
class UpdateCheckRepository(private val api: GitHubApiService) {

    private val _rateLimitInfo = MutableStateFlow<RateLimitInfo?>(null)
    val rateLimitInfo: StateFlow<RateLimitInfo?> = _rateLimitInfo.asStateFlow()

    /** In-memory only - timestamps of this process's own checks, used solely for the [RateLimitInfo] fallback estimate below. */
    private val localCheckTimestamps = mutableListOf<Long>()

    suspend fun check(currentVersionName: String): UpdateCheckStatus {
        recordLocalCheck()
        return try {
            val response = api.getLatestRelease(REPO_OWNER, REPO_NAME)
            updateRateLimitInfo(response.headers())
            if (!response.isSuccessful) {
                return if (response.code() == 403) {
                    UpdateCheckStatus.Error("Rate limited by GitHub - try again later")
                } else {
                    UpdateCheckStatus.Error("HTTP ${response.code()}")
                }
            }
            val release = response.body() ?: return UpdateCheckStatus.Error("Empty response from GitHub")
            val latestVersion = release.tagName.removePrefix("v")
            if (isNewerVersion(latestVersion, currentVersionName)) {
                UpdateCheckStatus.UpdateAvailable(latestVersion, release.htmlUrl)
            } else {
                UpdateCheckStatus.UpToDate(currentVersionName)
            }
        } catch (e: IOException) {
            UpdateCheckStatus.Error(e.message ?: e.toString())
        } catch (e: Exception) {
            // Most likely a JSON-parse failure.
            UpdateCheckStatus.Error("Unexpected response - ${e.message ?: e.toString()}")
        }
    }

    private fun recordLocalCheck() {
        val now = System.currentTimeMillis()
        localCheckTimestamps.add(now)
        pruneLocalCheckTimestamps(now)
    }

    private fun pruneLocalCheckTimestamps(now: Long = System.currentTimeMillis()) {
        localCheckTimestamps.removeAll { now - it > HOUR_MILLIS }
    }

    private fun updateRateLimitInfo(headers: Headers) {
        val remaining = headers["X-RateLimit-Remaining"]?.toIntOrNull()
        val limit = headers["X-RateLimit-Limit"]?.toIntOrNull()
        _rateLimitInfo.value = if (remaining != null && limit != null) {
            RateLimitInfo(remaining, limit, fromServer = true)
        } else {
            pruneLocalCheckTimestamps()
            RateLimitInfo(
                remaining = (FALLBACK_HOURLY_LIMIT - localCheckTimestamps.size).coerceAtLeast(0),
                limit = FALLBACK_HOURLY_LIMIT,
                fromServer = false,
            )
        }
    }
}
