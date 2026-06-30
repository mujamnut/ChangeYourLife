package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue

class AiActionSchemaValidator {
    data class Result(
        val actions: List<AiService.AiActionItem>,
        val issues: List<AiActionValidationIssue>,
    )

    fun validate(actions: List<AiService.AiActionItem>): Result {
        val validActions = mutableListOf<AiService.AiActionItem>()
        val issues = mutableListOf<AiActionValidationIssue>()
        actions.forEachIndexed { index, action ->
            val normalizedType = action.type.normalizedActionType()
            when {
                normalizedType.isBlank() -> {
                    issues += AiActionValidationIssue(
                        actionIndex = index,
                        field = "type",
                        code = "missing_action_type",
                        message = "Action type is required.",
                    )
                }
                normalizedType !in supportedActionTypes -> {
                    issues += AiActionValidationIssue(
                        actionIndex = index,
                        field = "type",
                        code = "unsupported_action_type",
                        message = "Unsupported CYL action type: $normalizedType.",
                    )
                }
                else -> {
                    val normalizedAction = action.copy(type = normalizedType)
                    val requirementIssues = normalizedAction.requiredFieldIssues(actionIndex = index)
                    if (requirementIssues.isEmpty()) {
                        validActions += normalizedAction
                    } else {
                        issues += requirementIssues
                    }
                }
            }
        }
        return Result(actions = validActions, issues = issues)
    }

    private fun AiService.AiActionItem.requiredFieldIssues(actionIndex: Int): List<AiActionValidationIssue> {
        fun issue(field: String, message: String): AiActionValidationIssue =
            AiActionValidationIssue(
                actionIndex = actionIndex,
                field = field,
                code = "missing_required_field",
                message = message,
            )

        fun hasAny(vararg values: String?): Boolean = values.any { value -> !value.isNullOrBlank() }

        return when (type) {
            "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
                val normalizedBlockType = blockType.normalizedActionType()
                when {
                    normalizedBlockType == "DIVIDER" -> emptyList()
                    normalizedBlockType in setOf("MEDIAFILE", "MEDIA_FILE") && hasAny(mediaUri) -> emptyList()
                    normalizedBlockType in setOf("MEDIAFILE", "MEDIA_FILE") -> {
                        listOf(issue("mediaUri", "Media/file block needs mediaUri."))
                    }
                    hasAny(content, title) -> emptyList()
                    else -> listOf(issue("content", "Block content is required unless the block is a divider."))
                }
            }
            "DELETE_BLOCK" -> {
                if (hasAny(blockId, blockText, content, title)) emptyList()
                else listOf(issue("blockId", "Delete block action needs blockId, blockText, content, or title."))
            }
            "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> {
                buildList {
                    if (!hasAny(blockId, blockText, title)) {
                        add(issue("blockId", "Update block action needs blockId, blockText, or title."))
                    }
                    if (type in setOf("UPDATE_BLOCK", "EDIT_BLOCK") && !hasAny(content, value)) {
                        add(issue("content", "Update block action needs replacement content."))
                    }
                }
            }
            "ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY" -> {
                if (hasAny(propertyName, title)) emptyList()
                else listOf(issue("propertyName", "Property action needs propertyName or title."))
            }
            "CREATE_DATABASE", "CREATE_TABLE" -> {
                if (hasAny(tableTitle, title, content)) emptyList()
                else listOf(issue("tableTitle", "Create table action needs tableTitle, title, or content."))
            }
            "CREATE_PAGE", "CREATE_SUBPAGE" -> {
                if (hasAny(title, content, tableTitle, moduleType)) emptyList()
                else listOf(issue("title", "Create page action needs title, content, tableTitle, or moduleType."))
            }
            "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> {
                if (hasAny(title, value, content, newColumnName)) emptyList()
                else listOf(issue("title", "Rename table action needs a new title."))
            }
            "ADD_TABLE_COLUMN" -> {
                if (hasAny(columnName, propertyName, title)) emptyList()
                else listOf(issue("columnName", "Add table column action needs columnName, propertyName, or title."))
            }
            "DELETE_TABLE_COLUMN", "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE",
            "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG", "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN",
            "UPDATE_ROLLUP_COLUMN" -> {
                buildList {
                    if (!hasAny(columnId, columnName, propertyName, title)) {
                        add(issue("columnName", "Column action needs columnId, columnName, propertyName, or title."))
                    }
                    if (type == "UPDATE_FORMULA_COLUMN" && !hasAny(formula, value, content)) {
                        add(issue("formula", "Formula column action needs formula, value, or content."))
                    }
                }
            }
            "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> {
                buildList {
                    if (!hasAny(columnId, columnName, propertyName, title)) {
                        add(issue("columnName", "Rename column action needs columnId, columnName, propertyName, or title."))
                    }
                    if (!hasAny(newColumnName, value, content)) {
                        add(issue("newColumnName", "Rename column action needs newColumnName, value, or content."))
                    }
                }
            }
            "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> {
                buildList {
                    if (!hasAny(columnId, columnName, propertyName, title)) {
                        add(issue("columnName", "Move column action needs columnId, columnName, propertyName, or title."))
                    }
                    if (targetIndex == null) add(issue("targetIndex", "Move column action needs targetIndex."))
                }
            }
            "ADD_TABLE_ROW" -> {
                if (hasAny(rowTitle, title, content) || cellValues.isNotEmpty() || tableRows.isNotEmpty()) emptyList()
                else listOf(issue("rowTitle", "Add row action needs rowTitle, title, content, cellValues, or tableRows."))
            }
            "DELETE_TABLE_ROW" -> {
                if (hasAny(rowId, rowTitle, title)) emptyList()
                else listOf(issue("rowTitle", "Delete row action needs rowId, rowTitle, or title."))
            }
            "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) {
                        add(issue("rowTitle", "Update row action needs rowId, rowTitle, or title."))
                    }
                    if (!hasAny(newRowTitle, value, content) && cellValues.isEmpty()) {
                        add(issue("value", "Update row action needs newRowTitle, value, content, or cellValues."))
                    }
                }
            }
            "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) {
                        add(issue("rowTitle", "Move row action needs rowId, rowTitle, or title."))
                    }
                    if (targetIndex == null) add(issue("targetIndex", "Move row action needs targetIndex."))
                }
            }
            "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK",
            "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
            "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK", "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
                if (hasAny(rowId, rowTitle, targetTitle, title)) emptyList()
                else listOf(issue("rowTitle", "Row page block action needs rowId, rowTitle, targetTitle, or title."))
            }
            "UPDATE_TABLE_CELL" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) add(issue("rowTitle", "Cell update needs rowId, rowTitle, or title."))
                    if (!hasAny(columnId, columnName, propertyName)) {
                        add(issue("columnName", "Cell update needs columnId, columnName, or propertyName."))
                    }
                    if (!hasAny(value, content)) add(issue("value", "Cell update needs value or content."))
                }
            }
            "SORT_TABLE", "SET_TABLE_SORT", "FILTER_TABLE", "SET_TABLE_FILTER", "GROUP_TABLE", "SET_TABLE_GROUP" -> {
                if (hasAny(columnId, columnName, propertyName, title, groupByColumnId, groupByColumnName)) emptyList()
                else listOf(issue("columnName", "Table rule action needs a target column."))
            }
            else -> emptyList()
        }
    }

    private fun String.normalizedActionType(): String =
        trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

    private companion object {
        val supportedActionTypes = setOf(
            "RENAME_CURRENT_PAGE",
            "RENAME_PAGE",
            "UPDATE_PAGE",
            "APPEND_BLOCK",
            "APPEND_PAGE_BLOCK",
            "ADD_BLOCK",
            "ADD_PROPERTY",
            "UPDATE_PROPERTY",
            "DELETE_PROPERTY",
            "DELETE_ALL_BLOCKS",
            "DELETE_BLOCK",
            "UPDATE_BLOCK",
            "EDIT_BLOCK",
            "UPDATE_TODO",
            "CHECK_BLOCK",
            "UNCHECK_BLOCK",
            "CREATE_DATABASE",
            "CREATE_TABLE",
            "RENAME_TABLE",
            "RENAME_DATABASE",
            "UPDATE_TABLE_TITLE",
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
            "ADD_TABLE_ROW",
            "DELETE_TABLE_ROW",
            "UPDATE_TABLE_ROW",
            "RENAME_TABLE_ROW",
            "REORDER_TABLE_ROW",
            "MOVE_TABLE_ROW",
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
            "UPDATE_TABLE_CELL",
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
            "CREATE_SUBPAGE",
            "CREATE_PAGE",
            "CREATE_TASK",
            "CREATE_REMINDER",
        )
    }
}
