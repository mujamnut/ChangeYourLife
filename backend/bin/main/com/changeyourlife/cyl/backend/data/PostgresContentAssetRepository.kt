package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentAssetClaim
import com.changeyourlife.cyl.backend.domain.ContentAssetErrorCode
import com.changeyourlife.cyl.backend.domain.ContentAssetIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ContentAssetKind
import com.changeyourlife.cyl.backend.domain.ContentAssetRecord
import com.changeyourlife.cyl.backend.domain.ContentAssetRepository
import com.changeyourlife.cyl.backend.domain.ContentAssetStatus
import com.changeyourlife.cyl.backend.domain.ContentAssetUpdate
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresContentAssetRepository(
    private val dataSource: DataSource,
) : ContentAssetRepository {
    override suspend fun claim(record: ContentAssetRecord): ContentAssetClaim = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val inserted = connection.prepareStatement(
                """
                INSERT INTO content_assets ($AssetColumns)
                VALUES (${List(AssetColumnCount) { "?" }.joinToString()})
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """.trimIndent(),
            ).use { statement ->
                statement.bind(record)
                statement.executeUpdate() == 1
            }
            if (inserted) {
                ContentAssetClaim(record, isNew = true)
            } else {
                val existing = connection.selectByIdempotency(record.ownerId, record.idempotencyKey)
                val claimed = checkNotNull(existing) {
                    "Idempotent content asset claim conflicted but could not be loaded."
                }
                if (claimed.requestFingerprint != record.requestFingerprint) {
                    throw ContentAssetIdempotencyConflictException()
                }
                ContentAssetClaim(claimed, isNew = false)
            }
        }
    }

    override suspend fun get(
        ownerId: String,
        assetId: String,
        includeDeleted: Boolean,
    ): ContentAssetRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection -> connection.selectAsset(ownerId, assetId, includeDeleted) }
    }

    override suspend fun updateLifecycle(
        ownerId: String,
        assetId: String,
        expectedStatuses: Set<ContentAssetStatus>,
        nextStatus: ContentAssetStatus,
        errorCode: ContentAssetErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long?,
    ): ContentAssetUpdate = withContext(Dispatchers.IO) {
        require(expectedStatuses.isNotEmpty()) { "expectedStatuses must not be empty." }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val placeholders = expectedStatuses.joinToString { "?" }
                val updatedRows = connection.prepareStatement(
                    """
                    UPDATE content_assets
                    SET status = ?,
                        error_code = ?,
                        updated_at = ?,
                        deleted_at = CASE WHEN ? = 'deleted' THEN COALESCE(?, updated_at) ELSE deleted_at END
                    WHERE user_id = ? AND id = ? AND status IN ($placeholders)
                    """.trimIndent(),
                ).use { statement ->
                    var index = 1
                    statement.setString(index++, nextStatus.wireValue)
                    statement.setString(index++, errorCode?.wireValue)
                    statement.setLong(index++, updatedAtEpochMillis)
                    statement.setString(index++, nextStatus.wireValue)
                    statement.setObject(index++, deletedAtEpochMillis)
                    statement.setString(index++, ownerId)
                    statement.setString(index++, assetId)
                    expectedStatuses.forEach { status -> statement.setString(index++, status.wireValue) }
                    statement.executeUpdate()
                }
                val current = connection.selectAsset(ownerId, assetId, includeDeleted = true)
                connection.commit()
                when {
                    updatedRows == 1 && current != null -> ContentAssetUpdate.Updated(current)
                    current == null -> ContentAssetUpdate.NotFound
                    else -> ContentAssetUpdate.Conflict(current)
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
        assetId: String,
        deletedAtEpochMillis: Long,
    ): ContentAssetRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE content_assets
                SET storage_deleted_at = ?, error_code = NULL, updated_at = GREATEST(updated_at, ?)
                WHERE user_id = ? AND id = ? AND status = 'deleted'
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, deletedAtEpochMillis)
                statement.setLong(2, deletedAtEpochMillis)
                statement.setString(3, ownerId)
                statement.setString(4, assetId)
                statement.executeUpdate()
            }
            connection.selectAsset(ownerId, assetId, includeDeleted = true)
        }
    }

    override suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ContentAssetRecord> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT $AssetColumns FROM content_assets
                WHERE deleted_at IS NULL
                  AND created_at < ?
                  AND status IN ('upload_queued', 'retryable_failure')
                ORDER BY created_at ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, createdBeforeEpochMillis)
                statement.setInt(2, limit)
                statement.executeQuery().use(ResultSet::toContentAssetList)
            }
        }
    }

    override suspend fun listPendingStorageDeletes(limit: Int): List<ContentAssetRecord> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext emptyList()
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT $AssetColumns FROM content_assets
                    WHERE deleted_at IS NOT NULL AND storage_deleted_at IS NULL
                    ORDER BY deleted_at ASC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use(ResultSet::toContentAssetList)
                }
            }
        }
}

private fun Connection.selectByIdempotency(ownerId: String, key: String): ContentAssetRecord? =
    prepareStatement(
        "SELECT $AssetColumns FROM content_assets WHERE user_id = ? AND idempotency_key = ? LIMIT 1",
    ).use { statement ->
        statement.setString(1, ownerId)
        statement.setString(2, key)
        statement.executeQuery().use { result -> if (result.next()) result.toContentAsset() else null }
    }

private fun Connection.selectAsset(
    ownerId: String,
    assetId: String,
    includeDeleted: Boolean,
): ContentAssetRecord? = prepareStatement(
    """
    SELECT $AssetColumns FROM content_assets
    WHERE user_id = ? AND id = ? ${if (includeDeleted) "" else "AND deleted_at IS NULL"}
    LIMIT 1
    """.trimIndent(),
).use { statement ->
    statement.setString(1, ownerId)
    statement.setString(2, assetId)
    statement.executeQuery().use { result -> if (result.next()) result.toContentAsset() else null }
}

private fun PreparedStatement.bind(record: ContentAssetRecord) {
    setString(1, record.assetId)
    setString(2, record.ownerId)
    setString(3, record.workspaceId)
    setString(4, record.pageId)
    setString(5, record.kind.wireValue)
    setString(6, record.storageKey)
    setString(7, record.mimeType)
    setString(8, record.originalName)
    setLong(9, record.sizeBytes)
    setString(10, record.sha256)
    setString(11, record.status.wireValue)
    setString(12, record.errorCode?.wireValue)
    setString(13, record.idempotencyKey)
    setString(14, record.requestFingerprint)
    setLong(15, record.createdAtEpochMillis)
    setLong(16, record.updatedAtEpochMillis)
    setObject(17, record.deletedAtEpochMillis)
    setObject(18, record.storageDeletedAtEpochMillis)
}

private fun ResultSet.toContentAssetList(): List<ContentAssetRecord> = buildList {
    while (next()) add(toContentAsset())
}

private fun ResultSet.toContentAsset(): ContentAssetRecord = ContentAssetRecord(
    assetId = getString("id"),
    ownerId = getString("user_id"),
    workspaceId = getString("workspace_id"),
    pageId = getString("page_id"),
    kind = checkNotNull(ContentAssetKind.fromWireValue(getString("kind"))),
    storageKey = getString("storage_key"),
    mimeType = getString("mime_type"),
    originalName = getString("original_name"),
    sizeBytes = getLong("size_bytes"),
    sha256 = getString("sha256"),
    status = ContentAssetStatus.fromWireValue(getString("status")),
    errorCode = getString("error_code")?.let(ContentAssetErrorCode::fromWireValue),
    idempotencyKey = getString("idempotency_key"),
    requestFingerprint = getString("request_fingerprint"),
    createdAtEpochMillis = getLong("created_at"),
    updatedAtEpochMillis = getLong("updated_at"),
    deletedAtEpochMillis = nullableAssetLong("deleted_at"),
    storageDeletedAtEpochMillis = nullableAssetLong("storage_deleted_at"),
)

private fun ResultSet.nullableAssetLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private const val AssetColumnCount = 18
private const val AssetColumns = """
    id, user_id, workspace_id, page_id, kind, storage_key, mime_type, original_name,
    size_bytes, sha256, status, error_code, idempotency_key, request_fingerprint,
    created_at, updated_at, deleted_at, storage_deleted_at
"""
