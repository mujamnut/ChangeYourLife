package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.domain.model.ChatAttachment

interface ChatAttachmentUploadGateway {
    suspend fun calculateSha256(attachment: ChatAttachment): ChatAttachmentChecksumResult

    suspend fun upload(
        attachment: ChatAttachment,
        onRemoteAccepted: (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): ChatAttachmentRemoteUploadResult
}

sealed interface ChatAttachmentChecksumResult {
    data class Success(val sha256: String) : ChatAttachmentChecksumResult

    data class Failure(
        val code: ChatAttachmentErrorCode,
        val retryable: Boolean,
    ) : ChatAttachmentChecksumResult
}

sealed interface ChatAttachmentRemoteUploadResult {
    data class Success(
        val remoteAssetId: String,
        val status: ChatAttachmentStatus,
    ) : ChatAttachmentRemoteUploadResult

    data class Failure(
        val code: ChatAttachmentErrorCode,
        val retryable: Boolean,
    ) : ChatAttachmentRemoteUploadResult
}
