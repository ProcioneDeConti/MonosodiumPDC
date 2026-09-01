package one.proci.e621.data.util

/** Progress of a running bulk grid action (favorite / unfavorite / download of many posts). */
data class BulkProgress(
    val done: Int,
    val total: Int,
    val failures: Int = 0,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val finished: Boolean get() = done >= total
}
