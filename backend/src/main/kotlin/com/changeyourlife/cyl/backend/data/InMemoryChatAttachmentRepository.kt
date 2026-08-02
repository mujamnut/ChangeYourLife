package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.aicontract.canTransitionTo
import com.changeyourlife.cyl.backend.domain.ChatAttachmentClaim
import com.changeyourlife.cyl.backend.domain.ChatAttachmentIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRecord
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRepository
import com.changeyourlife.cyl.backend.domain.ChatAttachmentUpdate

class InMemoryChatAttachmentRepository : ChatAttachmentRepository {
    private val lock = Any()
    private val recordsByOwnerAndId = linkedMapOf<String, ChatAttachmentRecord>()
    private val idByOwnerAndIdempotency = linkedMapOf<String, String>()

    override suspend fun claim(record: ChatAttachmentRecord): ChatAttachmentClaim = synchronized(lock) {
        val idempotencyMapKey = record.ownerId.key(record.idempotencyKey)
        val existingId = idByOwnerAndIdempotency[idempotencyMapKey]
        if (existingId != null) {
            val existing = checkNotNull(recordsByOwnerAndId[record.ownerId.key(existingId)])
            if (existing.requestFingerprint != record.requestFingerprint) {
                throw ChatAttachmentIdempotencyConflictException()
            }
            return@synchronized ChatAttachmentClaim(existing, isNew = false)
        }

        val recordKey = record.ownerId.key(record.attachmentId)
        check(recordKey !in recordsByOwnerAndId) { "Attachment id already exists for this owner." }
        recordsByOwnerAndId[recordKey] = record
        idByOwnerAndIdempotency[idempotencyMapKey] = record.attachmentId
        ChatAttachmentClaim(record, isNew = true)
    }

    override suspend fun get(
        ownerId: String,
        attachmentId: String,
        includeDeleted: Boolean,
    ): ChatAttachmentRecord? = synchronized(lock) {
        recordsByOwnerAndId[ownerId.key(attachmentId)]
            ?.takeIf { record -> includeDeleted || record.deletedAtEpochMillis == null }
    }

    override suspend fun updateLifecycle(
        ownerId: String,
        attachmentId: String,
        expectedStatuses: Set<ChatAttachmentStatus>,
        nextStatus: ChatAttachmentStatus,
        errorCode: ChatAttachmentErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long?,
    ): ChatAttachmentUpdate = synchronized(lock) {
        val key = ownerId.key(attachmentId)
        val current = recordsByOwnerAndId[key] ?: return@synchronized ChatAttachmentUpdate.NotFound
        if (current.status !in expectedStatuses || !current.status.canTransitionTo(nextStatus)) {
            return@synchronized ChatAttachmentUpdate.Conflict(current)
        }
        val updated = current.copy(
            status = nextStatus,
            errorCode = errorCode,
            updatedAtEpochMillis = updatedAtEpochMillis,
            deletedAtEpochMillis = when (nextStatus) {
                ChatAttachmentStatus.Deleted -> deletedAtEpochMillis ?: updatedAtEpochMillis
                else -> current.deletedAtEpochMillis
            },
        )
        recordsByOwnerAndId[key] = updated
        ChatAttachmentUpdate.Updated(updated)
    }

    override suspend fun markStorageDeleted(
        ownerId: String,
        attachmentId: String,
        deletedAtEpochMillis: Long,
    ): ChatAttachmentRecord? = synchronized(lock) {
        val key = ownerId.key(attachmentId)
        val current = recordsByOwnerAndId[key]
            ?.takeIf { record -> record.status == ChatAttachmentStatus.Deleted }
            ?: return@synchronized null
        val updated = current.copy(
            errorCode = null,
            updatedAtEpochMillis = maxOf(current.updatedAtEpochMillis, deletedAtEpochMillis),
            storageDeletedAtEpochMillis = deletedAtEpochMillis,
        )
        recordsByOwnerAndId[key] = updated
        updated
    }

    override suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ChatAttachmentRecord> = synchronized(lock) {
        recordsByOwnerAndId.values
            .asSequence()
            .filter { record ->
                record.deletedAtEpochMillis == null &&
                    record.createdAtEpochMillis < createdBeforeEpochMillis &&
                    record.isAbandonedUpload()
            }
            .sortedBy(ChatAttachmentRecord::createdAtEpochMillis)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    override suspend fun listPendingStorageDeletes(limit: Int): List<ChatAttachmentRecord> = synchronized(lock) {
        recordsByOwnerAndId.values
            .asSequence()
            .filter { record ->
                record.deletedAtEpochMillis != null && record.storageDeletedAtEpochMillis == null
            }
            .sortedBy { record -> record.deletedAtEpochMillis }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun String.key(value: String): String = "$this\u0000$value"
}

private fun ChatAttachmentRecord.isAbandonedUpload(): Boolean =
    status == ChatAttachmentStatus.PendingUpload ||
        (status == ChatAttachmentStatus.RetryableFailure && errorCode in UploadFailureCodes)

private val UploadFailureCodes = setOf(
    ChatAttachmentErrorCode.UploadOffline,
    ChatAttachmentErrorCode.UploadUrlExpired,
    ChatAttachmentErrorCode.UploadValidationFailed,
    ChatAttachmentErrorCode.StorageUnavailable,
)
