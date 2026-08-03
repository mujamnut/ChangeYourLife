package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.domain.ContentAssetClaim
import com.changeyourlife.cyl.backend.domain.ContentAssetErrorCode
import com.changeyourlife.cyl.backend.domain.ContentAssetIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ContentAssetKind
import com.changeyourlife.cyl.backend.domain.ContentAssetRecord
import com.changeyourlife.cyl.backend.domain.ContentAssetRepository
import com.changeyourlife.cyl.backend.domain.ContentAssetResult
import com.changeyourlife.cyl.backend.domain.ContentAssetStatus
import com.changeyourlife.cyl.backend.domain.ContentAssetUpdate
import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadHandle
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadRequest
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorage
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageError
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageResult
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ContentAssetService(
    private val repository: ContentAssetRepository,
    private val contentRepository: ContentRepository,
    private val storage: PrivateAssetStorage,
    private val featureEnabled: Boolean,
    private val limits: ContentAssetLimits = ContentAssetLimits(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun createUploadIntent(
        ownerId: String,
        idempotencyKey: String,
        command: CreateContentAssetCommand,
    ): ContentAssetResult<ContentAssetUploadOutcome> {
        if (!featureEnabled) return failure(ContentAssetErrorCode.FeatureDisabled, "Content assets are disabled.")
        val policy = validateCreate(ownerId, idempotencyKey, command)?.let { return it }
            ?: checkNotNull(resolvePolicy(command.kind, command.mimeType))

        val ownsWorkspace = contentRepository.listWorkspaces(ownerId)
            .any { workspace -> workspace.id == command.workspaceId.trim() }
        if (!ownsWorkspace) {
            return failure(ContentAssetErrorCode.Forbidden, "Workspace is not owned by the authenticated user.")
        }
        val normalizedPageId = command.pageId?.trim()?.takeIf(String::isNotBlank)
        if (normalizedPageId != null) {
            val page = contentRepository.getPage(ownerId, normalizedPageId)
            if (page == null || page.workspaceId != command.workspaceId.trim()) {
                return failure(ContentAssetErrorCode.Forbidden, "Page does not belong to the selected workspace.")
            }
        }

        val now = nowEpochMillis()
        val normalizedMimeType = command.mimeType.normalizedMimeType()
        val normalizedName = command.originalName.trim().ifBlank { policy.defaultFileName }
        val normalizedChecksum = command.sha256.lowercase()
        val candidateId = command.assetId.trim()
        val candidate = ContentAssetRecord(
            assetId = candidateId,
            ownerId = ownerId,
            workspaceId = command.workspaceId.trim(),
            pageId = normalizedPageId,
            kind = checkNotNull(command.kind),
            storageKey = storageKey(ownerId, candidateId),
            mimeType = normalizedMimeType,
            originalName = normalizedName,
            sizeBytes = command.sizeBytes,
            sha256 = normalizedChecksum,
            status = ContentAssetStatus.UploadQueued,
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
        } catch (conflict: ContentAssetIdempotencyConflictException) {
            return failure(ContentAssetErrorCode.IdempotencyConflict, conflict.message.orEmpty())
        }
        var record = claim.record
        if (record.status == ContentAssetStatus.Deleted || record.deletedAtEpochMillis != null) {
            return failure(ContentAssetErrorCode.NotFound, "Content asset was deleted.")
        }
        if (record.status == ContentAssetStatus.RemoteReady) {
            return ContentAssetResult.Success(
                ContentAssetUploadOutcome(record = record, uploadIntent = null, replayed = true),
            )
        }
        if (record.status == ContentAssetStatus.RetryableFailure) {
            record = when (
                val update = repository.updateLifecycle(
                    ownerId = ownerId,
                    assetId = record.assetId,
                    expectedStatuses = setOf(ContentAssetStatus.RetryableFailure),
                    nextStatus = ContentAssetStatus.UploadQueued,
                    errorCode = null,
                    updatedAtEpochMillis = now,
                )
            ) {
                is ContentAssetUpdate.Updated -> update.record
                is ContentAssetUpdate.Conflict -> update.current
                ContentAssetUpdate.NotFound ->
                    return failure(ContentAssetErrorCode.NotFound, "Content asset disappeared during retry.")
            }
        }
        if (record.status != ContentAssetStatus.UploadQueued) {
            return failure(
                ContentAssetErrorCode.InvalidState,
                "Upload intent is unavailable from status ${record.status.wireValue}.",
            )
        }

        val expiresAt = now + limits.uploadUrlTtlMillis
        return when (
            val storageResult = storage.createUploadIntent(
                PrivateAssetUploadRequest(
                    assetId = record.assetId,
                    storageKey = record.storageKey,
                    mimeType = record.mimeType,
                    sizeBytes = record.sizeBytes,
                    sha256 = record.sha256,
                    expiresAtEpochMillis = expiresAt,
                    metadata = mapOf(MetadataAssetId to record.assetId),
                ),
            )
        ) {
            is PrivateAssetStorageResult.Success -> ContentAssetResult.Success(
                ContentAssetUploadOutcome(
                    record = record,
                    uploadIntent = storageResult.value,
                    replayed = !claim.isNew,
                ),
            )
            is PrivateAssetStorageResult.Failure -> {
                repository.updateLifecycle(
                    ownerId = ownerId,
                    assetId = record.assetId,
                    expectedStatuses = setOf(ContentAssetStatus.UploadQueued),
                    nextStatus = ContentAssetStatus.RetryableFailure,
                    errorCode = storageResult.toContentError(),
                    updatedAtEpochMillis = now,
                )
                failure(storageResult.toContentError(), storageResult.developerMessage)
            }
        }
    }

    suspend fun completeUpload(
        ownerId: String,
        assetId: String,
    ): ContentAssetResult<ContentAssetRecord> {
        if (!featureEnabled) return failure(ContentAssetErrorCode.FeatureDisabled, "Content assets are disabled.")
        val record = repository.get(ownerId, assetId)
            ?: return failure(ContentAssetErrorCode.NotFound, "Content asset does not exist for owner.")
        if (record.status == ContentAssetStatus.RemoteReady) return ContentAssetResult.Success(record)
        if (record.status !in setOf(ContentAssetStatus.UploadQueued, ContentAssetStatus.RetryableFailure)) {
            return failure(
                ContentAssetErrorCode.InvalidState,
                "Upload cannot complete from status ${record.status.wireValue}.",
            )
        }

        val metadata = when (val head = storage.head(record.storageKey)) {
            is PrivateAssetStorageResult.Success -> head.value
            is PrivateAssetStorageResult.Failure -> {
                val code = if (head.error == PrivateAssetStorageError.NotFound) {
                    ContentAssetErrorCode.UploadValidationFailed
                } else {
                    head.toContentError()
                }
                markUploadFailure(record, code)
                return failure(code, head.developerMessage)
            }
        }
        val metadataProblem = when {
            metadata.sizeBytes != record.sizeBytes -> "Uploaded byte count does not match the intent."
            metadata.mimeType.normalizedMimeType() != record.mimeType ->
                "Uploaded content type does not match the intent."
            else -> null
        }
        if (metadataProblem != null) {
            markUploadFailure(record, ContentAssetErrorCode.UploadValidationFailed)
            return failure(ContentAssetErrorCode.UploadValidationFailed, metadataProblem)
        }

        val digest = when (
            val digestResult = storage.calculateDigest(record.storageKey, record.sizeBytes)
        ) {
            is PrivateAssetStorageResult.Success -> digestResult.value
            is PrivateAssetStorageResult.Failure -> {
                val code = if (digestResult.error == PrivateAssetStorageError.InvalidObject) {
                    ContentAssetErrorCode.UploadValidationFailed
                } else {
                    digestResult.toContentError()
                }
                markUploadFailure(record, code)
                return failure(code, digestResult.developerMessage)
            }
        }
        if (digest.sizeBytes != record.sizeBytes || !digest.sha256.equals(record.sha256, ignoreCase = true)) {
            markUploadFailure(record, ContentAssetErrorCode.UploadValidationFailed)
            return failure(
                ContentAssetErrorCode.UploadValidationFailed,
                "Uploaded content digest does not match the intent.",
            )
        }

        return when (
            val update = repository.updateLifecycle(
                ownerId = ownerId,
                assetId = assetId,
                expectedStatuses = setOf(
                    ContentAssetStatus.UploadQueued,
                    ContentAssetStatus.RetryableFailure,
                ),
                nextStatus = ContentAssetStatus.RemoteReady,
                errorCode = null,
                updatedAtEpochMillis = nowEpochMillis(),
            )
        ) {
            is ContentAssetUpdate.Updated -> ContentAssetResult.Success(update.record)
            is ContentAssetUpdate.Conflict -> if (update.current.status == ContentAssetStatus.RemoteReady) {
                ContentAssetResult.Success(update.current)
            } else {
                failure(ContentAssetErrorCode.InvalidState, "Content asset changed during verification.")
            }
            ContentAssetUpdate.NotFound -> failure(ContentAssetErrorCode.NotFound, "Content asset disappeared.")
        }
    }

    suspend fun getAsset(
        ownerId: String,
        assetId: String,
        includeDownload: Boolean,
    ): ContentAssetResult<ContentAssetReadOutcome> {
        val record = repository.get(ownerId, assetId)
            ?: return failure(ContentAssetErrorCode.NotFound, "Content asset does not exist for owner.")
        if (!includeDownload) return ContentAssetResult.Success(ContentAssetReadOutcome(record))
        if (record.status != ContentAssetStatus.RemoteReady) {
            return failure(ContentAssetErrorCode.InvalidState, "Content asset is not ready for download.")
        }
        val expiresAt = nowEpochMillis() + limits.downloadUrlTtlMillis
        return when (
            val read = storage.createReadHandle(
                PrivateAssetReadRequest(
                    storageKey = record.storageKey,
                    expiresAtEpochMillis = expiresAt,
                    downloadFileName = record.originalName,
                    forceDownload = true,
                ),
            )
        ) {
            is PrivateAssetStorageResult.Success -> ContentAssetResult.Success(
                ContentAssetReadOutcome(record = record, readHandle = read.value),
            )
            is PrivateAssetStorageResult.Failure -> failure(read.toContentError(), read.developerMessage)
        }
    }

    suspend fun deleteAsset(ownerId: String, assetId: String): ContentAssetResult<Unit> {
        val existing = repository.get(ownerId, assetId, includeDeleted = true)
            ?: return ContentAssetResult.Success(Unit)
        val deleted = if (existing.status == ContentAssetStatus.Deleted) {
            existing
        } else {
            val now = nowEpochMillis()
            when (
                val update = repository.updateLifecycle(
                    ownerId = ownerId,
                    assetId = assetId,
                    expectedStatuses = setOf(existing.status),
                    nextStatus = ContentAssetStatus.Deleted,
                    errorCode = null,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = now,
                )
            ) {
                is ContentAssetUpdate.Updated -> update.record
                is ContentAssetUpdate.Conflict -> update.current.takeIf {
                    it.status == ContentAssetStatus.Deleted
                } ?: return failure(ContentAssetErrorCode.InvalidState, "Content asset changed during deletion.")
                ContentAssetUpdate.NotFound -> return ContentAssetResult.Success(Unit)
            }
        }
        if (deleted.storageDeletedAtEpochMillis != null) return ContentAssetResult.Success(Unit)
        return when (val storageDelete = storage.delete(deleted.storageKey)) {
            is PrivateAssetStorageResult.Success -> {
                repository.markStorageDeleted(ownerId, assetId, nowEpochMillis())
                ContentAssetResult.Success(Unit)
            }
            is PrivateAssetStorageResult.Failure -> {
                repository.updateLifecycle(
                    ownerId = ownerId,
                    assetId = assetId,
                    expectedStatuses = setOf(ContentAssetStatus.Deleted),
                    nextStatus = ContentAssetStatus.Deleted,
                    errorCode = storageDelete.toContentError(),
                    updatedAtEpochMillis = nowEpochMillis(),
                    deletedAtEpochMillis = deleted.deletedAtEpochMillis,
                )
                failure(storageDelete.toContentError(), storageDelete.developerMessage)
            }
        }
    }

    suspend fun cleanupOrphans(): ContentAssetCleanupSummary {
        val cutoff = nowEpochMillis() - limits.orphanTtlMillis
        val orphaned = repository.listOrphanedUploads(cutoff, limits.cleanupBatchSize)
        orphaned.forEach { record -> deleteAsset(record.ownerId, record.assetId) }
        val pending = repository.listPendingStorageDeletes(limits.cleanupBatchSize)
        var deletedObjects = 0
        var failures = 0
        pending.forEach { record ->
            when (deleteAsset(record.ownerId, record.assetId)) {
                is ContentAssetResult.Success -> deletedObjects += 1
                is ContentAssetResult.Failure -> failures += 1
            }
        }
        return ContentAssetCleanupSummary(orphaned.size, pending.size, deletedObjects, failures)
    }

    private suspend fun markUploadFailure(record: ContentAssetRecord, code: ContentAssetErrorCode) {
        repository.updateLifecycle(
            ownerId = record.ownerId,
            assetId = record.assetId,
            expectedStatuses = setOf(
                ContentAssetStatus.UploadQueued,
                ContentAssetStatus.RetryableFailure,
            ),
            nextStatus = ContentAssetStatus.RetryableFailure,
            errorCode = code,
            updatedAtEpochMillis = nowEpochMillis(),
        )
    }

    private fun validateCreate(
        ownerId: String,
        idempotencyKey: String,
        command: CreateContentAssetCommand,
    ): ContentAssetResult.Failure? {
        val policy = resolvePolicy(command.kind, command.mimeType)
        val issue = when {
            ownerId.isBlank() -> "Authenticated owner is required."
            !command.assetId.matches(SafeAssetIdPattern) -> "assetId is invalid."
            idempotencyKey.isBlank() || idempotencyKey.length > MaxIdempotencyKeyChars ||
                idempotencyKey.hasControlCharacters() -> "A valid Idempotency-Key is required."
            command.workspaceId.isBlank() || command.workspaceId.length > MaxClientIdChars ||
                command.workspaceId.hasControlCharacters() -> "workspaceId is invalid."
            command.pageId != null && (
                command.pageId.length > MaxClientIdChars || command.pageId.hasControlCharacters()
                ) -> "pageId is invalid."
            command.kind == null || policy == null -> "Asset kind or MIME type is unsupported."
            command.originalName.length > MaxOriginalNameChars || command.originalName.hasControlCharacters() ->
                "Asset originalName is invalid."
            command.sizeBytes <= 0L || command.sizeBytes > policy.maxBytes ->
                "Asset size is outside the configured limit."
            !command.sha256.matches(Sha256Pattern) -> "Asset SHA-256 is invalid."
            else -> return null
        }
        return failure(ContentAssetErrorCode.InvalidRequest, issue)
    }

    private fun resolvePolicy(kind: ContentAssetKind?, rawMimeType: String): AssetTypePolicy? {
        val mimeType = rawMimeType.normalizedMimeType()
        return when {
            kind == ContentAssetKind.Image && mimeType in SupportedImageMimeTypes ->
                AssetTypePolicy(limits.maxImageBytes, "image")
            kind == ContentAssetKind.Pdf && mimeType == PdfMimeType ->
                AssetTypePolicy(limits.maxPdfBytes, "document.pdf")
            kind == ContentAssetKind.Text && mimeType in SupportedTextMimeTypes ->
                AssetTypePolicy(limits.maxTextBytes, "text.txt")
            else -> null
        }
    }

    private fun requestFingerprint(
        command: CreateContentAssetCommand,
        normalizedMimeType: String,
        normalizedName: String,
        normalizedChecksum: String,
    ): String = sha256Hex(
        listOf(
            command.assetId.trim(),
            command.workspaceId.trim(),
            command.pageId?.trim().orEmpty(),
            command.kind?.wireValue.orEmpty(),
            normalizedMimeType,
            normalizedName,
            command.sizeBytes.toString(),
            normalizedChecksum,
        ).joinToString("\u001f"),
    )

    private fun storageKey(ownerId: String, assetId: String): String =
        "assets/${sha256Hex(ownerId).take(24)}/$assetId"
}

data class ContentAssetLimits(
    val maxImageBytes: Long = 15L * 1024L * 1024L,
    val maxPdfBytes: Long = 50L * 1024L * 1024L,
    val maxTextBytes: Long = 1L * 1024L * 1024L,
    val uploadUrlTtlMillis: Long = 300_000L,
    val downloadUrlTtlMillis: Long = 300_000L,
    val orphanTtlMillis: Long = 86_400_000L,
    val cleanupBatchSize: Int = 100,
)

data class CreateContentAssetCommand(
    val assetId: String,
    val workspaceId: String,
    val pageId: String?,
    val kind: ContentAssetKind?,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class ContentAssetUploadOutcome(
    val record: ContentAssetRecord,
    val uploadIntent: PrivateAssetUploadIntent?,
    val replayed: Boolean,
)

data class ContentAssetReadOutcome(
    val record: ContentAssetRecord,
    val readHandle: PrivateAssetReadHandle? = null,
)

data class ContentAssetCleanupSummary(
    val orphanedRecords: Int,
    val pendingStorageDeletes: Int,
    val deletedObjects: Int,
    val failures: Int,
)

private data class AssetTypePolicy(val maxBytes: Long, val defaultFileName: String)

private fun failure(code: ContentAssetErrorCode, message: String): ContentAssetResult.Failure =
    ContentAssetResult.Failure(code, message)

private fun PrivateAssetStorageResult.Failure.toContentError(): ContentAssetErrorCode = when (error) {
    PrivateAssetStorageError.NotFound -> ContentAssetErrorCode.NotFound
    PrivateAssetStorageError.InvalidObject -> ContentAssetErrorCode.UploadValidationFailed
    PrivateAssetStorageError.Unavailable -> ContentAssetErrorCode.StorageUnavailable
}

private fun String.normalizedMimeType(): String = substringBefore(';').trim().lowercase()
private fun String.hasControlCharacters(): Boolean = any(Char::isISOControl)
private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")
private val SafeAssetIdPattern = Regex("[A-Za-z0-9_-]{1,80}")
private val SupportedImageMimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
private val SupportedTextMimeTypes = setOf("text/plain", "text/html", "text/csv", "application/json")
private const val PdfMimeType = "application/pdf"
private const val MetadataAssetId = "asset-id"
private const val MaxOriginalNameChars = 255
private const val MaxClientIdChars = 128
private const val MaxIdempotencyKeyChars = 200
