package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ChatMessageRecord
import com.changeyourlife.cyl.backend.domain.ChatSessionRecord
import com.changeyourlife.cyl.backend.domain.ChatSyncRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryChatSyncRepository : ChatSyncRepository {
    private val sessionsByKey = ConcurrentHashMap<String, ChatSessionRecord>()
    private val messagesByKey = ConcurrentHashMap<String, ChatMessageRecord>()

    override suspend fun listSessions(
        userId: String,
        scopeId: String,
        updatedAfter: Long,
    ): List<ChatSessionRecord> {
        return sessionsByKey.values
            .asSequence()
            .filter { session -> session.userId == userId }
            .filter { session -> session.scopeId == scopeId }
            .filter { session -> session.updatedAt > updatedAfter }
            .sortedBy { session -> session.updatedAt }
            .toList()
    }

    override suspend fun upsertSession(userId: String, session: ChatSessionRecord): ChatSessionRecord? {
        if (session.userId != userId) return null
        sessionsByKey[session.key] = session
        return session
    }

    override suspend fun listMessages(
        userId: String,
        sessionId: String,
        updatedAfter: Long,
    ): List<ChatMessageRecord> {
        val session = sessionsByKey[sessionKey(userId, sessionId)] ?: return emptyList()
        return messagesByKey.values
            .asSequence()
            .filter { message -> message.userId == userId }
            .filter { message -> message.sessionId == session.id }
            .filter { message -> message.updatedAt > updatedAfter }
            .sortedBy { message -> message.createdAt }
            .toList()
    }

    override suspend fun upsertMessage(userId: String, message: ChatMessageRecord): ChatMessageRecord? {
        if (message.userId != userId) return null
        val session = sessionsByKey[sessionKey(userId, message.sessionId)] ?: return null
        if (session.scopeId != message.scopeId) return null
        messagesByKey[message.key] = message
        return message
    }
}

private val ChatSessionRecord.key: String
    get() = sessionKey(userId, id)

private val ChatMessageRecord.key: String
    get() = "$userId:$id"

private fun sessionKey(userId: String, sessionId: String): String = "$userId:$sessionId"
