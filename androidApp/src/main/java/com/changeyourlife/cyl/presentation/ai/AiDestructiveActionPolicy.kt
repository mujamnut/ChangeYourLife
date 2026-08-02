package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction

internal const val DestructiveConfirmationRequiredCode =
    "DESTRUCTIVE_CONFIRMATION_REQUIRED"
internal const val ConfirmDestructiveActionsPrompt = "Confirm destructive changes"
internal const val CancelDestructiveActionsPrompt = "Cancel destructive changes"

internal object AiDestructiveActionPolicy {
    fun confirmationIndexes(actions: List<ChatAction>): Set<Int> {
        val explicitlyHighRisk = actions.mapIndexedNotNull { index, action ->
            index.takeIf { action.requiresExplicitConfirmation() }
        }
        val removalIndexes = actions.mapIndexedNotNull { index, action ->
            index.takeIf { action.removesUserData() }
        }
        return buildSet {
            addAll(explicitlyHighRisk)
            if (removalIndexes.size >= BulkRemovalConfirmationThreshold) {
                addAll(removalIndexes)
            }
        }
    }

    fun confirmationSummary(actions: List<ChatAction>, useMalay: Boolean): String {
        val labels = actions
            .map(ChatAction::destructiveLabel)
            .distinct()
            .take(3)
        val detail = labels.joinToString(", ")
        return if (useMalay) {
            buildString {
                append("Perubahan ini boleh memadam data")
                if (detail.isNotBlank()) append(": $detail")
                append(". Sahkan untuk teruskan atau batalkan.")
            }
        } else {
            buildString {
                append("This change can delete data")
                if (detail.isNotBlank()) append(": $detail")
                append(". Confirm to continue or cancel.")
            }
        }
    }
}

private fun ChatAction.requiresExplicitConfirmation(): Boolean {
    return when (type.normalizedDestructiveKey()) {
        "DELETE_PAGE_PERMANENTLY",
        "DELETE_ALL_BLOCKS",
        "DELETE_TABLE_ROWS",
        "CLEAR_TABLE_CELLS",
        "DELETE_TABLE_COLUMN",
        -> true

        "DELETE_BLOCK" -> blockType.normalizedDestructiveKey() in DatabaseBlockTypeKeys
        else -> false
    }
}

private fun ChatAction.removesUserData(): Boolean {
    return when (type.normalizedDestructiveKey()) {
        "DELETE_PAGE_PERMANENTLY",
        "DELETE_ALL_BLOCKS",
        "DELETE_BLOCK",
        "DELETE_PROPERTY",
        "DELETE_TABLE_COLUMN",
        "DELETE_TABLE_COLUMN_OPTION",
        "DELETE_TABLE_ROW",
        "DELETE_TABLE_ROWS",
        "DELETE_ROW_PAGE_BLOCK",
        "DELETE_TABLE_ROW_BLOCK",
        "CLEAR_TABLE_CELL",
        "CLEAR_TABLE_CELLS",
        "CLEAR_RELATION_CELL",
        "REMOVE_MEDIA_CELL",
        "CLEAR_MEDIA_CELL",
        -> true

        else -> false
    }
}

private fun ChatAction.destructiveLabel(): String {
    val target = listOf(
        targetTitle,
        tableTitle,
        columnName,
        rowTitle,
        blockText,
        title,
    ).firstOrNull(String::isNotBlank).orEmpty()
    val action = when (type.normalizedDestructiveKey()) {
        "DELETE_PAGE_PERMANENTLY" -> "page"
        "DELETE_ALL_BLOCKS" -> "all page blocks"
        "DELETE_TABLE_COLUMN" -> "column"
        "DELETE_TABLE_ROW", "DELETE_TABLE_ROWS" -> "database rows"
        "CLEAR_TABLE_CELL", "CLEAR_TABLE_CELLS" -> "cell values"
        "DELETE_BLOCK" -> if (blockType.normalizedDestructiveKey() in DatabaseBlockTypeKeys) {
            "database"
        } else {
            "block"
        }
        else -> "content"
    }
    return listOf(action, target.take(48))
        .filter(String::isNotBlank)
        .joinToString(" ")
}

private val DatabaseBlockTypeKeys = setOf(
    "DATABASE",
    "DATABASE_TABLE",
    "TABLE",
)

private fun String.normalizedDestructiveKey(): String = trim().uppercase()

private const val BulkRemovalConfirmationThreshold = 3
