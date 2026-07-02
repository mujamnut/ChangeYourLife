package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ChatMessageRecord
import com.changeyourlife.cyl.backend.domain.ChatSessionRecord
import com.changeyourlife.cyl.backend.domain.ChatSyncRepository
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresChatSyncRepository(
    private val dataSource: DataSource,
) : ChatSyncRepository {
    override suspend fun listSessions(
        userId: String,
        scopeId: String,
        updatedAfter: Long,
    ): List<ChatSessionRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, user_id, scope_id, title, created_at, updated_at, deleted_at
                FROM chat_sessions
                WHERE user_id = ?
                  AND scope_id = ?
                  AND updated_at > ?
                ORDER BY updated_at ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, scopeId)
                statement.setLong(3, updatedAfter)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toChatSessionRecord())
                    }
                }
            }
        }
    }

    override suspend fun upsertSession(userId: String, session: ChatSessionRecord): ChatSessionRecord? =
        withContext(Dispatchers.IO) {
            if (session.userId != userId) return@withContext null
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO chat_sessions (
                        id, user_id, scope_id, title, created_at, updated_at, deleted_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, id) DO UPDATE SET
                        scope_id = EXCLUDED.scope_id,
                        title = EXCLUDED.title,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at
                    RETURNING id, user_id, scope_id, title, created_at, updated_at, deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.bind(session)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toChatSessionRecord() else null
                    }
                }
            }
        }

    override suspend fun listMessages(
        userId: String,
        sessionId: String,
        updatedAfter: Long,
    ): List<ChatMessageRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT chat_messages.id,
                       chat_messages.user_id,
                       chat_messages.session_id,
                       chat_messages.scope_id,
                       chat_messages.role,
                       chat_messages.content,
                       chat_messages.page_links_json,
                       chat_messages.action_metadata_json,
                       chat_messages.created_at,
                       chat_messages.updated_at
                FROM chat_messages
                INNER JOIN chat_sessions
                    ON chat_sessions.user_id = chat_messages.user_id
                    AND chat_sessions.id = chat_messages.session_id
                WHERE chat_messages.user_id = ?
                  AND chat_messages.session_id = ?
                  AND chat_messages.updated_at > ?
                ORDER BY chat_messages.created_at ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, sessionId)
                statement.setLong(3, updatedAfter)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toChatMessageRecord())
                    }
                }
            }
        }
    }

    override suspend fun upsertMessage(userId: String, message: ChatMessageRecord): ChatMessageRecord? =
        withContext(Dispatchers.IO) {
            if (message.userId != userId) return@withContext null
            dataSource.connection.use { connection ->
                val sessionScope = connection.prepareStatement(
                    """
                    SELECT scope_id
                    FROM chat_sessions
                    WHERE user_id = ? AND id = ?
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.setString(2, message.sessionId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.getString("scope_id") else null
                    }
                } ?: return@withContext null
                if (sessionScope != message.scopeId) return@withContext null

                connection.prepareStatement(
                    """
                    INSERT INTO chat_messages (
                        id,
                        user_id,
                        session_id,
                        scope_id,
                        role,
                        content,
                        page_links_json,
                        action_metadata_json,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, id) DO UPDATE SET
                        session_id = EXCLUDED.session_id,
                        scope_id = EXCLUDED.scope_id,
                        role = EXCLUDED.role,
                        content = EXCLUDED.content,
                        page_links_json = EXCLUDED.page_links_json,
                        action_metadata_json = EXCLUDED.action_metadata_json,
                        updated_at = EXCLUDED.updated_at
                    RETURNING id,
                              user_id,
                              session_id,
                              scope_id,
                              role,
                              content,
                              page_links_json,
                              action_metadata_json,
                              created_at,
                              updated_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.bind(message)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toChatMessageRecord() else null
                    }
                }
            }
        }
}

private fun PreparedStatement.bind(session: ChatSessionRecord) {
    setString(1, session.id)
    setString(2, session.userId)
    setString(3, session.scopeId)
    setString(4, session.title)
    setLong(5, session.createdAt)
    setLong(6, session.updatedAt)
    if (session.deletedAt == null) {
        setObject(7, null)
    } else {
        setLong(7, session.deletedAt)
    }
}

private fun PreparedStatement.bind(message: ChatMessageRecord) {
    setString(1, message.id)
    setString(2, message.userId)
    setString(3, message.sessionId)
    setString(4, message.scopeId)
    setString(5, message.role)
    setString(6, message.content)
    setString(7, message.pageLinksJson)
    setString(8, message.actionMetadataJson)
    setLong(9, message.createdAt)
    setLong(10, message.updatedAt)
}

private fun ResultSet.toChatSessionRecord(): ChatSessionRecord {
    return ChatSessionRecord(
        id = getString("id"),
        userId = getString("user_id"),
        scopeId = getString("scope_id"),
        title = getString("title"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getNullableLong("deleted_at"),
    )
}

private fun ResultSet.toChatMessageRecord(): ChatMessageRecord {
    return ChatMessageRecord(
        id = getString("id"),
        userId = getString("user_id"),
        sessionId = getString("session_id"),
        scopeId = getString("scope_id"),
        role = getString("role"),
        content = getString("content"),
        pageLinksJson = getString("page_links_json"),
        actionMetadataJson = getString("action_metadata_json"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
    )
}

private fun ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}
