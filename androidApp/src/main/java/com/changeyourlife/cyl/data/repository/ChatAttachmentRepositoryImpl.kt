package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.data.local.dao.ChatAttachmentDao
import com.changeyourlife.cyl.data.local.entity.ChatAttachmentEntity
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatAttachmentRepositoryImpl @Inject constructor(
    private val dao: ChatAttachmentDao,
    private val json: Json,
) : ChatAttachmentRepository {
    override fun observeBySession(sessionId: String): Flow<List<ChatAttachment>> =
        dao.observeBySession(sessionId).map { entities -> entities.map(::toDomain) }

    override fun observeByMessage(messageId: String): Flow<List<ChatAttachment>> =
        dao.observeByMessage(messageId).map { entities -> entities.map(::toDomain) }

    override suspend fun getById(attachmentId: String): ChatAttachment? =
        dao.getById(attachmentId)?.let(::toDomain)

    override suspend fun getPendingUploads(): List<ChatAttachment> =
        dao.getPendingUploads(PendingUploadStatuses.map(ChatAttachmentStatus::wireValue))
            .map(::toDomain)
            .filter { attachment ->
                attachment.status != ChatAttachmentStatus.RetryableFailure ||
                    attachment.errorCode in RetryableUploadErrorCodes
            }

    override suspend fun upsert(attachment: ChatAttachment) {
        dao.upsert(attachment.toEntity())
    }

    override suspend fun linkToMessage(
        attachmentId: String,
        messageId: String,
        sessionId: String,
        updatedAt: Long,
    ) {
        dao.linkToMessage(attachmentId, messageId, sessionId, updatedAt)
    }

    override suspend fun updateStatus(
        attachmentId: String,
        status: ChatAttachmentStatus,
        progressPercent: Int,
        errorCode: String?,
        updatedAt: Long,
    ) {
        dao.updateStatus(
            attachmentId = attachmentId,
            status = status.wireValue,
            progressPercent = progressPercent.coerceIn(0, 100),
            errorCode = errorCode,
            updatedAt = updatedAt,
        )
    }

    override suspend fun transitionUploadState(
        attachmentId: String,
        expectedStatuses: Set<ChatAttachmentStatus>,
        nextStatus: ChatAttachmentStatus,
        progressPercent: Int,
        errorCode: String?,
        sha256: String?,
        remoteAssetId: String?,
        updatedAt: Long,
    ): Boolean {
        if (expectedStatuses.isEmpty()) return false
        return dao.transitionUploadState(
            attachmentId = attachmentId,
            expectedStatuses = expectedStatuses.map(ChatAttachmentStatus::wireValue),
            nextStatus = nextStatus.wireValue,
            progressPercent = progressPercent.coerceIn(0, 100),
            errorCode = errorCode,
            sha256 = sha256,
            remoteAssetId = remoteAssetId,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun deleteLocal(attachmentId: String) {
        dao.deleteById(attachmentId)
    }

    private fun toDomain(entity: ChatAttachmentEntity): ChatAttachment = ChatAttachment(
        id = entity.id,
        messageId = entity.messageId,
        sessionId = entity.sessionId,
        kind = ChatAttachmentKind.fromWireValue(entity.kind),
        name = entity.name,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        durationMs = entity.durationMs,
        sha256 = entity.sha256,
        localPath = entity.localPath,
        remoteAssetId = entity.remoteAssetId,
        waveform = runCatching { json.decodeFromString<List<Int>>(entity.waveformJson) }
            .getOrDefault(emptyList()),
        transcript = entity.transcript,
        language = entity.language,
        status = ChatAttachmentStatus.fromWireValue(entity.status),
        progressPercent = entity.progressPercent.coerceIn(0, 100),
        aiJobId = entity.aiJobId,
        errorCode = entity.errorCode,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun ChatAttachment.toEntity(): ChatAttachmentEntity = ChatAttachmentEntity(
        id = id,
        messageId = messageId,
        sessionId = sessionId,
        kind = kind.wireValue,
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        sha256 = sha256,
        localPath = localPath,
        remoteAssetId = remoteAssetId,
        waveformJson = json.encodeToString(waveform.map { value -> value.coerceIn(0, 100) }),
        transcript = transcript,
        language = language,
        status = status.wireValue,
        progressPercent = progressPercent.coerceIn(0, 100),
        aiJobId = aiJobId,
        errorCode = errorCode,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        val PendingUploadStatuses = listOf(
            ChatAttachmentStatus.LocalReady,
            ChatAttachmentStatus.UploadQueued,
            ChatAttachmentStatus.Uploading,
            ChatAttachmentStatus.PendingUpload,
            ChatAttachmentStatus.RetryableFailure,
        )
        val RetryableUploadErrorCodes = setOf(
            "upload_offline",
            "upload_url_expired",
            "upload_validation_failed",
            "storage_unavailable",
            "attachment_forbidden",
            "invalid_state",
        )
    }
}
