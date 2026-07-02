package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        assertEquals(PageBlockType.DatabaseTable, matches.single().type)
    }
}
