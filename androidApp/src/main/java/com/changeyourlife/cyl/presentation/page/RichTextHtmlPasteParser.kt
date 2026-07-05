package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextSpanEngine

internal object RichTextHtmlPasteParser {
    private val htmlTokenRegex = Regex("(?is)<[^>]+>|[^<]+")

    fun parse(html: String): List<RichTextPasteBlock> {
        if (!html.looksLikeHtml()) return emptyList()
        val parser = HtmlPasteParser()
        htmlTokenRegex.findAll(html).forEach { match ->
            val token = match.value
            if (token.startsWith("<")) {
                parser.consumeTag(token)
            } else {
                parser.appendText(token)
            }
        }
        return parser.finish()
    }
}

private class HtmlPasteParser {
    private val blocks = mutableListOf<RichTextPasteBlock>()
    private val styleStack = mutableListOf(HtmlInlineStyle())
    private val listStack = mutableListOf<PageBlockType>()
    private var currentBlock: HtmlBlockBuilder? = null
    private var preDepth = 0

    fun consumeTag(rawTag: String) {
        val tag = rawTag.toHtmlTag() ?: return
        when {
            tag.isClosing -> closeTag(tag.name)
            tag.isSelfClosing -> openSelfClosingTag(tag)
            else -> openTag(tag)
        }
    }

    fun appendText(rawText: String) {
        val decoded = rawText.decodeHtmlEntities()
        val text = if (preDepth > 0) decoded else decoded.collapseHtmlWhitespace()
        if (text.isEmpty()) return
        val block = currentBlock ?: startBlock(PageBlockType.Text)
        block.appendText(text, styleStack.last())
    }

    fun finish(): List<RichTextPasteBlock> {
        flushCurrentBlock()
        return blocks
    }

    private fun openTag(tag: HtmlTag) {
        when {
            tag.name.matches(Regex("h[1-6]")) -> startBlock(PageBlockType.Heading)
            tag.name in paragraphTags -> startBlock(PageBlockType.Text)
            tag.name == "blockquote" -> startBlock(PageBlockType.Quote)
            tag.name == "ul" -> listStack += PageBlockType.Bullet
            tag.name == "ol" -> listStack += PageBlockType.Numbered
            tag.name == "li" -> startBlock(listStack.lastOrNull() ?: PageBlockType.Bullet)
            tag.name == "pre" -> {
                preDepth += 1
                startBlock(PageBlockType.Code)
                pushStyle { copy(code = true) }
            }
            tag.name == "tr" -> startBlock(PageBlockType.Text)
            tag.name in tableCellTags -> appendCellSeparatorIfNeeded()
            tag.name in boldTags -> pushStyle { copy(bold = true) }
            tag.name in italicTags -> pushStyle { copy(italic = true) }
            tag.name in underlineTags -> pushStyle { copy(underline = true) }
            tag.name in strikeTags -> pushStyle { copy(strikethrough = true) }
            tag.name in codeTags -> pushStyle { copy(code = true) }
            tag.name == "a" -> pushStyle { copy(linkUrl = tag.attributes["href"].orEmpty().decodeHtmlEntities()) }
            tag.name == "mark" -> pushStyle { copy(highlight = "#FFF59D") }
            tag.name == "span" || tag.name == "font" -> pushStyle { withHtmlAttributes(tag.attributes) }
            tag.name == "input" -> applyInputTag(tag)
        }
    }

    private fun closeTag(name: String) {
        when {
            name.matches(Regex("h[1-6]")) -> flushCurrentBlock()
            name in paragraphTags -> flushCurrentBlock()
            name == "blockquote" -> flushCurrentBlock()
            name == "li" -> flushCurrentBlock()
            name == "ul" || name == "ol" -> {
                flushCurrentBlock()
                if (listStack.isNotEmpty()) listStack.removeAt(listStack.lastIndex)
            }
            name == "pre" -> {
                popStyle()
                preDepth = (preDepth - 1).coerceAtLeast(0)
                flushCurrentBlock()
            }
            name == "tr" -> flushCurrentBlock()
            name in boldTags ||
                name in italicTags ||
                name in underlineTags ||
                name in strikeTags ||
                name in codeTags ||
                name == "a" ||
                name == "mark" ||
                name == "span" ||
                name == "font" -> popStyle()
            name == "table" -> flushCurrentBlock()
        }
    }

    private fun openSelfClosingTag(tag: HtmlTag) {
        when {
            tag.name == "br" -> flushCurrentBlock()
            tag.name == "input" -> applyInputTag(tag)
            tag.name in tableCellTags -> appendCellSeparatorIfNeeded()
        }
    }

    private fun startBlock(type: PageBlockType): HtmlBlockBuilder {
        val existing = currentBlock
        if (existing != null && existing.hasContent) {
            flushCurrentBlock()
        }
        return HtmlBlockBuilder(type).also {
            currentBlock = it
        }
    }

    private fun flushCurrentBlock() {
        currentBlock
            ?.toPasteBlock()
            ?.let(blocks::add)
        currentBlock = null
    }

    private fun appendCellSeparatorIfNeeded() {
        currentBlock
            ?.takeIf { block -> block.hasContent && !block.textEndsWithWhitespace() }
            ?.appendText("\t", HtmlInlineStyle())
    }

    private fun applyInputTag(tag: HtmlTag) {
        if (tag.attributes["type"]?.equals("checkbox", ignoreCase = true) != true) return
        val block = currentBlock ?: startBlock(PageBlockType.Todo)
        block.type = PageBlockType.Todo
        block.isChecked = tag.attributes.containsKey("checked")
    }

    private fun pushStyle(transform: HtmlInlineStyle.() -> HtmlInlineStyle) {
        styleStack += styleStack.last().transform()
    }

    private fun popStyle() {
        if (styleStack.size > 1) {
            styleStack.removeAt(styleStack.lastIndex)
        }
    }
}

private class HtmlBlockBuilder(
    var type: PageBlockType,
) {
    private val text = StringBuilder()
    private val spans = mutableListOf<PageTextSpan>()
    var isChecked: Boolean = false

    val hasContent: Boolean
        get() = text.any { char -> !char.isWhitespace() }

    fun textEndsWithWhitespace(): Boolean = text.lastOrNull()?.isWhitespace() == true

    fun appendText(value: String, style: HtmlInlineStyle) {
        if (value.isEmpty()) return
        val normalizedValue = if (text.isEmpty()) value.trimStart() else value
        if (normalizedValue.isEmpty()) return
        val start = text.length
        text.append(normalizedValue)
        val end = text.length
        if (style.hasAnyStyle && start < end) {
            spans += style.toSpan(start = start, end = end)
        }
    }

    fun toPasteBlock(): RichTextPasteBlock? {
        val untrimmed = text.toString()
        val trimStart = untrimmed.indexOfFirst { char -> !char.isWhitespace() }
        if (trimStart < 0) return null
        val trimEnd = untrimmed.indexOfLast { char -> !char.isWhitespace() } + 1
        val finalText = untrimmed.substring(trimStart, trimEnd)
        if (finalText.isBlank()) return null
        val finalSpans = spans.mapNotNull { span ->
            val start = maxOf(span.start, trimStart) - trimStart
            val end = minOf(span.end, trimEnd) - trimStart
            if (start >= end) null else span.copy(start = start, end = end)
        }
        return RichTextPasteBlock(
            type = type,
            text = finalText,
            spans = RichTextSpanEngine.normalize(finalSpans, finalText),
            isChecked = isChecked,
        )
    }
}

private data class HtmlInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String = "",
    val color: String = "",
    val highlight: String = "",
) {
    val hasAnyStyle: Boolean
        get() = bold ||
            italic ||
            underline ||
            strikethrough ||
            code ||
            linkUrl.isNotBlank() ||
            color.isNotBlank() ||
            highlight.isNotBlank()

    fun withHtmlAttributes(attributes: Map<String, String>): HtmlInlineStyle {
        val style = attributes["style"].orEmpty()
        val css = style.parseCssDeclarations()
        return copy(
            bold = bold || css["font-weight"].isBoldFontWeight(),
            italic = italic || css["font-style"]?.contains("italic", ignoreCase = true) == true,
            underline = underline || css["text-decoration"].hasTextDecoration("underline"),
            strikethrough = strikethrough || css["text-decoration"].hasTextDecoration("line-through"),
            color = css["color"]?.toCanonicalCssColor().orEmpty().ifBlank {
                attributes["color"]?.toCanonicalCssColor().orEmpty().ifBlank { color }
            },
            highlight = (css["background-color"] ?: css["background"])
                ?.toCanonicalCssColor()
                .orEmpty()
                .ifBlank { highlight },
        )
    }

    fun toSpan(start: Int, end: Int): PageTextSpan {
        return PageTextSpan(
            start = start,
            end = end,
            bold = bold,
            italic = italic,
            underline = underline,
            strikethrough = strikethrough,
            code = code,
            linkUrl = linkUrl,
            color = color,
            highlight = highlight,
        )
    }
}

private data class HtmlTag(
    val name: String,
    val attributes: Map<String, String>,
    val isClosing: Boolean,
    val isSelfClosing: Boolean,
)

private fun String.toHtmlTag(): HtmlTag? {
    val clean = trim()
    if (!clean.startsWith("<") || !clean.endsWith(">")) return null
    if (clean.startsWith("<!--") || clean.startsWith("<!")) return null
    val body = clean.removePrefix("<").removeSuffix(">").trim()
    val isClosing = body.startsWith("/")
    val isSelfClosing = body.endsWith("/") || body.startsWith("br", ignoreCase = true)
    val tagBody = body
        .removePrefix("/")
        .removeSuffix("/")
        .trim()
    val name = tagBody
        .substringBefore(" ")
        .substringBefore("\t")
        .lowercase()
    if (name.isBlank()) return null
    return HtmlTag(
        name = name,
        attributes = tagBody.parseHtmlAttributes(),
        isClosing = isClosing,
        isSelfClosing = isSelfClosing,
    )
}

private fun String.parseHtmlAttributes(): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    val regex = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>/]+))""")
    regex.findAll(this).forEach { match ->
        val name = match.groupValues[1].lowercase()
        val value = listOf(match.groupValues[3], match.groupValues[4], match.groupValues[5])
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .decodeHtmlEntities()
        attributes[name] = value
    }
    val booleanAttributeRegex = Regex("""\s(checked|disabled|selected)\b""", RegexOption.IGNORE_CASE)
    booleanAttributeRegex.findAll(this).forEach { match ->
        attributes[match.groupValues[1].lowercase()] = "true"
    }
    return attributes
}

private fun String.parseCssDeclarations(): Map<String, String> {
    return split(';')
        .mapNotNull { declaration ->
            val name = declaration.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
            val value = declaration.substringAfter(':', missingDelimiterValue = "").trim()
            if (name.isBlank() || value.isBlank()) null else name to value
        }
        .toMap()
}

private fun String?.isBoldFontWeight(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return false
    if (value.contains("bold", ignoreCase = true)) return true
    return value.toIntOrNull()?.let { weight -> weight >= 600 } == true
}

private fun String?.hasTextDecoration(decoration: String): Boolean {
    return this?.contains(decoration, ignoreCase = true) == true
}

private fun String.toCanonicalCssColor(): String {
    val value = trim()
        .lowercase()
        .substringBefore("!important")
        .trim()
    if (value.isBlank() || value == "transparent" || value == "none") return ""
    if (value.startsWith("#")) {
        val hex = value.removePrefix("#").takeWhile { char -> char.isLetterOrDigit() }
        return when (hex.length) {
            3 -> "#${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}".uppercase()
            6 -> "#${hex.uppercase()}"
            else -> ""
        }
    }
    if (value.startsWith("rgb")) {
        val numbers = value
            .substringAfter("(")
            .substringBefore(")")
            .split(',')
            .mapNotNull { part -> part.trim().substringBefore('.').toIntOrNull() }
        if (numbers.size >= 3) {
            return "#${numbers.take(3).joinToString("") { number ->
                number.coerceIn(0, 255).toString(16).padStart(2, '0')
            }}".uppercase()
        }
    }
    return namedCssColors[value].orEmpty()
}

private fun String.looksLikeHtml(): Boolean {
    return contains('<') &&
        contains('>') &&
        Regex("(?is)<\\s*/?\\s*[a-z][a-z0-9:-]*(\\s|>|/)").containsMatchIn(this)
}

private fun String.collapseHtmlWhitespace(): String {
    return replace(Regex("\\s+"), " ")
}

internal fun String.decodeHtmlEntities(): String {
    return replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]+);")) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x", ignoreCase = true) -> {
                entity.drop(2).toIntOrNull(radix = 16)?.toChar()?.toString()
            }
            entity.startsWith("#") -> {
                entity.drop(1).toIntOrNull()?.toChar()?.toString()
            }
            else -> htmlEntities[entity.lowercase()]
        } ?: match.value
    }
}

private val paragraphTags = setOf("p", "div", "section", "article")
private val tableCellTags = setOf("td", "th")
private val boldTags = setOf("strong", "b")
private val italicTags = setOf("em", "i")
private val underlineTags = setOf("u", "ins")
private val strikeTags = setOf("s", "strike", "del")
private val codeTags = setOf("code", "kbd", "samp")

private val htmlEntities = mapOf(
    "nbsp" to " ",
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
)

private val namedCssColors = mapOf(
    "black" to "#000000",
    "white" to "#FFFFFF",
    "red" to "#FF0000",
    "green" to "#008000",
    "blue" to "#0000FF",
    "yellow" to "#FFFF00",
    "orange" to "#FFA500",
    "purple" to "#800080",
    "gray" to "#808080",
    "grey" to "#808080",
)
