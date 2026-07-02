package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextSlashCommandParserTest {
    @Test
    fun activeQueryDetectsSlashAtStartOfBlock() {
        val query = RichTextSlashCommandParser.activeQuery("/todo", cursor = 5)

        assertEquals(RichTextSlashQuery(start = 0, end = 5, query = "todo"), query)
    }

    @Test
    fun activeQueryDetectsSlashAfterWhitespace() {
        val query = RichTextSlashCommandParser.activeQuery("hello /table", cursor = 12)

        assertEquals(RichTextSlashQuery(start = 6, end = 12, query = "table"), query)
    }

    @Test
    fun activeQueryIgnoresSlashInsideWord() {
        val query = RichTextSlashCommandParser.activeQuery("http://example", cursor = 7)

        assertNull(query)
    }

    @Test
    fun matchingCommandsUsesAliases() {
        val matches = RichTextSlashCommandParser.matchingCommands("db")
        val action = matches.single().action

        assertTrue(action is RichTextSlashAction.ChangeType)
        assertEquals(PageBlockType.DatabaseTable, (action as RichTextSlashAction.ChangeType).type)
    }

    @Test
    fun matchingCommandsCanInsertTextBelow() {
        val matches = RichTextSlashCommandParser.matchingCommands("below")
        val action = matches.single().action

        assertTrue(action is RichTextSlashAction.InsertBlock)
        action as RichTextSlashAction.InsertBlock
        assertEquals(PageBlockType.Text, action.type)
        assertEquals(PageBlockInsertPosition.Below, action.position)
    }

    @Test
    fun matchingCommandsSupportsNumberedList() {
        val action = RichTextSlashCommandParser.matchingCommands("ol").single().action

        assertTrue(action is RichTextSlashAction.ChangeType)
        assertEquals(PageBlockType.Numbered, (action as RichTextSlashAction.ChangeType).type)
    }

    @Test
    fun matchingCommandsSupportsLinkedPage() {
        val action = RichTextSlashCommandParser.matchingCommands("page").single().action

        assertEquals(RichTextSlashAction.CreateLinkedPage, action)
    }

    @Test
    fun matchingCommandsSupportsContextPropertySheet() {
        val action = RichTextSlashCommandParser.matchingCommands("property").single().action

        assertEquals(RichTextSlashAction.OpenPropertySheet, action)
    }

    @Test
    fun matchingCommandsSupportsIndentAndOutdent() {
        val indentAction = RichTextSlashCommandParser.matchingCommands("indent").single().action
        val outdentAction = RichTextSlashCommandParser.matchingCommands("outdent").single().action

        assertEquals(RichTextSlashAction.IndentBlock, indentAction)
        assertEquals(RichTextSlashAction.OutdentBlock, outdentAction)
    }
}
