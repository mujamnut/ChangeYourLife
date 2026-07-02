package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPlaceholderPolicyTest {
    @Test
    fun firstTextBlockHintsAtSlashAndAi() {
        assertEquals(
            "Write, type / for blocks, or ask AI",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isFirstBlock = true),
            ),
        )
    }

    @Test
    fun focusedTextBlockHintsAtSlashAndMention() {
        assertEquals(
            "Type / for blocks or @ to mention",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isFocused = true),
            ),
        )
    }

    @Test
    fun rowPageTextBlockUsesRowNotesHint() {
        assertEquals(
            "Add row notes",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isTableRowPage = true),
            ),
        )
    }

    @Test
    fun nonTextBlocksUseTightLabels() {
        assertEquals(
            "Heading",
            EditorPlaceholderPolicy.placeholderFor(EditorPlaceholderContext(type = PageBlockType.Heading)),
        )
        assertEquals(
            "Caption",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.MediaFile, isMediaCaption = true),
            ),
        )
    }
}
