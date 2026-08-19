package one.proci.e621.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Wholly separate from [E621Client] - a different host (GitHub, not e621/e6AI), with no
 * auth/site-switching/rate-limit-courtesy machinery to share with it. Only ever called manually
 * (Settings > Updates > Check for Updates), never automatically - see [one.proci.e621.data.repository.UpdateCheckRepository].
 */
object GitHubClient {
    private const val BASE_URL = "https://api.github.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(): GitHubApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // GitHub's API rejects requests with no User-Agent header.
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MonosodiumPDC-Android-UpdateCheck")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(GitHubApiService::class.java)
    }
}
