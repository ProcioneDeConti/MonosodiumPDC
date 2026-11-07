package one.proci.e621.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import one.proci.e621.R
import one.proci.e621.data.recovery.Attempt
import one.proci.e621.data.recovery.Attempt.Stage
import one.proci.e621.data.recovery.Grade
import one.proci.e621.data.recovery.RecoveredImage
import one.proci.e621.data.recovery.RecoveredSet
import java.text.DateFormat
import java.util.Date

/**
 * The report card for a deleted-post search (ported from the desktop's RecoveryReport): one row per
 * place it looked, grouped by stage - files it found (with their grade) and, for each miss, a few
 * plain words on why. Used for "nothing found" and, closeable, over a recovered post.
 */

/** found / deliberately skipped / not there any more / looked, no copy / needs a login or key / something went wrong. */
enum class ReasonTone { FOUND, SKIP, GONE, NONE, BLOCKED, ERROR }

class Reason(val text: String, val tone: ReasonTone, val hint: String? = null)

/** md5-lookup boorus: a file recovered through one of their post pages came from the identical-file search. */
private val MIRROR_HOSTS = mapOf(
    "danbooru.donmai.us" to "Danbooru",
    "rule34.paheal.net" to "rule34.paheal",
    "gelbooru.com" to "Gelbooru",
    "rule34.xxx" to "Rule34.xxx",
)

private fun hostOf(url: String) = Uri.parse(url).host?.removePrefix("www.") ?: url

/** One report row per recovered file, in the section it came from. */
fun foundAttempts(items: List<RecoveredImage>): List<Attempt> = items.map { item ->
    val host = hostOf(item.sourceUrl)
    val mirror = MIRROR_HOSTS[host]
    Attempt(
        stage = if (item.viaArchive) Stage.ARCHIVE else if (mirror != null) Stage.MIRROR else Stage.SOURCE,
        label = mirror ?: host,
        url = item.sourceUrl,
        reason = "found ${item.grade.name.lowercase()}: ${item.imageUrl}",
        grade = item.grade,
    )
}

@Composable
fun gradeShortText(grade: Grade) = stringResource(
    when (grade) {
        Grade.EXACT -> R.string.recover_grade_exact_short
        Grade.SAME_DIMENSIONS -> R.string.recover_grade_same_short
        Grade.RESCALED -> R.string.recover_grade_rescaled_short
        Grade.MISMATCH -> R.string.recover_grade_mismatch_short
    },
)

/** A few plain words for one attempt's raw reason. */
@Composable
fun describeReason(a: Attempt): Reason {
    a.grade?.let { return Reason(gradeShortText(it), if (it == Grade.MISMATCH) ReasonTone.NONE else ReasonTone.FOUND) }
    val raw = a.reason.trim()
    val lower = raw.lowercase()
    if (a.stage == Stage.SKIPPED) {
        return Reason(
            stringResource(if (raw.contains("profile")) R.string.recover_reason_profile else R.string.recover_reason_front_page),
            ReasonTone.SKIP,
        )
    }
    if (a.stage == Stage.LIMIT) return Reason(stringResource(R.string.recover_reason_time, raw), ReasonTone.ERROR)
    if (lower.startsWith("login required")) {
        return if (lower.contains("expired")) {
            Reason(stringResource(R.string.recover_reason_login_expired), ReasonTone.BLOCKED, stringResource(R.string.recover_hint_sign_in_again))
        } else {
            Reason(stringResource(R.string.recover_reason_login), ReasonTone.BLOCKED, stringResource(R.string.recover_hint_sign_in))
        }
    }
    if (lower == "no copy") return Reason(stringResource(R.string.recover_reason_no_copy), ReasonTone.NONE)
    if (lower == "no api key saved") return Reason(stringResource(R.string.recover_reason_no_key), ReasonTone.BLOCKED)
    if (lower.contains("check the key")) {
        return Reason(stringResource(R.string.recover_reason_key_refused), ReasonTone.BLOCKED, stringResource(R.string.recover_hint_check_key))
    }
    Regex("""^deleted from ([^(]+?)\s*(\(|$)""", RegexOption.IGNORE_CASE).find(raw)?.let {
        return Reason(stringResource(R.string.recover_reason_deleted_from, it.groupValues[1]), ReasonTone.GONE)
    }
    if (lower.contains("no wayback snapshot")) return Reason(stringResource(R.string.recover_reason_never_archived), ReasonTone.GONE)
    if (lower.contains("asked us to slow down")) return Reason(stringResource(R.string.recover_reason_wayback_paused), ReasonTone.ERROR)
    Regex("""^(\d+) captures?, none usable""").find(raw)?.let {
        val n = it.groupValues[1].toInt()
        return Reason(pluralStringResource(R.plurals.recover_reason_archived, n, n), ReasonTone.GONE)
    }
    Regex("""HTTP (\d{3})""").find(raw)?.let {
        val code = it.groupValues[1].toInt()
        return when {
            code == 404 || code == 410 -> Reason(stringResource(R.string.recover_reason_gone, code), ReasonTone.GONE)
            code == 401 || code == 403 -> Reason(stringResource(R.string.recover_reason_refused, code), ReasonTone.BLOCKED)
            code == 429 -> Reason(stringResource(R.string.recover_reason_rate_limited), ReasonTone.ERROR)
            code >= 500 -> Reason(stringResource(R.string.recover_reason_down, code), ReasonTone.ERROR)
            else -> Reason("HTTP $code", ReasonTone.ERROR)
        }
    }
    if (lower.contains("timed out")) return Reason(stringResource(R.string.recover_reason_timed_out), ReasonTone.ERROR)
    if (lower.contains("hides this tweet") || lower.startsWith("tweet media not found")) {
        return Reason(stringResource(R.string.recover_reason_no_media), ReasonTone.GONE)
    }
    if (Regex("""no (og:image|submission image|submission file|reader image)|not found \(removed\?\)|has no (files|pages|images|media)|removed""").containsMatchIn(lower)) {
        return Reason(stringResource(R.string.recover_reason_no_image), ReasonTone.GONE)
    }
    if (lower.contains("not a usable image") || lower.startsWith("not an image")) return Reason(stringResource(R.string.recover_reason_not_image), ReasonTone.GONE)
    if (lower.contains("non-public address")) return Reason(stringResource(R.string.recover_reason_private), ReasonTone.ERROR)
    val first = raw.split(';', '\n').first().trim()
    val short = if (first.length > 60) first.take(57) + "…" else first
    return Reason(short.replaceFirstChar { it.uppercase() }, ReasonTone.ERROR)
}

/** The short "where" text after the site name: the URL's path, or a name for e621's own file. */
@Composable
private fun shortPath(url: String?): String {
    if (url == null) return ""
    val u = Uri.parse(url)
    if (u.host?.matches(Regex("""static1\.(e621|e6ai)\.net""")) == true) return stringResource(R.string.recover_report_own_copy)
    val path = (u.path.orEmpty()) + (u.query?.let { "?$it" } ?: "")
    return if (path == "/" || path.isEmpty()) "" else path
}

private fun toneColor(tone: ReasonTone) = when (tone) {
    ReasonTone.FOUND -> Color(0xFF86EFAC)
    ReasonTone.SKIP -> Color.White.copy(alpha = 0.35f)
    ReasonTone.GONE -> Color.White.copy(alpha = 0.6f)
    ReasonTone.NONE -> Color.White.copy(alpha = 0.5f)
    ReasonTone.BLOCKED -> Color(0xFFFCD34D)
    ReasonTone.ERROR -> Color(0xFFFCA5A5)
}

private fun toneIcon(tone: ReasonTone) = when (tone) {
    ReasonTone.FOUND -> Icons.Filled.CheckCircle
    ReasonTone.SKIP -> Icons.Filled.RemoveCircleOutline
    ReasonTone.GONE -> Icons.Filled.SearchOff
    ReasonTone.NONE -> Icons.Outlined.Circle
    ReasonTone.BLOCKED -> Icons.Filled.Lock
    ReasonTone.ERROR -> Icons.Filled.Warning
}

fun formatSearchedOn(unixSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(unixSeconds * 1000))

/**
 * [set] = what a search found plus where it came up empty; otherwise [failed] = a search that found
 * nothing ([noSources]: the post lists none). [onClose] adds an × (the card over a recovered post).
 */
@Composable
fun RecoveryReportCard(
    set: RecoveredSet?,
    failed: List<Attempt>?,
    noSources: Boolean = false,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val attempts: List<Attempt>
    val title: String
    var subtitle: String
    val success = set != null && set.items.isNotEmpty()
    if (success) {
        set!!
        attempts = foundAttempts(set.items) + set.report?.attempts.orEmpty()
        val best = set.items.first()
        title = pluralStringResource(R.plurals.recover_report_found, set.items.size, set.items.size)
        subtitle = stringResource(
            if (best.grade == Grade.EXACT) R.string.recover_report_best_exact else R.string.recover_report_best,
            gradeShortText(best.grade).lowercase(),
            foundAttempts(listOf(best)).first().label,
        )
        set.report?.let { subtitle += " · " + stringResource(R.string.recover_report_searched, formatSearchedOn(it.searchedAt)) }
    } else {
        attempts = failed ?: set?.report?.attempts.orEmpty()
        title = stringResource(R.string.recover_report_none)
        val looked = attempts.count { it.stage != Stage.SKIPPED && it.stage != Stage.LIMIT }
        subtitle = when {
            noSources -> stringResource(R.string.recover_report_no_sources)
            looked > 0 -> pluralStringResource(R.plurals.recover_report_looked, looked, looked)
            else -> stringResource(R.string.recover_none)
        }
    }

    val sections = listOf(
        R.string.recover_section_sources to listOf(Stage.SOURCE, Stage.SKIPPED),
        R.string.recover_section_mirrors to listOf(Stage.MIRROR),
        R.string.recover_section_archive to listOf(Stage.ARCHIVE),
        R.string.recover_section_limits to listOf(Stage.LIMIT),
    ).map { (titleRes, stages) ->
        titleRes to stages.flatMap { s -> attempts.filter { it.stage == s } }.sortedBy { if (it.grade != null) 0 else 1 }
    }.filter { it.second.isNotEmpty() }

    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                if (success) Icons.Filled.CheckCircle else Icons.Filled.SearchOff,
                contentDescription = null,
                tint = if (success) toneColor(ReasonTone.FOUND) else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            }
            if (attempts.isNotEmpty()) {
                val text = (listOf(title) + attempts.map { a -> "[${a.stage.name.lowercase()}] ${a.label}${a.url?.let { " $it" } ?: ""} - ${a.reason}" }).joinToString("\n")
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.6f))
                    Text(
                        stringResource(if (copied) R.string.recover_report_copied else R.string.recover_report_copy),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recover_report_close), tint = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        if (sections.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { (titleRes, rows) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(titleRes).uppercase(),
                            color = Color.White.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        rows.forEach { a -> ReportRow(a, onOpen = { url -> runCatching { uriHandler.openUri(url) } }) }
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun ReportRow(a: Attempt, onOpen: (String) -> Unit) {
    val reason = describeReason(a)
    val color = toneColor(reason.tone)
    val path = shortPath(a.url)
    Column(modifier = Modifier.fillMaxWidth().then(if (reason.tone == ReasonTone.SKIP) Modifier.padding(0.dp) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(toneIcon(reason.tone), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(a.label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                path,
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (path.isNotEmpty() && a.url != null) Modifier.clickable { onOpen(a.url) } else Modifier),
            )
            Text(reason.text, color = color, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        reason.hint?.let {
            Text(it, color = Color(0xFFFDE68A).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp))
        }
    }
}
