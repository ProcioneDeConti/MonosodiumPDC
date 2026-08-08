package one.proci.e621.data.dtext

import java.net.URLEncoder

/**
 * A practical subset of e621's DText markup (its own BBCode-like language, parsed server-side by
 * the `dtext` gem - not Markdown, despite how it's often described). Covers what actually shows up
 * in comments/dmails/forum posts day-to-day: bold/italic/underline/strike/superscript/subscript/
 * tiny text, spoilers, code blocks, quotes, simple lists, headings, named links, bare URLs, wiki
 * links, and @mentions. Deliberately does not attempt the full grammar (tables, sections, colored
 * text, nested-tag edge cases, etc).
 */
sealed class DBlock {
    data class Paragraph(val segments: List<DSegment>) : DBlock()
    data class Heading(val level: Int, val segments: List<DSegment>) : DBlock()
    data class CodeBlock(val text: String) : DBlock()
    data class QuoteBlock(val children: List<DBlock>) : DBlock()
    data class ListBlock(val items: List<List<DSegment>>) : DBlock()
}

/** A paragraph/list-item/heading is split at [spoiler] boundaries so each spoiler run can be independently reveal-on-tap. */
data class DSegment(val spoiler: Boolean, val nodes: List<DInline>)

sealed class DInline {
    data class PlainText(val text: String) : DInline()
    data class Styled(val tag: String, val children: List<DInline>) : DInline()
    data class Link(val label: String, val url: String) : DInline()
    data class Mention(val name: String) : DInline()
}

private val HeadingRegex = Regex("""^h([1-6])\.\s*(.*)$""")
private val OpenTagRegex = Regex("""\[(b|i|u|s|sup|sub|tn)\]""", RegexOption.IGNORE_CASE)
private val NamedLinkRegex = Regex(""""([^"\n]+)":(\S+)""")
private val WikiLinkRegex = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?\]\]""")
private val BareUrlRegex = Regex("""https?://\S+""")
private val MentionRegex = Regex("""(?<![\w@])@([A-Za-z0-9_-]+)""")

fun parseDText(raw: String): List<DBlock> = parseMixed(raw.replace("\r\n", "\n"))

/**
 * Pulls out [quote]/[code] blocks wherever they occur in the raw text - real e621 quotes are
 * commonly written as `[quote]Username said:\n...\n[/quote]`, i.e. `[quote]` sharing a line with
 * content rather than sitting alone on its own line, so these can't be detected by scanning
 * whole trimmed lines. [quote] nests (a quoted reply can itself contain a quote); [code] doesn't.
 * Whatever text is left over (not inside a quote/code block) is handed to the line-based
 * paragraph/list/heading splitter.
 */
private fun parseMixed(text: String): List<DBlock> {
    val blocks = mutableListOf<DBlock>()
    val plainBuffer = StringBuilder()
    var remaining = text

    fun flushPlain() {
        if (plainBuffer.isNotEmpty()) {
            blocks += parseLines(plainBuffer.toString().split("\n"))
            plainBuffer.clear()
        }
    }

    while (remaining.isNotEmpty()) {
        val quoteIdx = remaining.indexOf("[quote]", ignoreCase = true)
        val codeIdx = remaining.indexOf("[code]", ignoreCase = true)
        val isQuote = quoteIdx >= 0 && (codeIdx < 0 || quoteIdx <= codeIdx)
        val isCode = !isQuote && codeIdx >= 0
        if (!isQuote && !isCode) {
            plainBuffer.append(remaining)
            break
        }
        if (isQuote) {
            plainBuffer.append(remaining.substring(0, quoteIdx))
            flushPlain()
            val afterOpen = remaining.substring(quoteIdx + "[quote]".length)
            var depth = 1
            var searchFrom = 0
            var closeIdx = -1
            while (true) {
                val nextOpen = afterOpen.indexOf("[quote]", searchFrom, ignoreCase = true)
                val nextClose = afterOpen.indexOf("[/quote]", searchFrom, ignoreCase = true)
                if (nextClose == -1) break
                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++
                    searchFrom = nextOpen + "[quote]".length
                } else {
                    depth--
                    if (depth == 0) {
                        closeIdx = nextClose
                        break
                    }
                    searchFrom = nextClose + "[/quote]".length
                }
            }
            if (closeIdx == -1) {
                blocks += DBlock.QuoteBlock(parseMixed(afterOpen))
                remaining = ""
            } else {
                blocks += DBlock.QuoteBlock(parseMixed(afterOpen.substring(0, closeIdx)))
                remaining = afterOpen.substring(closeIdx + "[/quote]".length)
            }
        } else {
            plainBuffer.append(remaining.substring(0, codeIdx))
            flushPlain()
            val afterOpen = remaining.substring(codeIdx + "[code]".length)
            val closeIdx = afterOpen.indexOf("[/code]", ignoreCase = true)
            if (closeIdx == -1) {
                blocks += DBlock.CodeBlock(afterOpen.trim('\n'))
                remaining = ""
            } else {
                blocks += DBlock.CodeBlock(afterOpen.substring(0, closeIdx).trim('\n'))
                remaining = afterOpen.substring(closeIdx + "[/code]".length)
            }
        }
    }
    flushPlain()
    return blocks
}

private fun parseLines(lines: List<String>): List<DBlock> {
    val blocks = mutableListOf<DBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += DBlock.Paragraph(splitSpoilerSegments(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        val headingMatch = HeadingRegex.find(trimmed)
        when {
            trimmed.equals("[list]", ignoreCase = true) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().equals("[/list]", ignoreCase = true)) {
                    val t = lines[i].trim()
                    if (t.isNotEmpty()) {
                        items += when {
                            t.startsWith("[*]") -> t.removePrefix("[*]").trim()
                            t.startsWith("*") -> t.removePrefix("*").trim()
                            else -> t
                        }
                    }
                    i++
                }
                if (i < lines.size) i++
                blocks += DBlock.ListBlock(items.map { splitSpoilerSegments(it) })
            }
            headingMatch != null -> {
                flushParagraph()
                blocks += DBlock.Heading(headingMatch.groupValues[1].toInt(), splitSpoilerSegments(headingMatch.groupValues[2]))
                i++
            }
            trimmed.isEmpty() -> {
                flushParagraph()
                i++
            }
            else -> {
                paragraphBuffer += lines[i]
                i++
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun splitSpoilerSegments(text: String): List<DSegment> {
    val segments = mutableListOf<DSegment>()
    var remaining = text
    while (true) {
        val openIdx = remaining.indexOf("[spoiler]", ignoreCase = true)
        if (openIdx == -1) {
            if (remaining.isNotEmpty()) segments += DSegment(false, parseInline(remaining))
            break
        }
        if (openIdx > 0) segments += DSegment(false, parseInline(remaining.substring(0, openIdx)))
        val afterOpen = remaining.substring(openIdx + "[spoiler]".length)
        val closeIdx = afterOpen.indexOf("[/spoiler]", ignoreCase = true)
        if (closeIdx == -1) {
            if (afterOpen.isNotEmpty()) segments += DSegment(true, parseInline(afterOpen))
            break
        }
        segments += DSegment(true, parseInline(afterOpen.substring(0, closeIdx)))
        remaining = afterOpen.substring(closeIdx + "[/spoiler]".length)
    }
    return segments
}

private fun parseInline(input: String): List<DInline> {
    val nodes = mutableListOf<DInline>()
    var remaining = input
    while (remaining.isNotEmpty()) {
        val openTag = OpenTagRegex.find(remaining)
        val namedLink = NamedLinkRegex.find(remaining)
        val wikiLink = WikiLinkRegex.find(remaining)
        val bareUrl = BareUrlRegex.find(remaining)
        val mention = MentionRegex.find(remaining)
        val match = listOfNotNull(openTag, namedLink, wikiLink, bareUrl, mention).minByOrNull { it.range.first }

        if (match == null) {
            nodes += DInline.PlainText(remaining)
            break
        }
        if (match.range.first > 0) {
            nodes += DInline.PlainText(remaining.substring(0, match.range.first))
        }
        when (match) {
            openTag -> {
                val tag = match.groupValues[1].lowercase()
                val afterOpen = remaining.substring(match.range.last + 1)
                val closeMarker = "[/$tag]"
                val closeIdx = afterOpen.indexOf(closeMarker, ignoreCase = true)
                if (closeIdx == -1) {
                    nodes += DInline.PlainText(match.value)
                    remaining = afterOpen
                } else {
                    nodes += DInline.Styled(tag, parseInline(afterOpen.substring(0, closeIdx)))
                    remaining = afterOpen.substring(closeIdx + closeMarker.length)
                }
            }
            namedLink -> {
                nodes += DInline.Link(match.groupValues[1], normalizeUrl(match.groupValues[2]))
                remaining = remaining.substring(match.range.last + 1)
            }
            wikiLink -> {
                val page = match.groupValues[1].trim()
                val display = match.groupValues[2].ifBlank { page }.trim()
                nodes += DInline.Link(display, "https://e621.net/wiki_pages/show_or_new?title=" + encodeWikiTitle(page))
                remaining = remaining.substring(match.range.last + 1)
            }
            bareUrl -> {
                nodes += DInline.Link(match.value, match.value)
                remaining = remaining.substring(match.range.last + 1)
            }
            mention -> {
                nodes += DInline.Mention(match.groupValues[1])
                remaining = remaining.substring(match.range.last + 1)
            }
            else -> remaining = ""
        }
    }
    return nodes
}

private fun normalizeUrl(url: String): String = if (url.startsWith("/")) "https://e621.net$url" else url

private fun encodeWikiTitle(page: String): String = URLEncoder.encode(page.replace(' ', '_'), "UTF-8")
