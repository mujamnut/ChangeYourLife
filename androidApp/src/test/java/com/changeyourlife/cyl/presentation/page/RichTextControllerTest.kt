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
    fun controllerKeepsPendingTypingFormatForInsertedText() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Hello ", selection = TextRange(6)),
            ),
        )

        controller.toggleBold()
        val state = controller.updateText(TextFieldValue("Hello world", selection = TextRange(11)))

        assertTrue(RichTextFormat.Bold in state.activeFormats)
        assertEquals(listOf(PageTextSpan(start = 6, end = 11, bold = true)), state.spans)
    }

    @Test
    fun controllerKeepsPendingTypingStyleForInsertedText() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Hello ", selection = TextRange(6)),
            ),
        )

        controller.applyColor("#1565C0")
        controller.applyHighlight("#FFF59D")
        controller.applyLink("https://example.test")
        val state = controller.updateText(TextFieldValue("Hello world", selection = TextRange(11)))

        assertEquals(
            listOf(
                PageTextSpan(
                    start = 6,
                    end = 11,
                    linkUrl = "https://example.test",
                    color = "#1565C0",
                    highlight = "#FFF59D",
                ),
            ),
            state.spans,
        )
    }

    @Test
    fun controllerPreservesSpansWhenOnlySelectionChanges() {
        val controller = RichTextController(
            RichTextEditorState(
                blockId = "block-1",
                value = TextFieldValue("Hello", selection = TextRange(0, 5)),
                spans = listOf(PageTextSpan(start = 0, end = 5, bold = true)),
            ),
        )

        val state = controller.updateText(TextFieldValue("Hello", selection = TextRange(5)))

        assertEquals(listOf(PageTextSpan(start = 0, end = 5, bold = true)), state.spans)
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
            1. Rent
            [ ] Pay bill
            See [Budget](https://budget.test)
            """.trimIndent(),
        )

        assertEquals(PageBlockType.Heading, blocks[0].type)
        assertEquals("Plan", blocks[0].text)
        assertEquals(PageBlockType.Bullet, blocks[1].type)
        assertEquals("Food", blocks[1].text)
        assertEquals(listOf(PageTextSpan(start = 0, end = 4, bold = true)), blocks[1].spans)
        assertEquals(PageBlockType.Numbered, blocks[2].type)
        assertEquals("Rent", blocks[2].text)
        assertEquals(PageBlockType.Todo, blocks[3].type)
        assertEquals("Pay bill", blocks[3].text)
        assertEquals(PageBlockType.Text, blocks[4].type)
        assertEquals("See Budget", blocks[4].text)
        assertEquals(listOf(PageTextSpan(start = 4, end = 10, linkUrl = "https://budget.test")), blocks[4].spans)
    }

    @Test
    fun pasteParserConvertsLightHtmlToBlocks() {
        val blocks = RichTextPasteParser.parse("<h1>Plan</h1><ul><li><strong>Food</strong></li></ul>")

        assertEquals(PageBlockType.Heading, blocks[0].type)
        assertEquals("Plan", blocks[0].text)
        assertEquals(PageBlockType.Bullet, blocks[1].type)
        assertEquals("Food", blocks[1].text)
        assertEquals(listOf(PageTextSpan(start = 0, end = 4, bold = true)), blocks[1].spans)
    }

    @Test
    fun pasteParserPreservesRichHtmlBlocksAndInlineStyles() {
        val blocks = RichTextPasteParser.parse(
            """
            <h2>Budget</h2>
            <p><strong>Food</strong> and <em>Fuel</em>
            <a href="https://budget.test"><span style="color: #1565c0; background-color: rgb(255,245,157); text-decoration: underline line-through;">Link</span></a></p>
            <ol><li>Rent</li></ol>
            <ul><li><input type="checkbox" checked> Pay bill</li></ul>
            <blockquote>Note</blockquote>
            <pre>code</pre>
            """.trimIndent(),
        )

        assertEquals(PageBlockType.Heading, blocks[0].type)
        assertEquals("Budget", blocks[0].text)
        assertEquals(PageBlockType.Text, blocks[1].type)
        assertEquals("Food and Fuel Link", blocks[1].text)
        assertEquals(
            listOf(
                PageTextSpan(start = 0, end = 4, bold = true),
                PageTextSpan(start = 9, end = 13, italic = true),
                PageTextSpan(
                    start = 14,
                    end = 18,
                    underline = true,
                    strikethrough = true,
                    linkUrl = "https://budget.test",
                    color = "#1565C0",
                    highlight = "#FFF59D",
                ),
            ),
            blocks[1].spans,
        )
        assertEquals(PageBlockType.Numbered, blocks[2].type)
        assertEquals("Rent", blocks[2].text)
        assertEquals(PageBlockType.Todo, blocks[3].type)
        assertTrue(blocks[3].isChecked)
        assertEquals("Pay bill", blocks[3].text)
        assertEquals(PageBlockType.Quote, blocks[4].type)
        assertEquals("Note", blocks[4].text)
        assertEquals(PageBlockType.Text, blocks[5].type)
        assertEquals("code", blocks[5].text)
        assertEquals(listOf(PageTextSpan(start = 0, end = 4, code = true)), blocks[5].spans)
    }

    @Test
    fun pasteParserMergesSingleRichHtmlBlockIntoExistingText() {
        val blocks = RichTextPasteParser.mergeRichClipboardTextChangeIntoBlocks(
            currentType = PageBlockType.Text,
            currentIsChecked = false,
            oldValue = TextFieldValue("Hello old world", selection = TextRange(6, 9)),
            newValue = TextFieldValue("Hello Food world", selection = TextRange(10)),
            oldSpans = listOf(
                PageTextSpan(start = 0, end = 5, bold = true),
                PageTextSpan(start = 10, end = 15, italic = true),
            ),
            clipboardHtmlText = "<strong>Food</strong>",
        )

        assertEquals(1, blocks.size)
        assertEquals(PageBlockType.Text, blocks[0].type)
        assertEquals("Hello Food world", blocks[0].text)
        assertEquals(
            listOf(
                PageTextSpan(start = 0, end = 5, bold = true),
                PageTextSpan(start = 6, end = 10, bold = true),
                PageTextSpan(start = 11, end = 16, italic = true),
            ),
            blocks[0].spans,
        )
    }

    @Test
    fun enterInPlainTextStaysInsideCurrentBlock() {
        val blocks = RichTextBlockInteractionParser.splitEnterChange(
            currentType = PageBlockType.Text,
            currentIsChecked = false,
            oldValue = TextFieldValue("Hello world", selection = TextRange(5)),
            newValue = TextFieldValue("Hello\n world", selection = TextRange(6)),
            oldSpans = listOf(PageTextSpan(start = 6, end = 11, italic = true)),
        )

        assertEquals(emptyList<RichTextPasteBlock>(), blocks)
    }

    @Test
    fun enterSplitCreatesSiblingBlocksForTodoAndKeepsAfterTextSpans() {
        val blocks = RichTextBlockInteractionParser.splitEnterChange(
            currentType = PageBlockType.Todo,
            currentIsChecked = true,
            oldValue = TextFieldValue("Hello world", selection = TextRange(5)),
            newValue = TextFieldValue("Hello\n world", selection = TextRange(6)),
            oldSpans = listOf(PageTextSpan(start = 6, end = 11, italic = true)),
        )

        assertEquals(2, blocks.size)
        assertEquals(PageBlockType.Todo, blocks[0].type)
        assertEquals("Hello", blocks[0].text)
        assertEquals(true, blocks[0].isChecked)
        assertEquals(PageBlockType.Todo, blocks[1].type)
        assertEquals(" world", blocks[1].text)
        assertEquals(false, blocks[1].isChecked)
        assertEquals(listOf(PageTextSpan(start = 1, end = 6, italic = true)), blocks[1].spans)
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
