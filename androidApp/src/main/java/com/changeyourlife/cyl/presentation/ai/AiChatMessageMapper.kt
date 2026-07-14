package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.AiActionLog
import com.changeyourlife.cyl.domain.model.ChatMessage

object AiChatMessageMapper {
    fun toAiChatMessages(
        messages: List<ChatMessage>,
        actionLogs: List<AiActionLog> = emptyList(),
    ): List<AiChatMessage> {
        val logsByAuditId = actionLogs.associateBy { log -> log.auditId }
        return messages.map { message ->
            AiChatMessage(
                id = message.id,
                role = message.role,
                content = message.content,
                attachments = message.attachments.map { attachment ->
                    AiChatAttachment(
                        id = attachment.id,
                        name = attachment.name,
                        mimeType = attachment.mimeType,
                        kind = attachment.kind,
                        sizeBytes = attachment.sizeBytes,
                        previewDataUrl = attachment.previewDataUrl,
                    )
                },
                pageLinks = message.pageLinks.map { link ->
                    AiChatPageLink(
                        pageId = link.pageId,
                        title = link.title,
                        targetType = link.targetType,
                        targetId = link.targetId,
                    )
                },
                actionMetadata = message.actionMetadata?.let { metadata ->
                    val actionLog = logsByAuditId[metadata.auditId]
                    AiChatActionMetadata(
                        auditId = metadata.auditId,
                        requestMessageId = metadata.requestMessageId,
                        undoState = actionLog?.undoState.orEmpty(),
                        executedAt = metadata.executedAt,
                        provider = metadata.provider,
                        model = metadata.model,
                        mode = metadata.mode,
                        schemaName = metadata.schemaName,
                        schemaVersion = metadata.schemaVersion,
                        proposedActions = metadata.proposedActions.map { action ->
                            AiChatActionMetadataItem(
                                type = action.type,
                                target = action.target,
                                actionIndex = action.actionIndex,
                            )
                        },
                        executedActions = metadata.executedActions.map { action ->
                            AiChatActionMetadataItem(
                                type = action.type,
                                target = action.target,
                                actionIndex = action.actionIndex,
                            )
                        },
                        executionMessages = metadata.executionMessages,
                        validationIssues = metadata.validationIssues.map { issue ->
                            AiChatActionValidationIssue(
                                actionIndex = issue.actionIndex,
                                field = issue.field,
                                code = issue.code,
                                message = issue.message,
                            )
                        },
                    )
                },
            )
        }
    }
}
