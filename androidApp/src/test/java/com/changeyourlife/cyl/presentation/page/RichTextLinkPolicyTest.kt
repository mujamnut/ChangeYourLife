package com.changeyourlife.cyl.presentation.page

import androidx.compose.ui.text.TextRange
import com.changeyourlife.cyl.domain.model.PageTextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextLinkPolicyTest {
    @Test
    fun selectedLinkUrlReadsCollapsedCursorInsideLink() {
        val spans = listOf(PageTextSpan(start = 2, end = 8, linkUrl = "example.com"))

        assertEquals("example.com", RichTextLinkPolicy.selectedLinkUrl(spans, TextRange(5)))
    }

    @Test
    fun selectedLinkUrlReadsSelectionCoveredByLink() {
        val spans = listOf(PageTextSpan(start = 2, end = 8, linkUrl = "example.com"))

        assertEquals("example.com", RichTextLinkPolicy.selectedLinkUrl(spans, TextRange(3, 7)))
    }

    @Test
    fun normalizedOpenUrlAddsHttpsWhenSchemeMissing() {
        assertEquals("https://example.com", RichTextLinkPolicy.normalizedOpenUrl(" example.com "))
        assertEquals("mailto:me@example.com", RichTextLinkPolicy.normalizedOpenUrl("mailto:me@example.com"))
    }
}
