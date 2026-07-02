package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEditorFocusTest {
    @Test
    fun focusTargetAfterDeletingUsesPreviousFocusableBlock() {
        val blocks = listOf(
            PageBlock(id = "first", type = PageBlockType.Text),
            PageBlock(id = "second", type = PageBlockType.Text),
            PageBlock(id = "third", type = PageBlockType.Text),
        )

        assertEquals("second", blocks.editorFocusTargetAfterDeleting("third"))
    }

    @Test
    fun focusTargetAfterDeletingFallsBackToNextFocusableBlock() {
        val blocks = listOf(
            PageBlock(id = "first", type = PageBlockType.Text),
            PageBlock(id = "second", type = PageBlockType.Text),
        )

        assertEquals("second", blocks.editorFocusTargetAfterDeleting("first"))
    }

    @Test
    fun focusTargetAfterDeletingSkipsNonFocusableBlocks() {
        val blocks = listOf(
            PageBlock(id = "first", type = PageBlockType.Text),
            PageBlock(id = "divider", type = PageBlockType.Divider),
            PageBlock(id = "table", type = PageBlockType.DatabaseTable),
            PageBlock(id = "target", type = PageBlockType.Text),
        )

        assertEquals("first", blocks.editorFocusTargetAfterDeleting("target"))
        assertFalse(blocks.containsFocusableEditorBlock("divider"))
        assertFalse(blocks.containsFocusableEditorBlock("table"))
        assertTrue(blocks.containsFocusableEditorBlock("first"))
    }

    @Test
    fun focusTargetAfterDeletingFollowsNestedVisualOrder() {
        val blocks = listOf(
            PageBlock(
                id = "parent",
                type = PageBlockType.Text,
                children = listOf(
                    PageBlock(id = "child-1", type = PageBlockType.Text),
                    PageBlock(id = "child-2", type = PageBlockType.Text),
                ),
            ),
            PageBlock(id = "after-parent", type = PageBlockType.Text),
        )

        assertEquals("child-1", blocks.editorFocusTargetAfterDeleting("child-2"))
        assertEquals("child-2", blocks.editorFocusTargetAfterDeleting("after-parent"))
    }
}
