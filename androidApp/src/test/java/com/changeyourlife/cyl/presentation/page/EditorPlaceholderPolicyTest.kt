package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPlaceholderPolicyTest {
    @Test
    fun textBlocksStayVisuallyBlank() {
        assertEquals(
            "",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isFirstBlock = true),
            ),
        )
        assertEquals(
            "",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isFocused = true),
            ),
        )
        assertEquals(
            "",
            EditorPlaceholderPolicy.placeholderFor(
                EditorPlaceholderContext(type = PageBlockType.Text, isTableRowPage = true),
            ),
        )
    }

    @Test
    fun nonTextBlocksUseTightLabels() {
        assertEquals(
            "",
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
