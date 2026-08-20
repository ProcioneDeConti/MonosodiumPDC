package one.proci.e621.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Falls back to a generic default until [one.proci.e621.E621Application] wires the real,
 * per-account User-Agent through [current] on startup - mirrors
 * [one.proci.e621.data.dtext.DTextLinkConfig]'s pattern for reaching call sites outside the
 * Retrofit/Coil DI graph.
 */
object MediaUserAgent {
    @Volatile var current: String = "e621ForAndroid/1.0"
}

/**
 * Shared OkHttpClient for one-off media fetches that sit outside the main Retrofit and Coil
 * stacks (APNG frame decoding, manual downloads) - a single instance so these don't each spin up
 * their own connection pool and dispatcher thread pool, and tagged with [MediaUserAgent] so they
 * carry the same compliant, per-account User-Agent as every other request (see
 * [UserAgentInterceptor] - e621 blocks generic/default User-Agents with 403).
 */
object MediaHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(SharedHttp.dispatcher)
            .connectionPool(SharedHttp.connectionPool)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", MediaUserAgent.current)
                    .build()
                chain.proceed(request)
            })
            .build()
    }
}
