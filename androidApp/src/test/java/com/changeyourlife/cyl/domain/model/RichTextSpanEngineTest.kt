package com.changeyourlife.cyl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextSpanEngineTest {
    @Test
    fun normalizeClampsDropsEmptyAndMergesAdjacentSpans() {
        val spans = listOf(
            PageTextSpan(start = -4, end = 2, bold = true),
            PageTextSpan(start = 2, end = 4, bold = true),
            PageTextSpan(start = 4, end = 4, italic = true),
            PageTextSpan(start = 4, end = 20),
        )

        val normalized = RichTextSpanEngine.normalize(spans, text = "Hello")

        assertEquals(
            listOf(PageTextSpan(start = 0, end = 4, bold = true)),
            normalized,
        )
    }

    @Test
    fun toggleFormatAppliesAndRemovesStyleAcrossSelection() {
        val text = "Hello world"
        val bold = RichTextSpanEngine.toggleFormat(
            spans = emptyList(),
            format = RichTextFormat.Bold,
            start = 0,
            end = 5,
            textLength = text.length,
        )

        assertEquals(listOf(PageTextSpan(start = 0, end = 5, bold = true)), bold)
        assertTrue(RichTextSpanEngine.hasFormat(bold, RichTextFormat.Bold, 0, 5))

        val removed = RichTextSpanEngine.toggleFormat(
            spans = bold,
            format = RichTextFormat.Bold,
            start = 1,
            end = 4,
            textLength = text.length,
        )

        assertEquals(
            listOf(
                PageTextSpan(start = 0, end = 1, bold = true),
                PageTextSpan(start = 4, end = 5, bold = true),
            ),
            removed,
        )
        assertFalse(RichTextSpanEngine.hasFormat(removed, RichTextFormat.Bold, 1, 4))
    }

    @Test
    fun adjustForTextChangeMovesSpansAfterInsertion() {
        val adjusted = RichTextSpanEngine.adjustForTextChange(
            spans = listOf(PageTextSpan(start = 6, end = 11, italic = true)),
            oldText = "Hello world",
            newText = "Hello big world",
        )

        assertEquals(
            listOf(PageTextSpan(start = 10, end = 15, italic = true)),
            adjusted,
        )
    }

    @Test
    fun adjustForTextChangeShrinksSpanTouchedByDeletion() {
        val adjusted = RichTextSpanEngine.adjustForTextChange(
            spans = listOf(PageTextSpan(start = 0, end = 11, underline = true)),
            oldText = "Hello world",
            newText = "Hello",
        )

        assertEquals(
            listOf(PageTextSpan(start = 0, end = 5, underline = true)),
            adjusted,
        )
    }
}
