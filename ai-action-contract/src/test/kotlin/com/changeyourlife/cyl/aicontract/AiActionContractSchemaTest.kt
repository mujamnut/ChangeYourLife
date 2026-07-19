package com.changeyourlife.cyl.aicontract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiActionContractSchemaTest {
    @Test
    fun normalizesAliasAndReturnsSealedRowAction() {
        val result = AiActionContractSchema.parse(
            actionIndex = 3,
            payload = AiActionWire(
                type = "add table row",
                tableTitle = "Transactions",
                rowTitle = "Food",
                cellValues = mapOf("Amount" to "4"),
            ),
        )

        assertTrue(result.isValid)
        assertEquals("ADD_TABLE_ROW", result.normalizedPayload.type)
        val action = assertIs<CylAiAction.Row>(result.action)
        assertEquals(AiActionDomain.Row, action.domain)
        assertEquals("Transactions", action.tableTitle)
        assertEquals("Food", action.rowTitle)
        assertEquals(mapOf("Amount" to "4"), action.cellValues)
    }

    @Test
    fun rejectsFieldsFromAnotherDomain() {
        val result = AiActionContractSchema.parse(
            actionIndex = 0,
            payload = AiActionWire(
                type = "ADD_TABLE_ROW",
                tableTitle = "Transactions",
                rowTitle = "Fuel",
                columnName = "Category",
                options = listOf("Fuel"),
            ),
        )

        assertFalse(result.isValid)
        assertEquals("columnName,options", result.issues.single().field)
        assertEquals("unexpected_action_fields", result.issues.single().code)
    }

    @Test
    fun everySupportedTypeResolvesToExactlyOneDomain() {
        assertTrue(AiActionContractSchema.supportedTypes.isNotEmpty())
        AiActionContractSchema.supportedTypes.forEach { type ->
            assertTrue(AiActionContractSchema.domainFor(type) != null, type)
        }
    }

    @Test
    fun wireSerializationStaysFlatForOlderClients() {
        val encoded = Json.encodeToString(
            AiActionWire(
                type = "CLEAR_TABLE_CELL",
                tableTitle = "Transactions",
                rowTitle = "Food",
                columnName = "Month",
            ),
        )

        assertTrue("\"type\":\"CLEAR_TABLE_CELL\"" in encoded)
        assertTrue("\"tableTitle\":\"Transactions\"" in encoded)
        assertFalse("\"payload\"" in encoded)
        assertFalse("\"domain\"" in encoded)
    }
}
