package com.changeyourlife.cyl.backend.model.asset

import kotlinx.serialization.Serializable

@Serializable
data class CreateContentAssetUploadIntentRequest(
    val assetId: String,
    val workspaceId: String,
    val pageId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class ContentAssetUploadIntentResponse(
    val assetId: String,
    val status: String,
    val uploadUrl: String? = null,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMillis: Long? = null,
    val replayed: Boolean = false,
)

@Serializable
data class ContentAssetResponse(
    val assetId: String,
    val workspaceId: String,
    val pageId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
    val status: String,
    val errorCode: String? = null,
    val downloadUrl: String? = null,
    val downloadUrlExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ContentAssetErrorResponse(val error: ContentAssetApiError)

@Serializable
data class ContentAssetApiError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
