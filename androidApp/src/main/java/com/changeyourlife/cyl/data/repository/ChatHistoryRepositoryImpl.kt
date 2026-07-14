package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.sync.ChatSyncScheduler
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatMessageAttachment
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ChatHistoryRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val chatSyncScheduler: ChatSyncScheduler,
) : ChatHistoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeSessions(scopeId: String): Flow<List<ChatSession>> {
        return chatMessageDao.observeSessions(scopeId)
            .map { sessions -> sessions.map { it.toDomain() } }
    }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.observeMessages(sessionId)
            .map { messages -> messages.map { it.toDomain(json) } }
    }

    override fun observeMessagesForScope(scopeId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.observeMessagesForScope(scopeId)
            .map { messages -> messages.map { it.toDomain(json) } }
    }

    override suspend fun getOrCreateLatestSession(scopeId: String): ChatSession {
        return chatMessageDao.getLatestSession(scopeId)?.toDomain()
            ?: createSession(scopeId)
    }

    override suspend fun createSession(
        scopeId: String,
        title: String,
    ): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            scopeId = scopeId,
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        chatMessageDao.upsertSession(session.toEntity())
        chatSyncScheduler.pushSession(session.id)
        return session
    }

    override suspend fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        pageLinks: List<ChatPageLink>,
        attachments: List<ChatMessageAttachment>,
        actionMetadata: ChatActionMetadata?,
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val messageCount = chatMessageDao.getMessageCount(sessionId)
        val existingSession = chatMessageDao.getSession(sessionId)
        val session = existingSession ?: ChatSessionEntity(
            id = sessionId,
            scopeId = sessionId,
            title = "New chat",
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        val updatedTitle = if (role == "user" && messageCount == 0) {
            content.toSessionTitle()
        } else {
            session.title
        }
        chatMessageDao.upsertSession(
            session.copy(
                title = updatedTitle,
                updatedAt = now,
                syncStatus = SyncStatus.PendingPush,
            ),
        )
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            pageLinks = pageLinks,
            attachments = attachments,
            actionMetadata = actionMetadata,
            createdAt = now,
        )
        chatMessageDao.upsertMessage(message.toEntity(json))
        chatSyncScheduler.pushSession(session.id)
        chatSyncScheduler.pushMessage(message.id)
        return message
    }

    override suspend fun clearMessages(sessionId: String) {
        chatMessageDao.clearMessages(sessionId)
    }

    override suspend fun deleteSession(sessionId: String) {
        chatMessageDao.softDeleteSession(
            sessionId = sessionId,
            deletedAt = System.currentTimeMillis(),
        )
        chatSyncScheduler.pushSession(sessionId)
    }

    override suspend fun deleteEmptySessions(scopeId: String) {
        chatMessageDao.softDeleteEmptySessions(
            scopeId = scopeId,
            deletedAt = System.currentTimeMillis(),
        )
        chatSyncScheduler.pushPending()
    }
}

private fun ChatSessionEntity.toDomain(): ChatSession {
    return ChatSession(
        id = id,
        scopeId = scopeId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun ChatSession.toEntity(): ChatSessionEntity {
    return ChatSessionEntity(
        id = id,
        scopeId = scopeId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = null,
    )
}

private fun ChatMessageEntity.toDomain(json: Json): ChatMessage {
    return ChatMessage(
        id = id,
        sessionId = scopeId,
        role = role,
        content = content,
        pageLinks = runCatching {
            json.decodeFromString<List<ChatPageLinkDto>>(pageLinksJson).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        attachments = runCatching {
            json.decodeFromString<List<ChatMessageAttachmentDto>>(attachmentsJson).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        actionMetadata = actionMetadataJson
            .takeIf { it.isNotBlank() }
            ?.let { metadataJson ->
                runCatching { json.decodeFromString<ChatActionMetadataDto>(metadataJson).toDomain() }
                    .getOrNull()
            },
        createdAt = createdAt,
    )
}

private fun ChatMessage.toEntity(json: Json): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        scopeId = sessionId,
        role = role,
        content = content,
        pageLinksJson = json.encodeToString(pageLinks.map { it.toDto() }),
        attachmentsJson = json.encodeToString(attachments.map { it.toDto() }),
        actionMetadataJson = actionMetadata?.let { metadata ->
            json.encodeToString(metadata.toDto())
        }.orEmpty(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

@Serializable
private data class ChatPageLinkDto(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
)

@Serializable
private data class ChatMessageAttachmentDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val kind: String,
    val sizeBytes: Long,
    val previewDataUrl: String = "",
)

@Serializable
private data class ChatActionMetadataDto(
    val auditId: String = "",
    val requestMessageId: String = "",
    val executedAt: Long = 0L,
    val provider: String = "",
    val model: String = "",
    val mode: String = "",
    val schemaName: String = "",
    val schemaVersion: Int = 1,
    val proposedActions: List<ChatActionMetadataItemDto> = emptyList(),
    val executedActions: List<ChatActionMetadataItemDto> = emptyList(),
    val executionMessages: List<String> = emptyList(),
    val validationIssues: List<ChatActionValidationMetadataDto> = emptyList(),
)

@Serializable
private data class ChatActionMetadataItemDto(
    val type: String = "",
    val target: String = "",
    val actionIndex: Int? = null,
)

@Serializable
private data class ChatActionValidationMetadataDto(
    val actionIndex: Int? = null,
    val actionType: String = "",
    val actionDomain: String = "",
    val field: String = "",
    val code: String = "",
    val message: String = "",
)

private fun ChatPageLinkDto.toDomain(): ChatPageLink {
    return ChatPageLink(
        pageId = pageId,
        title = title,
        targetType = targetType,
        targetId = targetId,
    )
}

private fun ChatPageLink.toDto(): ChatPageLinkDto {
    return ChatPageLinkDto(
        pageId = pageId,
        title = title,
        targetType = targetType,
        targetId = targetId,
    )
}

private fun ChatMessageAttachmentDto.toDomain(): ChatMessageAttachment {
    return ChatMessageAttachment(
        id = id,
        name = name,
        mimeType = mimeType,
        kind = kind,
        sizeBytes = sizeBytes,
        previewDataUrl = previewDataUrl,
    )
}

private fun ChatMessageAttachment.toDto(): ChatMessageAttachmentDto {
    return ChatMessageAttachmentDto(
        id = id,
        name = name,
        mimeType = mimeType,
        kind = kind,
        sizeBytes = sizeBytes,
        previewDataUrl = previewDataUrl,
    )
}

private fun ChatActionMetadataDto.toDomain(): ChatActionMetadata {
    return ChatActionMetadata(
        auditId = auditId,
        requestMessageId = requestMessageId,
        executedAt = executedAt,
        provider = provider,
        model = model,
        mode = mode,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActions = proposedActions.map { it.toDomain() },
        executedActions = executedActions.map { it.toDomain() },
        executionMessages = executionMessages,
        validationIssues = validationIssues.map { it.toDomain() },
    )
}

private fun ChatActionMetadata.toDto(): ChatActionMetadataDto {
    return ChatActionMetadataDto(
        auditId = auditId,
        requestMessageId = requestMessageId,
        executedAt = executedAt,
        provider = provider,
        model = model,
        mode = mode,
        schemaName = schemaName,
        schemaVersion = schemaVersion,
        proposedActions = proposedActions.map { it.toDto() },
        executedActions = executedActions.map { it.toDto() },
        executionMessages = executionMessages,
        validationIssues = validationIssues.map { it.toDto() },
    )
}

private fun ChatActionMetadataItemDto.toDomain(): ChatActionMetadataItem {
    return ChatActionMetadataItem(
        type = type,
        target = target,
        actionIndex = actionIndex,
    )
}

private fun ChatActionMetadataItem.toDto(): ChatActionMetadataItemDto {
    return ChatActionMetadataItemDto(
        type = type,
        target = target,
        actionIndex = actionIndex,
    )
}

private fun ChatActionValidationMetadataDto.toDomain(): ChatActionValidationMetadata {
    return ChatActionValidationMetadata(
        actionIndex = actionIndex,
        actionType = actionType,
        actionDomain = actionDomain,
        field = field,
        code = code,
        message = message,
    )
}

private fun ChatActionValidationMetadata.toDto(): ChatActionValidationMetadataDto {
    return ChatActionValidationMetadataDto(
        actionIndex = actionIndex,
        actionType = actionType,
        actionDomain = actionDomain,
        field = field,
        code = code,
        message = message,
    )
}

private fun String.toSessionTitle(): String {
    return trim()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(48)
        ?.ifBlank { null }
        ?: "New chat"
}
