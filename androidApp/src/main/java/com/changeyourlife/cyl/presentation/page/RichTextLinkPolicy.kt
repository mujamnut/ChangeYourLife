package com.changeyourlife.cyl.presentation.page

import androidx.compose.ui.text.TextRange
import com.changeyourlife.cyl.domain.model.PageTextSpan

internal object RichTextLinkPolicy {
    fun selectedLinkUrl(
        spans: List<PageTextSpan>,
        range: TextRange,
    ): String {
        val start = range.min
        val end = range.max
        return spans.firstOrNull { span ->
            span.linkUrl.isNotBlank() &&
                if (start == end) {
                    start >= span.start && start <= span.end
                } else {
                    span.start <= start && span.end >= end
                }
        }?.linkUrl.orEmpty()
    }

    fun normalizedOpenUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val schemeEnd = trimmed.indexOf(':')
        val hasScheme = schemeEnd > 0 &&
            trimmed.take(schemeEnd).all { char -> char.isLetterOrDigit() || char == '+' || char == '-' || char == '.' }
        return if (hasScheme) trimmed else "https://$trimmed"
    }
}
