package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.backend.domain.ChatAttachmentClaim
import com.changeyourlife.cyl.backend.domain.ChatAttachmentIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRecord
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRepository
import com.changeyourlife.cyl.backend.domain.ChatAttachmentUpdate
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresChatAttachmentRepository(
    private val dataSource: DataSource,
) : ChatAttachmentRepository {
    override suspend fun claim(record: ChatAttachmentRecord): ChatAttachmentClaim = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val inserted = connection.prepareStatement(
                """
                INSERT INTO chat_attachments ($AttachmentColumns)
                VALUES (${List(AttachmentColumnCount) { "?" }.joinToString()})
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """.trimIndent(),
            ).use { statement ->
                statement.bind(record)
                statement.executeUpdate() == 1
            }
            if (inserted) {
                ChatAttachmentClaim(record, isNew = true)
            } else {
                val existing = connection.selectByIdempotency(record.ownerId, record.idempotencyKey)
                val claimed = checkNotNull(existing) {
                    "Idempotent attachment claim conflicted but the existing record could not be loaded."
                }
                if (claimed.requestFingerprint != record.requestFingerprint) {
                    throw ChatAttachmentIdempotencyConflictException()
                }
                ChatAttachmentClaim(claimed, isNew = false)
            }
        }
    }

    override suspend fun get(
        ownerId: String,
        attachmentId: String,
        includeDeleted: Boolean,
    ): ChatAttachmentRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.selectById(ownerId, attachmentId, includeDeleted)
        }
    }

    override suspend fun updateLifecycle(
        ownerId: String,
        attachmentId: String,
        expectedStatuses: Set<ChatAttachmentStatus>,
        nextStatus: ChatAttachmentStatus,
        errorCode: ChatAttachmentErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long?,
    ): ChatAttachmentUpdate = withContext(Dispatchers.IO) {
        require(expectedStatuses.isNotEmpty()) { "expectedStatuses must not be empty." }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val statusPlaceholders = expectedStatuses.joinToString { "?" }
                val updatedRows = connection.prepareStatement(
                    """
                    UPDATE chat_attachments
                    SET status = ?,
                        error_code = ?,
                        updated_at = ?,
                        deleted_at = CASE WHEN ? = 'deleted' THEN COALESCE(?, updated_at) ELSE deleted_at END
                    WHERE user_id = ?
                      AND id = ?
                      AND status IN ($statusPlaceholders)
                    """.trimIndent(),
                ).use { statement ->
                    var index = 1
                    statement.setString(index++, nextStatus.wireValue)
                    statement.setString(index++, errorCode?.wireValue)
                    statement.setLong(index++, updatedAtEpochMillis)
                    statement.setString(index++, nextStatus.wireValue)
                    statement.setObject(index++, deletedAtEpochMillis)
                    statement.setString(index++, ownerId)
                    statement.setString(index++, attachmentId)
                    expectedStatuses.forEach { status -> statement.setString(index++, status.wireValue) }
                    statement.executeUpdate()
                }
                val current = connection.selectById(ownerId, attachmentId, includeDeleted = true)
                connection.commit()
                when {
                    updatedRows == 1 && current != null -> ChatAttachmentUpdate.Updated(current)
                    current == null -> ChatAttachmentUpdate.NotFound
                    else -> ChatAttachmentUpdate.Conflict(current)
                }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun markStorageDeleted(
        ownerId: String,
        attachmentId: String,
        deletedAtEpochMillis: Long,
    ): ChatAttachmentRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE chat_attachments
                SET storage_deleted_at = ?,
                    error_code = NULL,
                    updated_at = GREATEST(updated_at, ?)
                WHERE user_id = ? AND id = ? AND status = 'deleted'
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, deletedAtEpochMillis)
                statement.setLong(2, deletedAtEpochMillis)
                statement.setString(3, ownerId)
                statement.setString(4, attachmentId)
                statement.executeUpdate()
            }
            connection.selectById(ownerId, attachmentId, includeDeleted = true)
        }
    }

    override suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ChatAttachmentRecord> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT $AttachmentColumns
                FROM chat_attachments
                WHERE deleted_at IS NULL
                  AND created_at < ?
                  AND (
                      status = 'pending_upload'
                      OR (
                          status = 'retryable_failure'
                          AND error_code IN (
                              'upload_offline',
                              'upload_url_expired',
                              'upload_validation_failed',
                              'storage_unavailable'
                          )
                      )
                  )
                ORDER BY created_at ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, createdBeforeEpochMillis)
                statement.setInt(2, limit)
                statement.executeQuery().use(ResultSet::toAttachmentList)
            }
        }
    }

    override suspend fun listPendingStorageDeletes(limit: Int): List<ChatAttachmentRecord> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext emptyList()
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT $AttachmentColumns
                    FROM chat_attachments
                    WHERE deleted_at IS NOT NULL AND storage_deleted_at IS NULL
                    ORDER BY deleted_at ASC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use(ResultSet::toAttachmentList)
                }
            }
        }
}

private fun Connection.selectByIdempotency(ownerId: String, idempotencyKey: String): ChatAttachmentRecord? =
    prepareStatement(
        """
        SELECT $AttachmentColumns
        FROM chat_attachments
        WHERE user_id = ? AND idempotency_key = ?
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, ownerId)
        statement.setString(2, idempotencyKey)
        statement.executeQuery().use { resultSet ->
            if (resultSet.next()) resultSet.toChatAttachmentRecord() else null
        }
    }

private fun Connection.selectById(
    ownerId: String,
    attachmentId: String,
    includeDeleted: Boolean,
): ChatAttachmentRecord? = prepareStatement(
    """
    SELECT $AttachmentColumns
    FROM chat_attachments
    WHERE user_id = ? AND id = ?
      ${if (includeDeleted) "" else "AND deleted_at IS NULL"}
    LIMIT 1
    """.trimIndent(),
).use { statement ->
    statement.setString(1, ownerId)
    statement.setString(2, attachmentId)
    statement.executeQuery().use { resultSet ->
        if (resultSet.next()) resultSet.toChatAttachmentRecord() else null
    }
}

private fun PreparedStatement.bind(record: ChatAttachmentRecord) {
    setString(1, record.attachmentId)
    setString(2, record.ownerId)
    setString(3, record.sessionClientId)
    setString(4, record.messageClientId)
    setString(5, record.kind.wireValue)
    setString(6, record.storageKey)
    setString(7, record.mimeType)
    setString(8, record.originalName)
    setLong(9, record.sizeBytes)
    setLong(10, record.durationMs)
    setString(11, record.sha256)
    setString(12, record.status.wireValue)
    setString(13, record.transcript)
    setString(14, record.transcriptLanguage)
    setString(15, record.transcriptionProvider)
    setString(16, record.transcriptionModel)
    setString(17, record.transcriptionVersion)
    setString(18, record.errorCode?.wireValue)
    setString(19, record.idempotencyKey)
    setString(20, record.requestFingerprint)
    setLong(21, record.createdAtEpochMillis)
    setLong(22, record.updatedAtEpochMillis)
    setObject(23, record.deletedAtEpochMillis)
    setObject(24, record.storageDeletedAtEpochMillis)
}

private fun ResultSet.toAttachmentList(): List<ChatAttachmentRecord> = buildList {
    while (next()) add(toChatAttachmentRecord())
}

private fun ResultSet.toChatAttachmentRecord(): ChatAttachmentRecord = ChatAttachmentRecord(
    attachmentId = getString("id"),
    ownerId = getString("user_id"),
    sessionClientId = getString("session_client_id"),
    messageClientId = getString("message_client_id"),
    kind = ChatAttachmentKind.fromWireValue(getString("kind")),
    storageKey = getString("storage_key"),
    mimeType = getString("mime_type"),
    originalName = getString("original_name"),
    sizeBytes = getLong("size_bytes"),
    durationMs = getLong("duration_ms"),
    sha256 = getString("sha256"),
    status = ChatAttachmentStatus.fromWireValue(getString("status")),
    transcript = getString("transcript"),
    transcriptLanguage = getString("transcript_language"),
    transcriptionProvider = getString("transcription_provider"),
    transcriptionModel = getString("transcription_model"),
    transcriptionVersion = getString("transcription_version"),
    errorCode = getString("error_code")?.let(ChatAttachmentErrorCode::fromWireValue),
    idempotencyKey = getString("idempotency_key"),
    requestFingerprint = getString("request_fingerprint"),
    createdAtEpochMillis = getLong("created_at"),
    updatedAtEpochMillis = getLong("updated_at"),
    deletedAtEpochMillis = nullableLong("deleted_at"),
    storageDeletedAtEpochMillis = nullableLong("storage_deleted_at"),
)

private fun ResultSet.nullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private const val AttachmentColumnCount = 24

private const val AttachmentColumns = """
    id,
    user_id,
    session_client_id,
    message_client_id,
    kind,
    storage_key,
    mime_type,
    original_name,
    size_bytes,
    duration_ms,
    sha256,
    status,
    transcript,
    transcript_language,
    transcription_provider,
    transcription_model,
    transcription_version,
    error_code,
    idempotency_key,
    request_fingerprint,
    created_at,
    updated_at,
    deleted_at,
    storage_deleted_at
"""
