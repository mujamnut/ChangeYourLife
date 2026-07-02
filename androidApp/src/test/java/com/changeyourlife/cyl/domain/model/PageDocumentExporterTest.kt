package com.changeyourlife.cyl.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDocumentExporterTest {
    @Test
    fun exportsBlocksAndInlineSpansToMarkdown() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "title",
                    type = PageBlockType.Heading,
                    text = "Budget",
                    richTextSpans = listOf(PageTextSpan(start = 0, end = 6, bold = true)),
                ),
                PageBlock(id = "todo", type = PageBlockType.Todo, text = "Pay rent", isChecked = true),
                PageBlock(
                    id = "parent",
                    type = PageBlockType.Bullet,
                    text = "Food",
                    children = listOf(PageBlock(id = "child", type = PageBlockType.Bullet, text = "Rice")),
                ),
            ),
        )

        assertEquals(
            """
            # **Budget**
            - [x] Pay rent
            - Food
              - Rice
            """.trimIndent(),
            PageDocumentExporter.toMarkdown(document),
        )
    }

    @Test
    fun exportsTableToMarkdown() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "table",
                    type = PageBlockType.DatabaseTable,
                    table = PageTable(
                        title = "Expenses",
                        columns = listOf(
                            PageTableColumn(id = "name", name = "Name"),
                            PageTableColumn(id = "amount", name = "Amount"),
                        ),
                        rows = listOf(PageTableRow(id = "row", cells = mapOf("name" to "Food", "amount" to "29"))),
                    ),
                ),
            ),
        )

        assertEquals(
            """
            ### Expenses
            | Name | Amount |
            | --- | --- |
            | Food | 29 |
            """.trimIndent(),
            PageDocumentExporter.toMarkdown(document),
        )
    }

    @Test
    fun exportsHtmlWithEscapingAndLinks() {
        val document = PageBlockDocument(
            blocks = listOf(
                PageBlock(
                    id = "link",
                    type = PageBlockType.Text,
                    text = "Open <CYL>",
                    richTextSpans = listOf(PageTextSpan(start = 0, end = 4, linkUrl = "https://cyl.test")),
                ),
            ),
        )

        val html = PageDocumentExporter.toHtml(document)

        assertTrue(html.contains("<a href=\"https://cyl.test\">Open</a>"))
        assertTrue(html.contains("&lt;CYL&gt;"))
    }
}
