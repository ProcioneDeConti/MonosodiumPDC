package one.proci.e621.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

interface GitHubApiService {
    // Returns the raw Response (rather than the deserialized body directly) so the caller can
    // read GitHub's X-RateLimit-* headers - see UpdateCheckRepository - and so a non-2xx status
    // (e.g. 403 when rate limited) is reported via response.isSuccessful rather than thrown.
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(@Path("owner") owner: String, @Path("repo") repo: String): Response<GitHubRelease>
}
