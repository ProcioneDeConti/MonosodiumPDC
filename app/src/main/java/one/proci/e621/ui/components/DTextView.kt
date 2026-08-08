package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import one.proci.e621.data.dtext.DBlock
import one.proci.e621.data.dtext.DInline
import one.proci.e621.data.dtext.DSegment
import one.proci.e621.data.dtext.parseDText

/** Renders e621 DText (see [one.proci.e621.data.dtext.parseDText]) - used for comment/dmail/forum post bodies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DTextView(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    if (text.isBlank()) return
    val blocks = remember(text) { parseDText(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { DBlockView(it, style, linkColor) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DBlockView(block: DBlock, style: TextStyle, linkColor: Color) {
    when (block) {
        is DBlock.Paragraph -> SegmentsFlow(block.segments, style, linkColor, Modifier.fillMaxWidth())
        is DBlock.Heading -> SegmentsFlow(
            block.segments,
            style.copy(fontWeight = FontWeight.Bold, fontSize = style.fontSize * headingScale(block.level)),
            linkColor,
            Modifier.fillMaxWidth(),
        )
        is DBlock.CodeBlock -> Text(
            text = block.text,
            style = style.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
        )
        is DBlock.QuoteBlock -> Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.children.forEach { DBlockView(it, style, linkColor) }
            }
        }
        is DBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("•  ", style = style)
                    SegmentsFlow(item, style, linkColor, Modifier.weight(1f))
                }
            }
        }
    }
}

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.5f
    2 -> 1.35f
    3 -> 1.2f
    4 -> 1.1f
    5 -> 1.0f
    else -> 0.95f
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentsFlow(segments: List<DSegment>, style: TextStyle, linkColor: Color, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier) {
        segments.forEach { seg ->
            if (seg.spoiler) {
                SpoilerText(seg.nodes, style, linkColor)
            } else {
                Text(text = buildAnnotated(seg.nodes, linkColor), style = style)
            }
        }
    }
}

@Composable
private fun SpoilerText(nodes: List<DInline>, style: TextStyle, linkColor: Color) {
    var revealed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .let {
                if (revealed) {
                    it
                } else {
                    it.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { revealed = true }
                }
            },
    ) {
        Text(text = buildAnnotated(nodes, linkColor), style = style)
        if (!revealed) {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

private fun buildAnnotated(nodes: List<DInline>, linkColor: Color): AnnotatedString = buildAnnotatedString {
    fun appendNodes(nodes: List<DInline>) {
        nodes.forEach { node ->
            when (node) {
                is DInline.PlainText -> append(node.text)
                is DInline.Mention -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = linkColor)) {
                    append("@${node.name}")
                }
                is DInline.Link -> withLink(
                    LinkAnnotation.Url(node.url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))),
                ) {
                    append(node.label)
                }
                is DInline.Styled -> {
                    val spanStyle = when (node.tag) {
                        "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "i" -> SpanStyle(fontStyle = FontStyle.Italic)
                        "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
                        "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)
                        "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.7.em)
                        "tn" -> SpanStyle(fontSize = 0.75.em)
                        else -> SpanStyle()
                    }
                    withStyle(spanStyle) { appendNodes(node.children) }
                }
            }
        }
    }
    appendNodes(nodes)
}
