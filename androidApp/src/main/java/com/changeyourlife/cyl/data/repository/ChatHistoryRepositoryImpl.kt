package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.domain.model.ChatMessage
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
        return session
    }

    override suspend fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        pageLinks: List<ChatPageLink>,
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
            ),
        )
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            pageLinks = pageLinks,
            createdAt = now,
        )
        chatMessageDao.upsertMessage(message.toEntity(json))
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
    }

    override suspend fun deleteEmptySessions(scopeId: String) {
        chatMessageDao.softDeleteEmptySessions(
            scopeId = scopeId,
            deletedAt = System.currentTimeMillis(),
        )
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
        createdAt = createdAt,
    )
}

@Serializable
private data class ChatPageLinkDto(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
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

private fun String.toSessionTitle(): String {
    return trim()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(48)
        ?.ifBlank { null }
        ?: "New chat"
}
