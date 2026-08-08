package one.proci.e621.data.util

import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SameYearFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val WithYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * e621 timestamps are ISO-8601 with an offset (e.g. "2026-08-07T10:23:45.000-04:00"). Renders as
 * relative time for anything recent ("5m ago", "3h ago", "2d ago"), falling back to an absolute
 * date for anything older than a week - matching the usual social-app convention rather than
 * showing a raw timestamp.
 */
fun formatRelativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val parsed = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return iso.take(10)
    val now = OffsetDateTime.now()
    val seconds = Duration.between(parsed, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "Just now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        seconds < 86_400 * 7 -> "${seconds / 86_400}d ago"
        parsed.year == now.year -> parsed.format(SameYearFormatter)
        else -> parsed.format(WithYearFormatter)
    }
}
