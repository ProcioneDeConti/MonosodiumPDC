package one.proci.e621.data.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import one.proci.e621.data.settings.UserSettings
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object E621Client {
    // Retrofit needs one fixed base URL to resolve every endpoint's relative path against;
    // [SiteInterceptor] rewrites the resulting request's host per-call to whichever site (e621 or
    // e6AI) is actually active, so this doesn't have to be rebuilt when the user switches sites.
    private const val BASE_URL = "https://e621.net/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(settings: StateFlow<UserSettings>): E621ApiService {
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(SharedHttp.dispatcher)
            .connectionPool(SharedHttp.connectionPool)
            .addInterceptor(SiteInterceptor(settings))
            .addInterceptor(UserAgentInterceptor(settings))
            .addInterceptor(AuthInterceptor(settings))
            .addInterceptor(RateLimitInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(E621ApiService::class.java)
    }
}
