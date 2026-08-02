package com.changeyourlife.cyl.backend.domain

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus

interface ChatAttachmentRepository {
    suspend fun claim(record: ChatAttachmentRecord): ChatAttachmentClaim

    suspend fun get(
        ownerId: String,
        attachmentId: String,
        includeDeleted: Boolean = false,
    ): ChatAttachmentRecord?

    suspend fun updateLifecycle(
        ownerId: String,
        attachmentId: String,
        expectedStatuses: Set<ChatAttachmentStatus>,
        nextStatus: ChatAttachmentStatus,
        errorCode: ChatAttachmentErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long? = null,
    ): ChatAttachmentUpdate

    suspend fun markStorageDeleted(
        ownerId: String,
        attachmentId: String,
        deletedAtEpochMillis: Long,
    ): ChatAttachmentRecord?

    suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ChatAttachmentRecord>

    suspend fun listPendingStorageDeletes(limit: Int): List<ChatAttachmentRecord>
}

data class ChatAttachmentRecord(
    val attachmentId: String,
    val ownerId: String,
    val sessionClientId: String,
    val messageClientId: String?,
    val kind: ChatAttachmentKind,
    val storageKey: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val status: ChatAttachmentStatus,
    val transcript: String? = null,
    val transcriptLanguage: String? = null,
    val transcriptionProvider: String? = null,
    val transcriptionModel: String? = null,
    val transcriptionVersion: String? = null,
    val errorCode: ChatAttachmentErrorCode? = null,
    val idempotencyKey: String,
    val requestFingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val storageDeletedAtEpochMillis: Long? = null,
)

data class ChatAttachmentClaim(
    val record: ChatAttachmentRecord,
    val isNew: Boolean,
)

sealed interface ChatAttachmentUpdate {
    data class Updated(val record: ChatAttachmentRecord) : ChatAttachmentUpdate

    data object NotFound : ChatAttachmentUpdate

    data class Conflict(val current: ChatAttachmentRecord) : ChatAttachmentUpdate
}

class ChatAttachmentIdempotencyConflictException :
    IllegalStateException("The attachment idempotency key was already used with a different request.")
