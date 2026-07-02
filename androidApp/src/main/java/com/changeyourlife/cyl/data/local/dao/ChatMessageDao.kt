package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE scopeId = :scopeId AND deletedAt IS NULL
        ORDER BY updatedAt DESC
        """,
    )
    fun observeSessions(scopeId: String): Flow<List<ChatSessionEntity>>

    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE scopeId = :scopeId AND deletedAt IS NULL
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestSession(scopeId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId AND deletedAt IS NULL LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionIncludingDeleted(sessionId: String): ChatSessionEntity?

    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE scopeId = :scopeId
        ORDER BY updatedAt DESC
        """,
    )
    suspend fun getSessionsForScopeIncludingDeleted(scopeId: String): List<ChatSessionEntity>

    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE syncStatus != :syncedStatus OR lastSyncedAt = 0
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getSessionsNeedingSync(syncedStatus: String = SyncStatus.Synced): List<ChatSessionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM chat_sessions
        WHERE (syncStatus != :syncedStatus OR lastSyncedAt = 0)
        AND syncStatus != :conflictStatus
        """,
    )
    fun observeSessionsNeedingSyncCount(
        syncedStatus: String = SyncStatus.Synced,
        conflictStatus: String = SyncStatus.Conflict,
    ): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE syncStatus = :conflictStatus")
    fun observeSessionConflictCount(conflictStatus: String = SyncStatus.Conflict): Flow<Int>

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scopeId = :sessionId
        ORDER BY createdAt ASC
        """,
    )
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT chat_messages.* FROM chat_messages
        INNER JOIN chat_sessions ON chat_sessions.id = chat_messages.scopeId
        WHERE chat_sessions.scopeId = :scopeId AND chat_sessions.deletedAt IS NULL
        ORDER BY chat_messages.createdAt DESC
        """,
    )
    fun observeMessagesForScope(scopeId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE scopeId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): ChatMessageEntity?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE scopeId = :sessionId
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE syncStatus != :syncedStatus OR lastSyncedAt = 0
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getMessagesNeedingSync(syncedStatus: String = SyncStatus.Synced): List<ChatMessageEntity>

    @Query(
        """
        SELECT COUNT(*) FROM chat_messages
        WHERE (syncStatus != :syncedStatus OR lastSyncedAt = 0)
        AND syncStatus != :conflictStatus
        """,
    )
    fun observeMessagesNeedingSyncCount(
        syncedStatus: String = SyncStatus.Synced,
        conflictStatus: String = SyncStatus.Conflict,
    ): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE syncStatus = :conflictStatus")
    fun observeMessageConflictCount(conflictStatus: String = SyncStatus.Conflict): Flow<Int>

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE scopeId = :sessionId")
    suspend fun clearMessages(sessionId: String)

    @Query(
        """
        UPDATE chat_sessions
        SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = :syncStatus
        WHERE id = :sessionId
        """,
    )
    suspend fun softDeleteSession(
        sessionId: String,
        deletedAt: Long,
        syncStatus: String = SyncStatus.PendingPush,
    )

    @Query(
        """
        UPDATE chat_sessions
        SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = :syncStatus
        WHERE scopeId = :scopeId
            AND deletedAt IS NULL
            AND NOT EXISTS (
                SELECT 1 FROM chat_messages
                WHERE chat_messages.scopeId = chat_sessions.id
            )
        """,
    )
    suspend fun softDeleteEmptySessions(
        scopeId: String,
        deletedAt: Long,
        syncStatus: String = SyncStatus.PendingPush,
    )
}
