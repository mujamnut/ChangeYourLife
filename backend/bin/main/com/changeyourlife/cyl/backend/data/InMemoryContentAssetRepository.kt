package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentAssetClaim
import com.changeyourlife.cyl.backend.domain.ContentAssetErrorCode
import com.changeyourlife.cyl.backend.domain.ContentAssetIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ContentAssetRecord
import com.changeyourlife.cyl.backend.domain.ContentAssetRepository
import com.changeyourlife.cyl.backend.domain.ContentAssetStatus
import com.changeyourlife.cyl.backend.domain.ContentAssetUpdate
import com.changeyourlife.cyl.backend.domain.canTransitionTo

class InMemoryContentAssetRepository : ContentAssetRepository {
    private val lock = Any()
    private val records = linkedMapOf<String, ContentAssetRecord>()
    private val idempotency = linkedMapOf<String, String>()

    override suspend fun claim(record: ContentAssetRecord): ContentAssetClaim = synchronized(lock) {
        val idempotencyKey = record.ownerId.key(record.idempotencyKey)
        idempotency[idempotencyKey]?.let { existingId ->
            val existing = checkNotNull(records[record.ownerId.key(existingId)])
            if (existing.requestFingerprint != record.requestFingerprint) {
                throw ContentAssetIdempotencyConflictException()
            }
            return@synchronized ContentAssetClaim(existing, isNew = false)
        }
        val recordKey = record.ownerId.key(record.assetId)
        check(recordKey !in records) { "Content asset id already exists for this owner." }
        records[recordKey] = record
        idempotency[idempotencyKey] = record.assetId
        ContentAssetClaim(record, isNew = true)
    }

    override suspend fun get(
        ownerId: String,
        assetId: String,
        includeDeleted: Boolean,
    ): ContentAssetRecord? = synchronized(lock) {
        records[ownerId.key(assetId)]
            ?.takeIf { record -> includeDeleted || record.deletedAtEpochMillis == null }
    }

    override suspend fun updateLifecycle(
        ownerId: String,
        assetId: String,
        expectedStatuses: Set<ContentAssetStatus>,
        nextStatus: ContentAssetStatus,
        errorCode: ContentAssetErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long?,
    ): ContentAssetUpdate = synchronized(lock) {
        val key = ownerId.key(assetId)
        val current = records[key] ?: return@synchronized ContentAssetUpdate.NotFound
        if (current.status !in expectedStatuses || !current.status.canTransitionTo(nextStatus)) {
            return@synchronized ContentAssetUpdate.Conflict(current)
        }
        val updated = current.copy(
            status = nextStatus,
            errorCode = errorCode,
            updatedAtEpochMillis = updatedAtEpochMillis,
            deletedAtEpochMillis = if (nextStatus == ContentAssetStatus.Deleted) {
                deletedAtEpochMillis ?: updatedAtEpochMillis
            } else {
                current.deletedAtEpochMillis
            },
        )
        records[key] = updated
        ContentAssetUpdate.Updated(updated)
    }

    override suspend fun markStorageDeleted(
        ownerId: String,
        assetId: String,
        deletedAtEpochMillis: Long,
    ): ContentAssetRecord? = synchronized(lock) {
        val key = ownerId.key(assetId)
        val current = records[key]?.takeIf { it.status == ContentAssetStatus.Deleted }
            ?: return@synchronized null
        current.copy(
            errorCode = null,
            updatedAtEpochMillis = maxOf(current.updatedAtEpochMillis, deletedAtEpochMillis),
            storageDeletedAtEpochMillis = deletedAtEpochMillis,
        ).also { updated -> records[key] = updated }
    }

    override suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ContentAssetRecord> = synchronized(lock) {
        records.values.asSequence()
            .filter { record ->
                record.deletedAtEpochMillis == null &&
                    record.createdAtEpochMillis < createdBeforeEpochMillis &&
                    record.status in setOf(
                        ContentAssetStatus.UploadQueued,
                        ContentAssetStatus.RetryableFailure,
                    )
            }
            .sortedBy(ContentAssetRecord::createdAtEpochMillis)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    override suspend fun listPendingStorageDeletes(limit: Int): List<ContentAssetRecord> = synchronized(lock) {
        records.values.asSequence()
            .filter { record ->
                record.deletedAtEpochMillis != null && record.storageDeletedAtEpochMillis == null
            }
            .sortedBy(ContentAssetRecord::deletedAtEpochMillis)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun String.key(value: String): String = "$this\u0000$value"
}
