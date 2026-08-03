package com.changeyourlife.cyl.data.share

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine
import com.changeyourlife.cyl.domain.repository.IncomingShareContentMapper
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidIncomingShareContentMapper @Inject constructor() : IncomingShareContentMapper {
    override suspend fun map(items: List<IncomingShareItem>): List<PageBlock> =
        withContext(Dispatchers.Default) {
            val staged = items
                .asSequence()
                .filter { item -> item.status == IncomingShareItemStatus.STAGED }
                .sortedBy(IncomingShareItem::position)
                .toList()

            val contentBlocks = staged.flatMap { item ->
                when (item.kind) {
                    IncomingShareItemKind.TEXT -> item.text.orEmpty().toPlainBlocks()
                    IncomingShareItemKind.HTML -> item.toHtmlBlocks()
                    IncomingShareItemKind.URL -> item.text
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { url ->
                            listOf(
                                PageContentCodec.newBlock(PageBlockType.WebBookmark).copy(text = url),
                            )
                        }
                        .orEmpty()
                    IncomingShareItemKind.STREAM -> emptyList()
                }
            }
            val attachments = staged
                .filter { item -> item.kind == IncomingShareItemKind.STREAM }
                .map { item ->
                    PageMediaAttachment(
                        id = item.id,
                        assetId = item.id,
                        uri = "",
                        name = item.displayName.ifBlank { "Untitled file" },
                        mimeType = item.resolvedMimeType,
                        sizeBytes = item.sizeBytes,
                    )
                }
            buildList {
                addAll(contentBlocks)
                if (attachments.isNotEmpty()) {
                    add(
                        PageContentCodec.newBlock(PageBlockType.MediaFile).copy(
                            mediaAttachments = attachments,
                        ),
                    )
                }
            }
        }

    private fun IncomingShareItem.toHtmlBlocks(): List<PageBlock> {
        val safeHtml = html.orEmpty().sanitizePassiveHtml()
        if (safeHtml.isBlank()) return text.orEmpty().toPlainBlocks()
        val spanned = HtmlCompat.fromHtml(safeHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val blocks = spanned.toPageBlocks()
        return blocks.ifEmpty { text.orEmpty().toPlainBlocks() }
    }
}

private fun String.toPlainBlocks(): List<PageBlock> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    return normalized
        .split('\n')
        .mapNotNull { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@mapNotNull null
            val trimmed = line.trimStart()
            val (type, text, checked) = when {
                trimmed.startsWith("# ") -> Triple(PageBlockType.Heading, trimmed.removePrefix("# "), false)
                trimmed.startsWith("- [x] ", ignoreCase = true) ->
                    Triple(PageBlockType.Todo, trimmed.substring(6), true)
                trimmed.startsWith("- [ ] ") -> Triple(PageBlockType.Todo, trimmed.substring(6), false)
                NumberedPrefixRegex.containsMatchIn(trimmed) ->
                    Triple(PageBlockType.Numbered, trimmed.replaceFirst(NumberedPrefixRegex, ""), false)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    Triple(PageBlockType.Bullet, trimmed.substring(2), false)
                trimmed.startsWith("> ") -> Triple(PageBlockType.Quote, trimmed.substring(2), false)
                else -> Triple(PageBlockType.Text, line, false)
            }
            PageContentCodec.newBlock(type).copy(text = text, isChecked = checked)
        }
        .ifEmpty {
            normalized.trim().takeIf(String::isNotBlank)?.let { text ->
                listOf(PageContentCodec.newBlock(PageBlockType.Text).copy(text = text))
            }.orEmpty()
        }
}

private fun String.sanitizePassiveHtml(): String =
    replace(HtmlCommentRegex, "")
        .replace(DangerousElementRegex, "")
        .replace(DangerousStandaloneTagRegex, "")

private fun Spanned.toPageBlocks(): List<PageBlock> {
    val source = toString().replace("\r\n", "\n").replace('\r', '\n')
    if (source.isBlank()) return emptyList()
    val ranges = buildList {
        var start = 0
        source.forEachIndexed { index, char ->
            if (char == '\n') {
                if (source.substring(start, index).isNotBlank()) add(start until index)
                start = index + 1
            }
        }
        if (start < source.length && source.substring(start).isNotBlank()) add(start until source.length)
    }
    return ranges.map { range ->
        val raw = source.substring(range)
        val leftTrim = raw.indexOfFirst { char -> !char.isWhitespace() }.coerceAtLeast(0)
        val rightTrim = raw.indexOfLast { char -> !char.isWhitespace() }.coerceAtLeast(leftTrim) + 1
        val start = range.first + leftTrim
        val end = range.first + rightTrim
        val text = source.substring(start, end)
        val styles = getSpans(start, end, Any::class.java)
        val type = when {
            styles.any { span -> span is BulletSpan } -> PageBlockType.Bullet
            styles.any { span -> span is QuoteSpan } -> PageBlockType.Quote
            styles.filterIsInstance<RelativeSizeSpan>().any { span -> span.sizeChange > 1.15f } -> PageBlockType.Heading
            else -> PageBlockType.Text
        }
        val spans = styles.mapNotNull { style -> style.toPageSpan(this, start, end) }
        PageContentCodec.newBlock(type).copy(
            text = text,
            richTextSpans = RichTextSpanEngine.normalize(spans, text),
        )
    }
}

private fun Any.toPageSpan(
    text: Spanned,
    blockStart: Int,
    blockEnd: Int,
): PageTextSpan? {
    val start = text.getSpanStart(this).coerceAtLeast(blockStart) - blockStart
    val end = text.getSpanEnd(this).coerceAtMost(blockEnd) - blockStart
    if (start >= end) return null
    return when (this) {
        is StyleSpan -> when (style) {
            Typeface.BOLD -> PageTextSpan(start, end, bold = true)
            Typeface.ITALIC -> PageTextSpan(start, end, italic = true)
            Typeface.BOLD_ITALIC -> PageTextSpan(start, end, bold = true, italic = true)
            else -> null
        }
        is UnderlineSpan -> PageTextSpan(start, end, underline = true)
        is StrikethroughSpan -> PageTextSpan(start, end, strikethrough = true)
        is URLSpan -> PageTextSpan(start, end, linkUrl = url.orEmpty())
        is ForegroundColorSpan -> PageTextSpan(start, end, color = foregroundColor.toHexColor())
        is BackgroundColorSpan -> PageTextSpan(start, end, highlight = backgroundColor.toHexColor())
        else -> null
    }
}

private fun Int.toHexColor(): String = "#%08X".format(this)

private val NumberedPrefixRegex = Regex("^\\d+[.)]\\s+")
private val HtmlCommentRegex = Regex("(?is)<!--.*?-->")
private val DangerousElementRegex = Regex(
    "(?is)<(script|style|iframe|object|embed|form|button|textarea|select)[^>]*>.*?</\\1\\s*>",
)
private val DangerousStandaloneTagRegex = Regex(
    "(?is)</?(script|style|iframe|object|embed|form|input|button|textarea|select|meta|link)[^>]*>",
)
