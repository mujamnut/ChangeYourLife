package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.RichTextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSuggestionControllerTest {
    @Test
    fun resolvesSlashQueryThroughCommandRegistry() {
        val state = EditorSuggestionController.resolve(
            text = "/db",
            cursor = 3,
            mentionPages = emptyList(),
        )

        requireNotNull(state)
        assertEquals(RichTextCommandPaletteKind.Slash, state.query.kind)
        assertEquals("db", state.query.query)
        assertEquals("Table", state.items.single().title)
        assertEquals(EditorCommandGroup.Database.label, state.items.single().groupLabel)
    }

    @Test
    fun resolvesMentionQueryWithPageIdentity() {
        val page = testPage(id = "budget", title = "Budget Tracker")

        val state = EditorSuggestionController.resolve(
            text = "Open @bud",
            cursor = 9,
            mentionPages = listOf(page),
        )

        requireNotNull(state)
        assertEquals(RichTextCommandPaletteKind.Mention, state.query.kind)
        assertEquals("bud", state.query.query)
        assertEquals(page.paletteItemId(), state.items.single().id)
        assertEquals("@Budget Tracker", state.items.single().title)
    }

    @Test
    fun moveSelectionWrapsAroundItems() {
        val state = EditorSuggestionState(
            query = EditorSuggestionQuery(
                kind = RichTextCommandPaletteKind.Slash,
                start = 0,
                end = 1,
                query = "",
            ),
            items = listOf(
                paletteItem("one"),
                paletteItem("two"),
                paletteItem("three"),
            ),
            selectedIndex = 0,
        )

        assertEquals(2, EditorSuggestionController.moveSelection(state, delta = -1).selectedIndex)
        assertEquals(1, EditorSuggestionController.moveSelection(state, delta = 1).selectedIndex)
    }

    @Test
    fun registryCanDisableContextualCommands() {
        val matches = EditorCommandRegistry.matchingSlashCommands(
            query = "property",
            context = EditorCommandContext(canEditTableProperties = false),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun codeBlockContextDisablesSlashSuggestions() {
        val state = EditorSuggestionController.resolve(
            text = "/table",
            cursor = 6,
            mentionPages = emptyList(),
            context = EditorCommandContext(isCodeBlock = true),
        )

        assertNull(state)
    }

    @Test
    fun registryExposesBlockTypeCommandsForBlockMenus() {
        val types = EditorCommandRegistry
            .changeTypeEntries()
            .mapNotNull { entry -> entry.changeTypeOrNull() }

        assertTrue(PageBlockType.Text in types)
        assertTrue(PageBlockType.Heading in types)
        assertTrue(PageBlockType.DatabaseTable in types)
    }

    @Test
    fun registryExposesInsertCommandsForBlockMenus() {
        val belowAction = EditorCommandRegistry
            .insertBlockEntries(position = PageBlockInsertPosition.Below)
            .single()
            .insertBlockActionOrNull()

        requireNotNull(belowAction)
        assertEquals(PageBlockType.Text, belowAction.type)
        assertEquals(PageBlockInsertPosition.Below, belowAction.position)
    }

    @Test
    fun registryExposesRichTextToolbarActions() {
        val entries = EditorCommandRegistry.richTextToolbarEntries()
        val ids = entries.map { entry -> entry.id }
        val formats = entries.mapNotNull { entry ->
            (entry.action as? RichTextToolbarRegistryAction.ToggleFormat)?.format
        }

        assertTrue(RichTextFormat.Bold in formats)
        assertTrue(RichTextFormat.Italic in formats)
        assertTrue(RichTextFormat.Code in formats)
        assertTrue("link" in ids)
        assertTrue("color:blue" in ids)
        assertTrue("highlight:yellow" in ids)
    }

    private fun paletteItem(id: String): RichTextCommandPaletteItem {
        return RichTextCommandPaletteItem(
            id = id,
            title = id,
            subtitle = "",
            leading = "/",
            kind = RichTextCommandPaletteKind.Slash,
        )
    }

    private fun testPage(
        id: String,
        title: String,
    ): Page {
        return Page(
            id = id,
            workspaceId = "workspace",
            parentPageId = null,
            title = title,
            content = "",
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )
    }
}
