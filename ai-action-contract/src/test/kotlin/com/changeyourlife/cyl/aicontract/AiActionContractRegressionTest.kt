package com.changeyourlife.cyl.aicontract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val ExpectedActionCount = 95

class AiActionContractRegressionInvariantTest {
    @Test
    fun fixtureRegistryCoversExactlyAllSharedActions() {
        assertEquals(ExpectedActionCount, AiActionContractSchema.supportedTypes.size)
        assertEquals(
            AiActionContractSchema.supportedTypes,
            AiActionRegressionFixtures.payloads.keys,
        )
    }

    @Test
    fun generatedPromptCatalogAdvertisesEveryActionExactlyOnce() {
        val catalog = AiActionContractSchema.promptInstructions()

        AiActionContractSchema.supportedTypes.forEach { actionType ->
            val token = Regex(
                pattern = "(?<![A-Z0-9_])${Regex.escape(actionType)}(?![A-Z0-9_])",
            )
            assertEquals(
                expected = 1,
                actual = token.findAll(catalog).count(),
                message = "$actionType must appear exactly once in the generated prompt catalog.",
            )
        }
    }
}

@RunWith(Parameterized::class)
class AiActionContractActionRegressionTest(
    private val actionType: String,
    private val payload: AiActionWire,
) {
    @Test
    fun validFixtureParsesIntoItsRegisteredDomain() {
        val result = AiActionContractSchema.parse(
            actionIndex = 0,
            payload = payload,
        )

        assertTrue(
            actual = result.isValid,
            message = "$actionType failed contract validation: ${result.issues.joinToString { it.message }}",
        )
        assertEquals(actionType, result.normalizedPayload.type)
        assertEquals(
            AiActionContractSchema.domainFor(actionType),
            assertNotNull(result.action).domain,
        )
    }

    @Test
    fun crossDomainFieldIsRejected() {
        val domain = assertNotNull(AiActionContractSchema.domainFor(actionType))
        val invalidPayload = AiActionRegressionFixtures.withCrossDomainField(
            payload = payload,
            domain = domain,
        )
        val result = AiActionContractSchema.parse(
            actionIndex = 0,
            payload = invalidPayload,
        )

        assertFalse(result.isValid, "$actionType accepted a field from another action domain.")
        assertTrue(
            result.issues.any { issue -> issue.code == "unexpected_action_fields" },
            "$actionType did not report unexpected_action_fields: ${result.issues}",
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun actionFixtures(): List<Array<Any>> =
            AiActionRegressionFixtures.payloads.map { (actionType, payload) ->
                arrayOf(actionType, payload)
            }
    }
}

private object AiActionRegressionFixtures {
    val payloads: Map<String, AiActionWire> =
        AiActionContractSchema.supportedTypes
            .sorted()
            .associateWith(::validPayload)

    fun withCrossDomainField(
        payload: AiActionWire,
        domain: AiActionDomain,
    ): AiActionWire = when (domain) {
        AiActionDomain.Page -> payload.copy(rowBlockId = "cross-domain-row-block")
        AiActionDomain.Block -> payload.copy(sourcePageId = "cross-domain-source-page")
        AiActionDomain.Property -> payload.copy(blockId = "cross-domain-block")
        AiActionDomain.Database -> payload.copy(rowBlockId = "cross-domain-row-block")
        AiActionDomain.Column -> payload.copy(rowBlockId = "cross-domain-row-block")
        AiActionDomain.Row -> payload.copy(blockId = "cross-domain-block")
        AiActionDomain.RowContent -> payload.copy(sourcePageId = "cross-domain-source-page")
        AiActionDomain.Cell,
        AiActionDomain.Task,
        AiActionDomain.Reminder,
        -> payload.copy(blockId = "cross-domain-block")
    }

    private fun validPayload(type: String): AiActionWire = when (type) {
        "RENAME_CURRENT_PAGE", "RENAME_PAGE" -> pageTarget(type).copy(title = "Renamed page")
        "UPDATE_PAGE" -> pageTarget(type).copy(content = "Updated page content")
        "CREATE_PAGE" -> AiActionWire(type = type, title = "Created page", content = "Page content")
        "CREATE_SUBPAGE" -> pageTarget(type).copy(title = "Created subpage", content = "Subpage content")
        "MOVE_PAGE" -> pageTarget(type).copy(parentPageTitle = "Archive")
        "DUPLICATE_PAGE" -> pageTarget(type).copy(title = "Budget copy")
        "TRASH_PAGE", "RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY" -> pageTarget(type)

        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" ->
            pageTarget(type).copy(content = "New block", blockType = "Text")

        "DELETE_ALL_BLOCKS" -> pageTarget(type)
        "DELETE_BLOCK" -> pageTarget(type).copy(blockId = "block-text")
        "MOVE_BLOCK" -> pageTarget(type).copy(blockId = "block-text", moveDirection = "up")
        "INDENT_BLOCK", "OUTDENT_BLOCK", "DUPLICATE_BLOCK" ->
            pageTarget(type).copy(blockId = "block-text")
        "UPDATE_BLOCK", "EDIT_BLOCK" ->
            pageTarget(type).copy(blockId = "block-text", content = "Updated block")

        "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" ->
            pageTarget(type).copy(blockId = "block-todo", isChecked = type != "UNCHECK_BLOCK")

        "FORMAT_BLOCK_TEXT" -> pageTarget(type).copy(
            blockId = "block-text",
            textToFormat = "budget",
            format = "Bold",
        )

        "ADD_PROPERTY" -> pageTarget(type).copy(
            propertyName = "Budget",
            propertyType = "Number",
            value = "100",
        )

        "UPDATE_PROPERTY" -> pageTarget(type).copy(propertyName = "Budget", value = "200")
        "DELETE_PROPERTY" -> pageTarget(type).copy(propertyName = "Budget")
        "RENAME_PROPERTY" -> pageTarget(type).copy(
            propertyName = "Budget",
            newPropertyName = "Monthly budget",
        )
        "MOVE_PROPERTY" -> pageTarget(type).copy(propertyName = "Budget", targetIndex = 1)
        "DUPLICATE_PROPERTY" -> pageTarget(type).copy(
            propertyName = "Budget",
            newPropertyName = "Budget copy",
        )

        "CREATE_DATABASE", "CREATE_TABLE" -> pageTarget(type).copy(
            tableTitle = "Transactions",
            tableColumns = listOf(
                AiTableColumnWire(name = "Name", type = "Text"),
                AiTableColumnWire(name = "Amount", type = "Number"),
            ),
        )

        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" ->
            tableTarget(type).copy(title = "Renamed transactions")
        "DUPLICATE_DATABASE" -> tableTarget(type).copy(title = "Transactions copy")
        "ATTACH_TABLE_DATA_SOURCE" -> tableTarget(type).copy(
            sourcePageTitle = "Sales",
            sourceTableTitle = "Orders",
        )
        "CLEAR_TABLE_DATA_SOURCE" -> tableTarget(type)

        "ADD_TABLE_COLUMN" -> tableTarget(type).copy(
            columnName = "Category",
            columnType = "Select",
            options = listOf("Food", "Fuel"),
        )

        "DELETE_TABLE_COLUMN" -> tableTarget(type).copy(columnName = "Category")
        "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> tableTarget(type).copy(
            columnName = "Category",
            newColumnName = "Expense category",
        )

        "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE" ->
            tableTarget(type).copy(columnName = "Amount", columnType = "Number")

        "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG" -> tableTarget(type).copy(
            columnName = "Category",
            columnType = "Select",
            options = listOf("Food", "Fuel", "Other"),
        )

        "UPDATE_FORMULA_COLUMN" -> tableTarget(type).copy(
            columnName = "Balance",
            formula = "{Income} - {Expense}",
        )

        "UPDATE_RELATION_COLUMN" -> tableTarget(type).copy(
            columnName = "Category relation",
            relationTargetTableId = "table-categories",
            relationTargetTableTitle = "Categories",
        )

        "UPDATE_ROLLUP_COLUMN" -> tableTarget(type).copy(
            columnName = "Category total",
            rollupRelationColumnId = "column-relation",
            rollupRelationColumnName = "Category relation",
            rollupTargetColumnId = "column-amount",
            rollupTargetColumnName = "Amount",
            rollupAggregation = "Sum",
        )

        "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" ->
            tableTarget(type).copy(columnName = "Category", targetIndex = 1)
        "DUPLICATE_TABLE_COLUMN" ->
            tableTarget(type).copy(columnName = "Category", newColumnName = "Category copy")

        "ADD_TABLE_ROW" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            cellValues = mapOf("Name" to "Lunch", "Amount" to "12"),
        )

        "DELETE_TABLE_ROW" -> tableTarget(type).copy(rowTitle = "Lunch")
        "UPDATE_TABLE_ROW" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            cellValues = mapOf("Amount" to "15"),
        )

        "RENAME_TABLE_ROW" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            newRowTitle = "Dinner",
        )

        "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" ->
            tableTarget(type).copy(rowTitle = "Lunch", targetIndex = 1)
        "DUPLICATE_TABLE_ROW" ->
            tableTarget(type).copy(rowTitle = "Lunch", newRowTitle = "Lunch copy")
        "DELETE_TABLE_ROWS" -> tableTarget(type).copy(
            columnName = "Month",
            filterQuery = "2026-04",
        )
        "UPDATE_TABLE_ROWS" -> tableTarget(type).copy(
            rowIds = listOf("row-lunch"),
            cellValues = mapOf("Status" to "Done"),
        )

        "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK" ->
            tableTarget(type).copy(
                rowTitle = "Lunch",
                content = "Receipt note",
                blockType = "Text",
            )

        "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK" ->
            tableTarget(type).copy(
                rowTitle = "Lunch",
                rowBlockId = "row-block",
                content = "Updated receipt note",
            )

        "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            rowBlockId = "row-todo",
            isChecked = type == "CHECK_ROW_PAGE_BLOCK",
        )

        "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            rowBlockId = "row-block",
        )

        "UPDATE_TABLE_CELL" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            columnName = "Amount",
            value = "18",
        )

        "CLEAR_TABLE_CELL" -> tableTarget(type).copy(
            rowTitle = "Lunch",
            columnName = "Amount",
        )

        "CLEAR_TABLE_CELLS" -> tableTarget(type).copy(
            columnName = "Month",
            filterQuery = "2026-07",
        )

        "CHANGE_TABLE_VIEW", "SET_TABLE_VIEW" -> tableTarget(type).copy(tableView = "Calendar")
        "SET_TABLE_VIEW_CONFIG", "CONFIGURE_TABLE_VIEW", "UPDATE_TABLE_VIEW_CONFIG" ->
            tableTarget(type).copy(
                tableView = "Calendar",
                calendarDateColumnName = "Date",
            )

        "SORT_TABLE", "SET_TABLE_SORT" -> tableTarget(type).copy(
            columnName = "Amount",
            sortDirection = "Descending",
        )

        "CLEAR_TABLE_SORT" -> tableTarget(type)
        "FILTER_TABLE", "SET_TABLE_FILTER" -> tableTarget(type).copy(
            columnName = "Category",
            filterQuery = "Food",
        )

        "CLEAR_TABLE_FILTER" -> tableTarget(type)
        "GROUP_TABLE", "SET_TABLE_GROUP" -> tableTarget(type).copy(
            columnName = "Category",
            groupByColumnName = "Category",
        )

        "CLEAR_TABLE_GROUP" -> tableTarget(type)
        "CREATE_TASK" -> tableTarget(type).copy(title = "Submit report")
        "CREATE_REMINDER" -> tableTarget(type).copy(
            title = "Pay electricity bill",
            delayMinutes = 30,
        )
        "CANCEL_REMINDER", "COMPLETE_REMINDER" -> tableTarget(type).copy(
            rowTitle = "Pay electricity bill",
            columnName = "Date",
        )
        "RESCHEDULE_REMINDER" -> tableTarget(type).copy(
            rowTitle = "Pay electricity bill",
            columnName = "Date",
            delayMinutes = 60,
        )

        else -> error("Missing regression fixture for shared action: $type")
    }

    private fun pageTarget(type: String): AiActionWire =
        AiActionWire(type = type, targetTitle = "Budget")

    private fun tableTarget(type: String): AiActionWire =
        AiActionWire(
            type = type,
            targetTitle = "Budget",
            tableTitle = "Transactions",
        )
}
