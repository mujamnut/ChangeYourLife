package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatHistoryRepository {
    fun observeSessions(scopeId: String): Flow<List<ChatSession>>

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>

    fun observeMessagesForScope(scopeId: String): Flow<List<ChatMessage>>

    suspend fun getOrCreateLatestSession(scopeId: String): ChatSession

    suspend fun createSession(
        scopeId: String,
        title: String = "New chat",
    ): ChatSession

    suspend fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        pageLinks: List<ChatPageLink> = emptyList(),
    ): ChatMessage

    suspend fun clearMessages(sessionId: String)
}
