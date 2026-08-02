package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.model.ChatAttachment
import kotlinx.coroutines.flow.Flow

interface ChatAttachmentRepository {
    fun observeBySession(sessionId: String): Flow<List<ChatAttachment>>

    fun observeByMessage(messageId: String): Flow<List<ChatAttachment>>

    suspend fun getById(attachmentId: String): ChatAttachment?

    suspend fun getPendingUploads(): List<ChatAttachment>

    suspend fun upsert(attachment: ChatAttachment)

    suspend fun linkToMessage(
        attachmentId: String,
        messageId: String,
        sessionId: String,
        updatedAt: Long,
    )

    suspend fun updateStatus(
        attachmentId: String,
        status: ChatAttachmentStatus,
        progressPercent: Int,
        errorCode: String?,
        updatedAt: Long,
    )

    suspend fun transitionUploadState(
        attachmentId: String,
        expectedStatuses: Set<ChatAttachmentStatus>,
        nextStatus: ChatAttachmentStatus,
        progressPercent: Int,
        errorCode: String?,
        sha256: String? = null,
        remoteAssetId: String? = null,
        updatedAt: Long,
    ): Boolean

    suspend fun deleteLocal(attachmentId: String)
}
