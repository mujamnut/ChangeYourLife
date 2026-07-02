package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.Page
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextCommandPaletteTest {
    @Test
    fun slashPaletteItemsPreserveCommandIdentity() {
        val command = RichTextSlashCommandParser.matchingCommands("db").single()
        val item = richTextSlashPaletteItems(listOf(command)).single()

        assertEquals(command.paletteItemId(), item.id)
        assertEquals("Table", item.title)
        assertEquals("Database table", item.subtitle)
        assertEquals("/", item.leading)
        assertEquals(RichTextCommandPaletteKind.Slash, item.kind)
        assertEquals(EditorCommandGroup.Database.label, item.groupLabel)
    }

    @Test
    fun mentionPaletteItemsPreservePageIdentity() {
        val page = Page(
            id = "page-1",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = "",
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

        val item = richTextMentionPaletteItems(listOf(page)).single()

        assertEquals(page.paletteItemId(), item.id)
        assertEquals("@Budget Tracker", item.title)
        assertEquals("Mention page", item.subtitle)
        assertEquals("@", item.leading)
        assertEquals(RichTextCommandPaletteKind.Mention, item.kind)
        assertEquals(EditorCommandGroup.Page.label, item.groupLabel)
    }

    @Test
    fun mentionPaletteUsesFallbackTitleForUntitledPage() {
        val page = Page(
            id = "page-2",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "",
            content = "",
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

        val item = richTextMentionPaletteItems(listOf(page)).single()

        assertEquals("@Untitled page", item.title)
    }
}
