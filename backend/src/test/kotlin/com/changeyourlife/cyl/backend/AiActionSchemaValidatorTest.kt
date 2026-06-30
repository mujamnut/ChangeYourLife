package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.service.AiActionSchemaValidator
import com.changeyourlife.cyl.backend.service.AiService
import kotlin.test.Test
import kotlin.test.assertEquals

class AiActionSchemaValidatorTest {
    private val validator = AiActionSchemaValidator()

    @Test
    fun normalizesSupportedActionType() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "add table row",
                    rowTitle = "Makeup",
                ),
            ),
        )

        assertEquals(emptyList(), result.issues)
        assertEquals("ADD_TABLE_ROW", result.actions.single().type)
    }

    @Test
    fun rejectsUnsupportedActionType() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "CREATE_DATABASE_WITH_MAGIC",
                    title = "Budget",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("type", issue.field)
        assertEquals("unsupported_action_type", issue.code)
    }

    @Test
    fun rejectsMediaFileBlockWithoutUri() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "ADD_BLOCK",
                    blockType = "MediaFile",
                    title = "Receipt",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("mediaUri", issue.field)
        assertEquals("missing_required_field", issue.code)
    }

    @Test
    fun rejectsFormulaColumnWithoutFormulaValueOrContent() {
        val result = validator.validate(
            listOf(
                AiService.AiActionItem(
                    type = "UPDATE_FORMULA_COLUMN",
                    columnName = "Total",
                ),
            ),
        )

        assertEquals(emptyList(), result.actions)
        val issue = result.issues.single()
        assertEquals("formula", issue.field)
        assertEquals("missing_required_field", issue.code)
    }
}
