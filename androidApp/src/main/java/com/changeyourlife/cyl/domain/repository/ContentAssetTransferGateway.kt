package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetStatus

interface ContentAssetTransferGateway {
    suspend fun upload(
        asset: ContentAsset,
        onRemoteAccepted: (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): ContentAssetRemoteUploadResult

    suspend fun getDownloadHandle(remoteAssetId: String): ContentAssetRemoteReadResult

    suspend fun delete(remoteAssetId: String): ContentAssetRemoteDeleteResult
}

interface ContentAssetUploadScheduler {
    fun enqueue(assetId: String)

    suspend fun resumePendingUploads()
}

enum class ContentAssetTransferError(
    val wireValue: String,
    val retryableByDefault: Boolean,
) {
    FEATURE_DISABLED("feature_disabled", false),
    INVALID_REQUEST("invalid_request", false),
    FORBIDDEN("forbidden", false),
    NOT_FOUND("not_found", false),
    IDEMPOTENCY_CONFLICT("idempotency_conflict", false),
    INVALID_STATE("invalid_state", false),
    UPLOAD_VALIDATION_FAILED("upload_validation_failed", false),
    STORAGE_UNAVAILABLE("storage_unavailable", true),
    AUTH_REQUIRED("auth_required", true),
    UNKNOWN("unknown", true),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetTransferError =
            entries.firstOrNull { error -> error.wireValue.equals(value.trim(), ignoreCase = true) }
                ?: UNKNOWN
    }
}

sealed interface ContentAssetRemoteUploadResult {
    data class Success(
        val remoteAssetId: String,
        val status: ContentAssetStatus,
    ) : ContentAssetRemoteUploadResult

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : ContentAssetRemoteUploadResult
}

sealed interface ContentAssetRemoteReadResult {
    data class Success(
        val asset: ContentAssetRemoteDescriptor,
        val downloadUrl: String,
        val expiresAtEpochMillis: Long,
    ) : ContentAssetRemoteReadResult

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : ContentAssetRemoteReadResult
}

data class ContentAssetRemoteDescriptor(
    val remoteAssetId: String,
    val workspaceId: String,
    val ownerPageId: String?,
    val kind: ContentAssetKind,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

sealed interface ContentAssetRemoteDeleteResult {
    data object Success : ContentAssetRemoteDeleteResult

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : ContentAssetRemoteDeleteResult
}
