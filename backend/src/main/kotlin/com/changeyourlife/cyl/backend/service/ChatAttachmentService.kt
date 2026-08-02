package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.backend.domain.ChatAttachmentIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRecord
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRepository
import com.changeyourlife.cyl.backend.domain.ChatAttachmentUpdate
import com.changeyourlife.cyl.backend.domain.VoiceAssetReadHandle
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class ChatAttachmentService(
    private val repository: ChatAttachmentRepository,
    private val storage: VoiceAssetStorage,
    private val featureEnabled: Boolean,
    private val limits: ChatAttachmentLimits = ChatAttachmentLimits(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newAttachmentId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun createUploadIntent(
        ownerId: String,
        idempotencyKey: String,
        command: CreateChatAttachmentCommand,
    ): VoiceAttachmentResult<ChatAttachmentUploadOutcome> {
        if (!featureEnabled) return failure(ChatAttachmentErrorCode.FeatureDisabled, "Voice notes are disabled.")
        validateCreate(ownerId, idempotencyKey, command)?.let { return it }

        val now = nowEpochMillis()
        val normalizedMimeType = command.mimeType.normalizedMimeType()
        val normalizedName = command.originalName.trim().ifBlank { DefaultAudioFileName }
        val normalizedChecksum = command.sha256.lowercase()
        val candidateId = newAttachmentId()
        val candidate = ChatAttachmentRecord(
            attachmentId = candidateId,
            ownerId = ownerId,
            sessionClientId = command.sessionClientId.trim(),
            messageClientId = command.messageClientId?.trim()?.takeIf(String::isNotBlank),
            kind = ChatAttachmentKind.Audio,
            storageKey = storageKey(ownerId, candidateId),
            mimeType = normalizedMimeType,
            originalName = normalizedName,
            sizeBytes = command.sizeBytes,
            durationMs = command.durationMs,
            sha256 = normalizedChecksum,
            status = ChatAttachmentStatus.PendingUpload,
            idempotencyKey = idempotencyKey.trim(),
            requestFingerprint = requestFingerprint(
                command = command,
                normalizedMimeType = normalizedMimeType,
                normalizedName = normalizedName,
                normalizedChecksum = normalizedChecksum,
            ),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val claim = try {
            repository.claim(candidate)
        } catch (failure: ChatAttachmentIdempotencyConflictException) {
            return failure(ChatAttachmentErrorCode.IdempotencyConflict, failure.message.orEmpty())
        }

        var record = claim.record
        if (record.status == ChatAttachmentStatus.Deleted || record.deletedAtEpochMillis != null) {
            return failure(ChatAttachmentErrorCode.AttachmentNotFound, "Attachment was deleted.")
        }
        if (record.status in UploadedOrLaterStatuses) {
            return VoiceAttachmentResult.Success(
                ChatAttachmentUploadOutcome(
                    record = record,
                    uploadIntent = null,
                    replayed = true,
                ),
            )
        }
        if (record.status == ChatAttachmentStatus.RetryableFailure) {
            if (record.errorCode !in UploadFailureCodes) {
                return failure(ChatAttachmentErrorCode.InvalidState, "Attachment is not retrying an upload failure.")
            }
            record = when (
                val update = repository.updateLifecycle(
                    ownerId = ownerId,
                    attachmentId = record.attachmentId,
                    expectedStatuses = setOf(ChatAttachmentStatus.RetryableFailure),
                    nextStatus = ChatAttachmentStatus.PendingUpload,
                    errorCode = null,
                    updatedAtEpochMillis = now,
                )
            ) {
                is ChatAttachmentUpdate.Updated -> update.record
                is ChatAttachmentUpdate.Conflict -> update.current
                ChatAttachmentUpdate.NotFound ->
                    return failure(ChatAttachmentErrorCode.AttachmentNotFound, "Attachment disappeared during retry.")
            }
        }
        if (record.status != ChatAttachmentStatus.PendingUpload) {
            return failure(
                ChatAttachmentErrorCode.InvalidState,
                "Upload intent cannot be created from status ${record.status.wireValue}.",
            )
        }

        val expiresAt = now + limits.uploadUrlTtlMillis
        return when (
            val storageResult = storage.createUploadIntent(
                VoiceAssetUploadRequest(
                    attachmentId = record.attachmentId,
                    storageKey = record.storageKey,
                    mimeType = record.mimeType,
                    sizeBytes = record.sizeBytes,
                    durationMs = record.durationMs,
                    sha256 = record.sha256,
                    expiresAtEpochMillis = expiresAt,
                ),
            )
        ) {
            is VoiceAttachmentResult.Success -> VoiceAttachmentResult.Success(
                ChatAttachmentUploadOutcome(
                    record = record,
                    uploadIntent = storageResult.value,
                    replayed = !claim.isNew,
                ),
            )
            is VoiceAttachmentResult.Failure -> {
                repository.updateLifecycle(
                    ownerId = ownerId,
                    attachmentId = record.attachmentId,
                    expectedStatuses = setOf(ChatAttachmentStatus.PendingUpload),
                    nextStatus = ChatAttachmentStatus.RetryableFailure,
                    errorCode = storageResult.code,
                    updatedAtEpochMillis = now,
                )
                storageResult
            }
        }
    }

    suspend fun completeUpload(
        ownerId: String,
        attachmentId: String,
    ): VoiceAttachmentResult<ChatAttachmentRecord> {
        if (!featureEnabled) return failure(ChatAttachmentErrorCode.FeatureDisabled, "Voice notes are disabled.")
        val record = repository.get(ownerId, attachmentId)
            ?: return failure(ChatAttachmentErrorCode.AttachmentNotFound, "Attachment does not exist for owner.")
        if (record.status in UploadedOrLaterStatuses) return VoiceAttachmentResult.Success(record)
        if (record.status != ChatAttachmentStatus.PendingUpload &&
            !(record.status == ChatAttachmentStatus.RetryableFailure && record.errorCode in UploadFailureCodes)
        ) {
            return failure(
                ChatAttachmentErrorCode.InvalidState,
                "Upload cannot complete from status ${record.status.wireValue}.",
            )
        }

        val metadata = when (val headResult = storage.head(record.storageKey)) {
            is VoiceAttachmentResult.Success -> headResult.value
            is VoiceAttachmentResult.Failure -> {
                val mappedCode = if (headResult.code == ChatAttachmentErrorCode.AttachmentNotFound) {
                    ChatAttachmentErrorCode.UploadValidationFailed
                } else {
                    headResult.code
                }
                markUploadFailure(record, mappedCode)
                return failure(mappedCode, headResult.developerMessage)
            }
        }
        val validationProblem = when {
            metadata.sizeBytes != record.sizeBytes -> "Uploaded byte count does not match the intent."
            metadata.mimeType.normalizedMimeType() != record.mimeType ->
                "Uploaded content type does not match the intent."
            !metadata.sha256.equals(record.sha256, ignoreCase = true) ->
                "Uploaded checksum metadata does not match the intent."
            metadata.durationMs != record.durationMs ->
                "Uploaded duration metadata does not match the intent."
            else -> null
        }
        if (validationProblem != null) {
            markUploadFailure(record, ChatAttachmentErrorCode.UploadValidationFailed)
            return failure(ChatAttachmentErrorCode.UploadValidationFailed, validationProblem)
        }

        val now = nowEpochMillis()
        return when (
            val update = repository.updateLifecycle(
                ownerId = ownerId,
                attachmentId = attachmentId,
                expectedStatuses = setOf(
                    ChatAttachmentStatus.PendingUpload,
                    ChatAttachmentStatus.RetryableFailure,
                ),
                nextStatus = ChatAttachmentStatus.Uploaded,
                errorCode = null,
                updatedAtEpochMillis = now,
            )
        ) {
            is ChatAttachmentUpdate.Updated -> VoiceAttachmentResult.Success(update.record)
            is ChatAttachmentUpdate.Conflict -> if (update.current.status in UploadedOrLaterStatuses) {
                VoiceAttachmentResult.Success(update.current)
            } else {
                failure(ChatAttachmentErrorCode.InvalidState, "Attachment changed while upload was verified.")
            }
            ChatAttachmentUpdate.NotFound ->
                failure(ChatAttachmentErrorCode.AttachmentNotFound, "Attachment disappeared during verification.")
        }
    }

    suspend fun getAttachment(
        ownerId: String,
        attachmentId: String,
        includePlayback: Boolean,
    ): VoiceAttachmentResult<ChatAttachmentReadOutcome> {
        val record = repository.get(ownerId, attachmentId)
            ?: return failure(ChatAttachmentErrorCode.AttachmentNotFound, "Attachment does not exist for owner.")
        if (!includePlayback) {
            return VoiceAttachmentResult.Success(ChatAttachmentReadOutcome(record = record))
        }
        if (record.status !in PlaybackReadyStatuses) {
            return failure(
                ChatAttachmentErrorCode.InvalidState,
                "Playback is unavailable from status ${record.status.wireValue}.",
            )
        }
        val expiresAt = nowEpochMillis() + limits.playbackUrlTtlMillis
        return when (val readResult = storage.createReadHandle(record.storageKey, expiresAt)) {
            is VoiceAttachmentResult.Success -> VoiceAttachmentResult.Success(
                ChatAttachmentReadOutcome(record = record, readHandle = readResult.value),
            )
            is VoiceAttachmentResult.Failure -> readResult
        }
    }

    suspend fun deleteAttachment(
        ownerId: String,
        attachmentId: String,
    ): VoiceAttachmentResult<Unit> {
        val existing = repository.get(ownerId, attachmentId, includeDeleted = true)
            ?: return VoiceAttachmentResult.Success(Unit)
        val deletedRecord = if (existing.status == ChatAttachmentStatus.Deleted) {
            existing
        } else {
            val now = nowEpochMillis()
            when (
                val update = repository.updateLifecycle(
                    ownerId = ownerId,
                    attachmentId = attachmentId,
                    expectedStatuses = setOf(existing.status),
                    nextStatus = ChatAttachmentStatus.Deleted,
                    errorCode = null,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = now,
                )
            ) {
                is ChatAttachmentUpdate.Updated -> update.record
                is ChatAttachmentUpdate.Conflict -> update.current.takeIf {
                    it.status == ChatAttachmentStatus.Deleted
                } ?: return failure(
                    ChatAttachmentErrorCode.InvalidState,
                    "Attachment changed while it was being deleted.",
                )
                ChatAttachmentUpdate.NotFound -> return VoiceAttachmentResult.Success(Unit)
            }
        }
        if (deletedRecord.storageDeletedAtEpochMillis != null) return VoiceAttachmentResult.Success(Unit)

        return when (val deleteResult = storage.delete(deletedRecord.storageKey)) {
            is VoiceAttachmentResult.Success -> {
                repository.markStorageDeleted(ownerId, attachmentId, nowEpochMillis())
                VoiceAttachmentResult.Success(Unit)
            }
            is VoiceAttachmentResult.Failure -> {
                repository.updateLifecycle(
                    ownerId = ownerId,
                    attachmentId = attachmentId,
                    expectedStatuses = setOf(ChatAttachmentStatus.Deleted),
                    nextStatus = ChatAttachmentStatus.Deleted,
                    errorCode = deleteResult.code,
                    updatedAtEpochMillis = nowEpochMillis(),
                    deletedAtEpochMillis = deletedRecord.deletedAtEpochMillis,
                )
                deleteResult
            }
        }
    }

    suspend fun cleanupOrphans(): ChatAttachmentCleanupSummary {
        val cutoff = nowEpochMillis() - limits.orphanTtlMillis
        val orphaned = repository.listOrphanedUploads(cutoff, limits.cleanupBatchSize)
        orphaned.forEach { record -> deleteAttachment(record.ownerId, record.attachmentId) }

        val pendingDeletes = repository.listPendingStorageDeletes(limits.cleanupBatchSize)
        var deletedObjects = 0
        var failures = 0
        pendingDeletes.forEach { record ->
            when (deleteAttachment(record.ownerId, record.attachmentId)) {
                is VoiceAttachmentResult.Success -> deletedObjects += 1
                is VoiceAttachmentResult.Failure -> failures += 1
            }
        }
        return ChatAttachmentCleanupSummary(
            orphanedRecords = orphaned.size,
            pendingStorageDeletes = pendingDeletes.size,
            deletedObjects = deletedObjects,
            failures = failures,
        )
    }

    private suspend fun markUploadFailure(
        record: ChatAttachmentRecord,
        code: ChatAttachmentErrorCode,
    ) {
        repository.updateLifecycle(
            ownerId = record.ownerId,
            attachmentId = record.attachmentId,
            expectedStatuses = setOf(
                ChatAttachmentStatus.PendingUpload,
                ChatAttachmentStatus.RetryableFailure,
            ),
            nextStatus = ChatAttachmentStatus.RetryableFailure,
            errorCode = code,
            updatedAtEpochMillis = nowEpochMillis(),
        )
    }

    private fun validateCreate(
        ownerId: String,
        idempotencyKey: String,
        command: CreateChatAttachmentCommand,
    ): VoiceAttachmentResult.Failure? {
        val issue = when {
            ownerId.isBlank() -> "Authenticated owner is required."
            idempotencyKey.isBlank() || idempotencyKey.length > MaxIdempotencyKeyChars ||
                idempotencyKey.hasControlCharacters() -> "A valid Idempotency-Key is required."
            command.kind != ChatAttachmentKind.Audio -> "Only audio attachments are supported by this endpoint."
            command.mimeType.normalizedMimeType() != SupportedAudioMimeType ->
                "Voice-note mimeType must be $SupportedAudioMimeType."
            command.originalName.length > MaxOriginalNameChars || command.originalName.hasControlCharacters() ->
                "Voice-note originalName is invalid."
            command.sizeBytes <= 0L || command.sizeBytes > limits.maxBytes ->
                "Voice-note size is outside the configured limit."
            command.durationMs < limits.minDurationMs || command.durationMs > limits.maxDurationMs ->
                "Voice-note duration is outside the configured limit."
            !command.sha256.matches(Sha256Pattern) -> "Voice-note SHA-256 is invalid."
            command.sessionClientId.isBlank() || command.sessionClientId.length > MaxClientIdChars ||
                command.sessionClientId.hasControlCharacters() -> "sessionClientId is invalid."
            command.messageClientId != null && (
                command.messageClientId.length > MaxClientIdChars ||
                    command.messageClientId.hasControlCharacters()
                ) -> "messageClientId is invalid."
            else -> return null
        }
        return failure(ChatAttachmentErrorCode.InvalidRequest, issue)
    }

    private fun requestFingerprint(
        command: CreateChatAttachmentCommand,
        normalizedMimeType: String,
        normalizedName: String,
        normalizedChecksum: String,
    ): String = sha256Hex(
        listOf(
            command.kind.wireValue,
            normalizedMimeType,
            normalizedName,
            command.sizeBytes.toString(),
            command.durationMs.toString(),
            normalizedChecksum,
            command.sessionClientId.trim(),
            command.messageClientId?.trim().orEmpty(),
        ).joinToString(separator = "\u001f"),
    )

    private fun storageKey(ownerId: String, attachmentId: String): String =
        "voice/${sha256Hex(ownerId).take(24)}/$attachmentId.m4a"
}

data class ChatAttachmentLimits(
    val minDurationMs: Long = 300L,
    val maxDurationMs: Long = 300_000L,
    val maxBytes: Long = 10_485_760L,
    val uploadUrlTtlMillis: Long = 300_000L,
    val playbackUrlTtlMillis: Long = 300_000L,
    val orphanTtlMillis: Long = 86_400_000L,
    val cleanupBatchSize: Int = 100,
)

data class CreateChatAttachmentCommand(
    val kind: ChatAttachmentKind,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val sessionClientId: String,
    val messageClientId: String?,
)

data class ChatAttachmentUploadOutcome(
    val record: ChatAttachmentRecord,
    val uploadIntent: VoiceAssetUploadIntent?,
    val replayed: Boolean,
)

data class ChatAttachmentReadOutcome(
    val record: ChatAttachmentRecord,
    val readHandle: VoiceAssetReadHandle? = null,
)

data class ChatAttachmentCleanupSummary(
    val orphanedRecords: Int,
    val pendingStorageDeletes: Int,
    val deletedObjects: Int,
    val failures: Int,
)

private fun failure(
    code: ChatAttachmentErrorCode,
    developerMessage: String,
): VoiceAttachmentResult.Failure = VoiceAttachmentResult.Failure(code, developerMessage)

private fun String.normalizedMimeType(): String = substringBefore(';').trim().lowercase()

private fun String.hasControlCharacters(): Boolean = any(Char::isISOControl)

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")

private val UploadFailureCodes = setOf(
    ChatAttachmentErrorCode.UploadOffline,
    ChatAttachmentErrorCode.UploadUrlExpired,
    ChatAttachmentErrorCode.UploadValidationFailed,
    ChatAttachmentErrorCode.StorageUnavailable,
)

private val UploadedOrLaterStatuses = setOf(
    ChatAttachmentStatus.Uploaded,
    ChatAttachmentStatus.Transcribing,
    ChatAttachmentStatus.Ready,
    ChatAttachmentStatus.AiQueued,
    ChatAttachmentStatus.AiProcessing,
    ChatAttachmentStatus.Completed,
)

private val PlaybackReadyStatuses = UploadedOrLaterStatuses

private const val SupportedAudioMimeType = "audio/mp4"
private const val DefaultAudioFileName = "voice-note.m4a"
private const val MaxOriginalNameChars = 255
private const val MaxClientIdChars = 128
private const val MaxIdempotencyKeyChars = 200
