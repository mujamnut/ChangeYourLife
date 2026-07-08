package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction

internal enum class AiActionExecutionDomain(val id: String, val label: String) {
    Page("page", "Page"),
    Block("block", "Block"),
    Property("property", "Property"),
    Database("database", "Database"),
    Column("column", "Column"),
    Row("row", "Row"),
    RowContent("row_content", "Row content"),
    Cell("cell", "Cell"),
    Task("task", "Task"),
    Reminder("reminder", "Reminder"),
    Unknown("unknown", "Unknown"),
}

internal data class AiActionExecutionTrace(
    val actionIndex: Int,
    val actionType: String,
    val domain: AiActionExecutionDomain,
) {
    val messageLabel: String
        get() = "${domain.label}/$actionType #${actionIndex + 1}"
}

internal object AiActionExecutionRegistry {
    private val pageActions = setOf(
        "RENAME_CURRENT_PAGE",
        "RENAME_PAGE",
        "UPDATE_PAGE",
        "CREATE_SUBPAGE",
        "CREATE_PAGE",
    )

    private val blockActions = setOf(
        "APPEND_BLOCK",
        "APPEND_PAGE_BLOCK",
        "ADD_BLOCK",
        "DELETE_ALL_BLOCKS",
        "DELETE_BLOCK",
        "FORMAT_BLOCK_TEXT",
        "UPDATE_BLOCK",
        "EDIT_BLOCK",
        "UPDATE_TODO",
        "CHECK_BLOCK",
        "UNCHECK_BLOCK",
    )

    private val propertyActions = setOf(
        "ADD_PROPERTY",
        "UPDATE_PROPERTY",
        "DELETE_PROPERTY",
    )

    private val databaseActions = setOf(
        "CREATE_DATABASE",
        "CREATE_TABLE",
        "RENAME_TABLE",
        "RENAME_DATABASE",
        "UPDATE_TABLE_TITLE",
        "CHANGE_TABLE_VIEW",
        "SET_TABLE_VIEW",
        "SET_TABLE_VIEW_CONFIG",
        "CONFIGURE_TABLE_VIEW",
        "UPDATE_TABLE_VIEW_CONFIG",
        "SORT_TABLE",
        "SET_TABLE_SORT",
        "CLEAR_TABLE_SORT",
        "FILTER_TABLE",
        "SET_TABLE_FILTER",
        "CLEAR_TABLE_FILTER",
        "GROUP_TABLE",
        "SET_TABLE_GROUP",
        "CLEAR_TABLE_GROUP",
    )

    private val columnActions = setOf(
        "ADD_TABLE_COLUMN",
        "DELETE_TABLE_COLUMN",
        "RENAME_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN_TYPE",
        "CHANGE_TABLE_COLUMN_TYPE",
        "SET_TABLE_COLUMN_TYPE",
        "UPDATE_TABLE_COLUMN_CONFIG",
        "SET_TABLE_COLUMN_CONFIG",
        "UPDATE_FORMULA_COLUMN",
        "UPDATE_RELATION_COLUMN",
        "UPDATE_ROLLUP_COLUMN",
        "REORDER_TABLE_COLUMN",
        "MOVE_TABLE_COLUMN",
    )

    private val rowActions = setOf(
        "ADD_TABLE_ROW",
        "DELETE_TABLE_ROW",
        "UPDATE_TABLE_ROW",
        "RENAME_TABLE_ROW",
        "REORDER_TABLE_ROW",
        "MOVE_TABLE_ROW",
    )

    private val rowContentActions = setOf(
        "ADD_ROW_PAGE_BLOCK",
        "APPEND_ROW_PAGE_BLOCK",
        "ADD_TABLE_ROW_BLOCK",
        "UPDATE_ROW_PAGE_BLOCK",
        "EDIT_ROW_PAGE_BLOCK",
        "UPDATE_TABLE_ROW_BLOCK",
        "CHECK_ROW_PAGE_BLOCK",
        "UNCHECK_ROW_PAGE_BLOCK",
        "DELETE_ROW_PAGE_BLOCK",
        "DELETE_TABLE_ROW_BLOCK",
    )

    private val cellActions = setOf("UPDATE_TABLE_CELL")
    private val taskActions = setOf("CREATE_TASK")
    private val reminderActions = setOf("CREATE_REMINDER")

    val supportedActions: Set<String> =
        pageActions +
            blockActions +
            propertyActions +
            databaseActions +
            columnActions +
            rowActions +
            rowContentActions +
            cellActions +
            taskActions +
            reminderActions

    fun supports(action: ChatAction): Boolean {
        return action.normalizedExecutionType() in supportedActions
    }

    fun trace(actionIndex: Int, action: ChatAction): AiActionExecutionTrace {
        val actionType = action.normalizedExecutionType()
        return AiActionExecutionTrace(
            actionIndex = actionIndex,
            actionType = actionType,
            domain = domainFor(actionType),
        )
    }

    fun domainFor(actionType: String): AiActionExecutionDomain {
        return when (actionType) {
            in pageActions -> AiActionExecutionDomain.Page
            in blockActions -> AiActionExecutionDomain.Block
            in propertyActions -> AiActionExecutionDomain.Property
            in databaseActions -> AiActionExecutionDomain.Database
            in columnActions -> AiActionExecutionDomain.Column
            in rowActions -> AiActionExecutionDomain.Row
            in rowContentActions -> AiActionExecutionDomain.RowContent
            in cellActions -> AiActionExecutionDomain.Cell
            in taskActions -> AiActionExecutionDomain.Task
            in reminderActions -> AiActionExecutionDomain.Reminder
            else -> AiActionExecutionDomain.Unknown
        }
    }
}

internal fun ChatAction.normalizedExecutionType(): String = type.trim().uppercase()
