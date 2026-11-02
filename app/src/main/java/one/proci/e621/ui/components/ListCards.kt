package one.proci.e621.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import one.proci.e621.ui.theme.CardShape
import one.proci.e621.ui.theme.PillShape
import one.proci.e621.ui.theme.tabular
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/** Content padding for a LazyColumn of [ListCard]s: a small gutter around the stack. */
val ListCardContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/** Gap between stacked [ListCard]s; use with `verticalArrangement = Arrangement.spacedBy(...)`. */
val ListCardSpacing = Arrangement.spacedBy(6.dp)

/**
 * A row in a list screen (forum topics, messages, comments, ...): a rounded tonal card that
 * replaces the old full-bleed row + divider. [onClick] is optional for read-only rows.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = CardShape, color = color, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** The "loading the next page" footer at the bottom of a paged list. */
@Composable
fun LoadingMoreFooter() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        RainbowSpinner(modifier = Modifier.size(24.dp))
    }
}

/**
 * A titled horizontal bar chart in a [ListCard] (dashboard + favorites analysis): label, a pill
 * bar on a faint track scaled to the largest value, and a tabular count.
 */
@Composable
fun BarChartCard(title: String, rows: List<Pair<String, Int>>) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    ListCard {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        rows.forEach { (label, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.4f), maxLines = 1)
                Box(
                    modifier = Modifier
                        .weight(0.48f)
                        .padding(end = 8.dp)
                        .height(12.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(12.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.12f),
                )
            }
        }
    }
}
