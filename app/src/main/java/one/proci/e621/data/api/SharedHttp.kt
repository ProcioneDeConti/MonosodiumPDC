package one.proci.e621.data.api

import okhttp3.ConnectionPool
import okhttp3.Dispatcher

/**
 * The app builds three separate [okhttp3.OkHttpClient]s (Retrofit's API client, Coil's image
 * fetcher, [MediaHttpClient]'s one-off downloads) because each needs its own interceptor chain -
 * notably [SiteInterceptor] and [AuthInterceptor], which must NOT run on image/media requests
 * (they'd rewrite the CDN host to the API host, and leak the user's API key to it). Sharing just
 * the dispatcher (request thread pool) and connection pool between them avoids each client idling
 * its own separate pool of keep-alive threads/sockets, without merging their interceptors.
 */
object SharedHttp {
    val dispatcher = Dispatcher()
    val connectionPool = ConnectionPool()
}
