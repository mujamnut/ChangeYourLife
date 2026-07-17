package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.search.ChatSearchIndexUpdater
import com.changeyourlife.cyl.data.sync.ChatSyncScheduler
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.ChatPageLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHistoryRepositoryImplTest {
    @Test
    fun actionMetadataSurvivesRepositoryRecreation() = runBlocking {
        val dao = FakeChatMessageDao()
        val firstRepository = ChatHistoryRepositoryImpl(dao, FakeChatSyncScheduler(), NoOpChatSearchIndexUpdater())
        val session = firstRepository.createSession(scopeId = "workspace-chat")
        val metadata = ChatActionMetadata(
            auditId = "audit-1",
            requestMessageId = "user-message-1",
            executedAt = 2000L,
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            mode = "Edit",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 2,
            proposedActions = listOf(
                ChatActionMetadataItem(type = "ADD_TABLE_ROW", target = "Budget / Makan", actionIndex = 0),
            ),
            executedActions = listOf(
                ChatActionMetadataItem(type = "ADD_TABLE_ROW", target = "Budget / Makan", actionIndex = 0),
            ),
            executionMessages = listOf("Done: Added row to Budget"),
            validationIssues = listOf(
                ChatActionValidationMetadata(
                    actionIndex = 0,
                    field = "rowTitle",
                    code = "checked",
                    message = "validated",
                ),
            ),
        )

        firstRepository.appendMessage(
            sessionId = session.id,
            role = "assistant",
            content = "Siap.",
            pageLinks = listOf(
                ChatPageLink(
                    pageId = "page-1",
                    title = "Budget",
                    targetType = "row",
                    targetId = "row-1",
                ),
            ),
            actionMetadata = metadata,
        )

        val recreatedRepository = ChatHistoryRepositoryImpl(dao, FakeChatSyncScheduler(), NoOpChatSearchIndexUpdater())
        val restoredMessage = recreatedRepository.observeMessages(session.id).first().single()

        assertEquals("assistant", restoredMessage.role)
        assertEquals("Siap.", restoredMessage.content)
        assertEquals(listOf(ChatPageLink("page-1", "Budget", "row", "row-1")), restoredMessage.pageLinks)
        assertEquals(metadata, restoredMessage.actionMetadata)
    }

    @Test
    fun malformedActionMetadataIsIgnoredWithoutDroppingMessage() = runBlocking {
        val dao = FakeChatMessageDao()
        dao.upsertSession(
            ChatSessionEntity(
                id = "session-1",
                scopeId = "workspace-chat",
                title = "Chat",
                createdAt = 100,
                updatedAt = 100,
                deletedAt = null,
            ),
        )
        dao.upsertMessage(
            ChatMessageEntity(
                id = "message-1",
                scopeId = "session-1",
                role = "assistant",
                content = "Broken metadata should not break chat.",
                pageLinksJson = "[]",
                actionMetadataJson = "{not valid json",
                createdAt = 100,
                updatedAt = 100,
            ),
        )

        val message = ChatHistoryRepositoryImpl(dao, FakeChatSyncScheduler(), NoOpChatSearchIndexUpdater())
            .observeMessages("session-1")
            .first()
            .single()

        assertEquals("Broken metadata should not break chat.", message.content)
        assertNull(message.actionMetadata)
    }

    @Test
    fun legacyActionMetadataWithoutAuditFieldsUsesSafeDefaults() = runBlocking {
        val dao = FakeChatMessageDao()
        dao.upsertSession(
            ChatSessionEntity(
                id = "session-1",
                scopeId = "workspace-chat",
                title = "Chat",
                createdAt = 100,
                updatedAt = 100,
                deletedAt = null,
            ),
        )
        dao.upsertMessage(
            ChatMessageEntity(
                id = "message-1",
                scopeId = "session-1",
                role = "assistant",
                content = "Old metadata.",
                pageLinksJson = "[]",
                actionMetadataJson = """
                    {
                      "mode": "Edit",
                      "schemaName": "CYL_ACTION_SCHEMA",
                      "schemaVersion": 1,
                      "proposedActions": [{"type": "ADD_TABLE_ROW", "target": "Budget"}],
                      "executedActions": [{"type": "ADD_TABLE_ROW", "target": "Budget"}],
                      "executionMessages": ["Done"],
                      "validationIssues": []
                    }
                """.trimIndent(),
                createdAt = 100,
                updatedAt = 100,
            ),
        )

        val metadata = ChatHistoryRepositoryImpl(dao, FakeChatSyncScheduler(), NoOpChatSearchIndexUpdater())
            .observeMessages("session-1")
            .first()
            .single()
            .actionMetadata

        assertEquals("", metadata?.auditId)
        assertEquals("", metadata?.requestMessageId)
        assertEquals(0L, metadata?.executedAt)
        assertEquals("", metadata?.provider)
        assertEquals("", metadata?.model)
        assertEquals("Edit", metadata?.mode)
        assertEquals(listOf(ChatActionMetadataItem("ADD_TABLE_ROW", "Budget")), metadata?.executedActions)
    }

    private class FakeChatMessageDao : ChatMessageDao {
        private val sessions = linkedMapOf<String, ChatSessionEntity>()
        private val messages = linkedMapOf<String, ChatMessageEntity>()

        override fun observeSessions(scopeId: String): Flow<List<ChatSessionEntity>> {
            return flowOf(
                sessions.values
                    .filter { session -> session.scopeId == scopeId && session.deletedAt == null }
                    .sortedByDescending { session -> session.updatedAt },
            )
        }

        override suspend fun getLatestSession(scopeId: String): ChatSessionEntity? {
            return sessions.values
                .filter { session -> session.scopeId == scopeId && session.deletedAt == null }
                .maxByOrNull { session -> session.updatedAt }
        }

        override suspend fun getSession(sessionId: String): ChatSessionEntity? {
            return sessions[sessionId]?.takeIf { session -> session.deletedAt == null }
        }

        override suspend fun getSessionIncludingDeleted(sessionId: String): ChatSessionEntity? {
            return sessions[sessionId]
        }

        override suspend fun getSessionsForScopeIncludingDeleted(scopeId: String): List<ChatSessionEntity> {
            return sessions.values
                .filter { session -> session.scopeId == scopeId }
                .sortedByDescending { session -> session.updatedAt }
        }

        override suspend fun getSessionsNeedingSync(syncedStatus: String): List<ChatSessionEntity> {
            return sessions.values
                .filter { session -> session.syncStatus != syncedStatus || session.lastSyncedAt == 0L }
                .sortedBy { session -> session.updatedAt }
        }

        override fun observeSessionsNeedingSyncCount(
            syncedStatus: String,
            conflictStatus: String,
        ): Flow<Int> {
            return flowOf(
                sessions.values.count { session ->
                    (session.syncStatus != syncedStatus || session.lastSyncedAt == 0L) &&
                        session.syncStatus != conflictStatus
                },
            )
        }

        override fun observeSessionConflictCount(conflictStatus: String): Flow<Int> {
            return flowOf(sessions.values.count { session -> session.syncStatus == conflictStatus })
        }

        override suspend fun upsertSession(session: ChatSessionEntity) {
            sessions[session.id] = session
        }

        override fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
            return flowOf(
                messages.values
                    .filter { message -> message.scopeId == sessionId }
                    .sortedBy { message -> message.createdAt },
            )
        }

        override fun observeMessagesForScope(scopeId: String): Flow<List<ChatMessageEntity>> {
            val visibleSessionIds = sessions.values
                .filter { session -> session.scopeId == scopeId && session.deletedAt == null }
                .map { session -> session.id }
                .toSet()
            return flowOf(
                messages.values
                    .filter { message -> message.scopeId in visibleSessionIds }
                    .sortedByDescending { message -> message.createdAt },
            )
        }

        override suspend fun getMessageCount(sessionId: String): Int {
            return messages.values.count { message -> message.scopeId == sessionId }
        }

        override suspend fun getMessage(messageId: String): ChatMessageEntity? {
            return messages[messageId]
        }

        override suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity> {
            return messages.values
                .filter { message -> message.scopeId == sessionId }
                .sortedBy { message -> message.createdAt }
        }

        override suspend fun getMessagesNeedingSync(syncedStatus: String): List<ChatMessageEntity> {
            return messages.values
                .filter { message -> message.syncStatus != syncedStatus || message.lastSyncedAt == 0L }
                .sortedBy { message -> message.updatedAt }
        }

        override fun observeMessagesNeedingSyncCount(
            syncedStatus: String,
            conflictStatus: String,
        ): Flow<Int> {
            return flowOf(
                messages.values.count { message ->
                    (message.syncStatus != syncedStatus || message.lastSyncedAt == 0L) &&
                        message.syncStatus != conflictStatus
                },
            )
        }

        override fun observeMessageConflictCount(conflictStatus: String): Flow<Int> {
            return flowOf(messages.values.count { message -> message.syncStatus == conflictStatus })
        }

        override suspend fun upsertMessage(message: ChatMessageEntity) {
            messages[message.id] = message
        }

        override suspend fun clearMessages(sessionId: String) {
            messages.entries.removeIf { (_, message) -> message.scopeId == sessionId }
        }

        override suspend fun softDeleteSession(sessionId: String, deletedAt: Long, syncStatus: String) {
            sessions[sessionId]?.let { session ->
                sessions[sessionId] = session.copy(
                    deletedAt = deletedAt,
                    updatedAt = deletedAt,
                    syncStatus = syncStatus,
                )
            }
        }

        override suspend fun softDeleteEmptySessions(scopeId: String, deletedAt: Long, syncStatus: String) {
            sessions.values
                .filter { session ->
                    session.scopeId == scopeId &&
                        session.deletedAt == null &&
                        messages.values.none { message -> message.scopeId == session.id }
                }
                .forEach { session ->
                    sessions[session.id] = session.copy(
                        deletedAt = deletedAt,
                        updatedAt = deletedAt,
                        syncStatus = syncStatus,
                    )
                }
        }
    }

    private class NoOpChatSearchIndexUpdater : ChatSearchIndexUpdater {
        override suspend fun rebuildChatScope(scopeId: String) = Unit

        override suspend fun rebuildChatSession(sessionId: String) = Unit
    }

    private class FakeChatSyncScheduler : ChatSyncScheduler {
        override fun pushSession(sessionId: String) = Unit
        override fun pushMessage(messageId: String) = Unit
        override fun pushPending() = Unit
    }
}
