package com.changeyourlife.cyl.presentation.page

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.RichTextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextControllerTest {
    @Test
    fun controllerTogglesCodeOverSelection() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Hello", selection = TextRange(0, 5)),
            ),
        )

        val state = controller.toggleCode()

        assertEquals(listOf(PageTextSpan(start = 0, end = 5, code = true)), state.spans)
        assertTrue(RichTextFormat.Code in state.activeFormats)
    }

    @Test
    fun controllerAppliesLinkOverCurrentWordWhenSelectionIsCollapsed() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Open Budget", selection = TextRange(7)),
            ),
        )

        val state = controller.applyLink("https://example.test")

        assertEquals(
            listOf(PageTextSpan(start = 5, end = 11, linkUrl = "https://example.test")),
            state.spans,
        )
    }

    @Test
    fun mentionParserDetectsMentionAfterWhitespaceOnly() {
        assertEquals(
            RichTextMentionQuery(start = 5, end = 12, query = "Budget"),
            RichTextMentionParser.activeQuery("Open @Budget", cursor = 12),
        )
        assertNull(RichTextMentionParser.activeQuery("me@test", cursor = 7))
    }

    @Test
    fun controllerReplacesMentionQueryWithMentionSpan() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Open @Bud", selection = TextRange(9)),
            ),
        )

        val state = controller.replaceRangeWithMention(
            range = TextRange(5, 9),
            pageId = "page-1",
            title = "Budget Tracker",
        )

        assertEquals("Open @Budget Tracker ", state.value.text)
        assertEquals(
            listOf(
                PageTextSpan(
                    start = 5,
                    end = 20,
                    mentionPageId = "page-1",
                    mentionLabel = "@Budget Tracker",
                ),
            ),
            state.spans,
        )
    }

    @Test
    fun pasteParserTurnsLightMarkdownIntoBlocksAndInlineSpans() {
        val blocks = RichTextPasteParser.parse(
            """
            # Plan
            - **Food**
            [ ] Pay bill
            See [Budget](https://budget.test)
            """.trimIndent(),
        )

        assertEquals(PageBlockType.Heading, blocks[0].type)
        assertEquals("Plan", blocks[0].text)
        assertEquals(PageBlockType.Bullet, blocks[1].type)
        assertEquals("Food", blocks[1].text)
        assertEquals(listOf(PageTextSpan(start = 0, end = 4, bold = true)), blocks[1].spans)
        assertEquals(PageBlockType.Todo, blocks[2].type)
        assertEquals("Pay bill", blocks[2].text)
        assertEquals(PageBlockType.Text, blocks[3].type)
        assertEquals("See Budget", blocks[3].text)
        assertEquals(listOf(PageTextSpan(start = 4, end = 10, linkUrl = "https://budget.test")), blocks[3].spans)
    }

    @Test
    fun pasteParserMergesMultiLinePasteIntoNonEmptySelection() {
        val blocks = RichTextPasteParser.mergeTextChangeIntoBlocks(
            currentType = PageBlockType.Text,
            currentIsChecked = false,
            oldValue = TextFieldValue("Hello old world", selection = TextRange(6, 9)),
            newValue = TextFieldValue("Hello # Plan\n- **Food** world", selection = TextRange(24)),
            oldSpans = listOf(
                PageTextSpan(start = 0, end = 5, bold = true),
                PageTextSpan(start = 10, end = 15, italic = true),
            ),
        )

        assertEquals(2, blocks.size)
        assertEquals(PageBlockType.Text, blocks[0].type)
        assertEquals("Hello Plan", blocks[0].text)
        assertEquals(listOf(PageTextSpan(start = 0, end = 5, bold = true)), blocks[0].spans)
        assertEquals(PageBlockType.Text, blocks[1].type)
        assertEquals("Food world", blocks[1].text)
        assertEquals(
            listOf(
                PageTextSpan(start = 0, end = 4, bold = true),
                PageTextSpan(start = 5, end = 10, italic = true),
            ),
            blocks[1].spans,
        )
    }

    @Test
    fun pasteParserUsesPastedTypesWhenSelectionReplacesWholeBlock() {
        val blocks = RichTextPasteParser.mergeTextChangeIntoBlocks(
            currentType = PageBlockType.Text,
            currentIsChecked = false,
            oldValue = TextFieldValue("Old", selection = TextRange(0, 3)),
            newValue = TextFieldValue("# Title\n- Item", selection = TextRange(14)),
            oldSpans = emptyList(),
        )

        assertEquals(2, blocks.size)
        assertEquals(PageBlockType.Heading, blocks[0].type)
        assertEquals("Title", blocks[0].text)
        assertEquals(PageBlockType.Bullet, blocks[1].type)
        assertEquals("Item", blocks[1].text)
    }
}
