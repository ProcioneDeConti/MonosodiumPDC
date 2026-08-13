package one.proci.e621.data.util

/** Below 1000, the exact count; at or above, one decimal place + "K"/"M" (e.g. "1.1K", "-3.5K", "2.4M"). */
fun formatCount(count: Int): String {
    val magnitude = kotlin.math.abs(count)
    val (divisor, suffix) = when {
        magnitude >= 1_000_000 -> 1_000_000.0 to "M"
        magnitude >= 1_000 -> 1_000.0 to "K"
        else -> return count.toString()
    }
    val formatted = "%.1f%s".format(magnitude / divisor, suffix)
    return if (count < 0) "-$formatted" else formatted
}
