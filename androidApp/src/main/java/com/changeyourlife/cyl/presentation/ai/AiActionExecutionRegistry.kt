package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.aicontract.AiActionDomain
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
    val supportedActions: Set<String> = AiActionContractSchema.supportedTypes

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
        return AiActionContractSchema.domainFor(actionType).toExecutionDomain()
    }
}

internal fun AiActionDomain?.toExecutionDomain(): AiActionExecutionDomain = when (this) {
    AiActionDomain.Page -> AiActionExecutionDomain.Page
    AiActionDomain.Block -> AiActionExecutionDomain.Block
    AiActionDomain.Property -> AiActionExecutionDomain.Property
    AiActionDomain.Database -> AiActionExecutionDomain.Database
    AiActionDomain.Column -> AiActionExecutionDomain.Column
    AiActionDomain.Row -> AiActionExecutionDomain.Row
    AiActionDomain.RowContent -> AiActionExecutionDomain.RowContent
    AiActionDomain.Cell -> AiActionExecutionDomain.Cell
    AiActionDomain.Task -> AiActionExecutionDomain.Task
    AiActionDomain.Reminder -> AiActionExecutionDomain.Reminder
    null -> AiActionExecutionDomain.Unknown
}

internal fun ChatAction.normalizedExecutionType(): String = AiActionContractSchema.normalizeType(type)
