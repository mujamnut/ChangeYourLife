package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiActionLogRecord
import com.changeyourlife.cyl.backend.domain.AiActionLogRepository
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresAiActionLogRepository(
    private val dataSource: DataSource,
) : AiActionLogRepository {
    override suspend fun listActionLogs(
        userId: String,
        workspaceId: String,
        updatedAfter: Long,
    ): List<AiActionLogRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT audit_id,
                       user_id,
                       request_message_id,
                       response_message_id,
                       session_id,
                       workspace_id,
                       mode,
                       provider,
                       model,
                       schema_name,
                       schema_version,
                       proposed_actions_json,
                       executed_actions_json,
                       validation_issues_json,
                       execution_messages_json,
                       undo_commands_json,
                       undo_state,
                       created_at,
                       updated_at
                FROM ai_action_logs
                WHERE user_id = ?
                  AND workspace_id = ?
                  AND updated_at > ?
                ORDER BY updated_at ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, workspaceId)
                statement.setLong(3, updatedAfter)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toAiActionLogRecord())
                    }
                }
            }
        }
    }

    override suspend fun upsertActionLog(userId: String, actionLog: AiActionLogRecord): AiActionLogRecord? =
        withContext(Dispatchers.IO) {
            if (actionLog.userId != userId) return@withContext null
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO ai_action_logs (
                        audit_id,
                        user_id,
                        request_message_id,
                        response_message_id,
                        session_id,
                        workspace_id,
                        mode,
                        provider,
                        model,
                        schema_name,
                        schema_version,
                        proposed_actions_json,
                        executed_actions_json,
                        validation_issues_json,
                        execution_messages_json,
                        undo_commands_json,
                        undo_state,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, audit_id) DO UPDATE SET
                        request_message_id = EXCLUDED.request_message_id,
                        response_message_id = EXCLUDED.response_message_id,
                        session_id = EXCLUDED.session_id,
                        workspace_id = EXCLUDED.workspace_id,
                        mode = EXCLUDED.mode,
                        provider = EXCLUDED.provider,
                        model = EXCLUDED.model,
                        schema_name = EXCLUDED.schema_name,
                        schema_version = EXCLUDED.schema_version,
                        proposed_actions_json = EXCLUDED.proposed_actions_json,
                        executed_actions_json = EXCLUDED.executed_actions_json,
                        validation_issues_json = EXCLUDED.validation_issues_json,
                        execution_messages_json = EXCLUDED.execution_messages_json,
                        undo_commands_json = EXCLUDED.undo_commands_json,
                        undo_state = EXCLUDED.undo_state,
                        updated_at = EXCLUDED.updated_at
                    RETURNING audit_id,
                              user_id,
                              request_message_id,
                              response_message_id,
                              session_id,
                              workspace_id,
                              mode,
                              provider,
                              model,
                              schema_name,
                              schema_version,
                              proposed_actions_json,
                              executed_actions_json,
                              validation_issues_json,
                              execution_messages_json,
                              undo_commands_json,
                              undo_state,
                              created_at,
                              updated_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.bind(actionLog)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toAiActionLogRecord() else null
                    }
                }
            }
        }
}

private fun PreparedStatement.bind(actionLog: AiActionLogRecord) {
    setString(1, actionLog.auditId)
    setString(2, actionLog.userId)
    setString(3, actionLog.requestMessageId)
    setString(4, actionLog.responseMessageId)
    setString(5, actionLog.sessionId)
    setString(6, actionLog.workspaceId)
    setString(7, actionLog.mode)
    setString(8, actionLog.provider)
    setString(9, actionLog.model)
    setString(10, actionLog.schemaName)
    setInt(11, actionLog.schemaVersion)
    setString(12, actionLog.proposedActionsJson)
    setString(13, actionLog.executedActionsJson)
    setString(14, actionLog.validationIssuesJson)
    setString(15, actionLog.executionMessagesJson)
    setString(16, actionLog.undoCommandsJson)
    setString(17, actionLog.undoState)
    setLong(18, actionLog.createdAt)
    setLong(19, actionLog.updatedAt)
}

private fun ResultSet.toAiActionLogRecord(): AiActionLogRecord {
    return AiActionLogRecord(
        auditId = getString("audit_id"),
        userId = getString("user_id"),
        requestMessageId = getString("request_message_id"),
        responseMessageId = getString("response_message_id"),
        sessionId = getString("session_id"),
        workspaceId = getString("workspace_id"),
        mode = getString("mode"),
        provider = getString("provider"),
        model = getString("model"),
        schemaName = getString("schema_name"),
        schemaVersion = getInt("schema_version"),
        proposedActionsJson = getString("proposed_actions_json"),
        executedActionsJson = getString("executed_actions_json"),
        validationIssuesJson = getString("validation_issues_json"),
        executionMessagesJson = getString("execution_messages_json"),
        undoCommandsJson = getString("undo_commands_json"),
        undoState = getString("undo_state"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
    )
}
