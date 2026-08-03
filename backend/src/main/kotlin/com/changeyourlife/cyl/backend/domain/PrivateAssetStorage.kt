package com.changeyourlife.cyl.backend.domain

sealed interface PrivateAssetStorageResult<out T> {
    data class Success<T>(val value: T) : PrivateAssetStorageResult<T>

    data class Failure(
        val error: PrivateAssetStorageError,
        val developerMessage: String = "",
    ) : PrivateAssetStorageResult<Nothing>
}

enum class PrivateAssetStorageError {
    NotFound,
    InvalidObject,
    Unavailable,
}

data class PrivateAssetUploadRequest(
    val assetId: String,
    val storageKey: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val expiresAtEpochMillis: Long,
    val metadata: Map<String, String> = emptyMap(),
)

data class PrivateAssetUploadIntent(
    val assetId: String,
    val uploadUrl: String,
    val requiredHeaders: Map<String, String>,
    val expiresAtEpochMillis: Long,
)

data class PrivateAssetMetadata(
    val storageKey: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val metadata: Map<String, String>,
)

data class PrivateAssetDigest(
    val sizeBytes: Long,
    val sha256: String,
)

data class PrivateAssetReadRequest(
    val storageKey: String,
    val expiresAtEpochMillis: Long,
    val downloadFileName: String = "",
    val forceDownload: Boolean = false,
)

data class PrivateAssetReadHandle(
    val readUrl: String,
    val expiresAtEpochMillis: Long,
)

interface PrivateAssetStorage {
    suspend fun createUploadIntent(
        request: PrivateAssetUploadRequest,
    ): PrivateAssetStorageResult<PrivateAssetUploadIntent>

    suspend fun head(storageKey: String): PrivateAssetStorageResult<PrivateAssetMetadata>

    suspend fun calculateDigest(
        storageKey: String,
        maxBytes: Long,
    ): PrivateAssetStorageResult<PrivateAssetDigest>

    suspend fun createReadHandle(
        request: PrivateAssetReadRequest,
    ): PrivateAssetStorageResult<PrivateAssetReadHandle>

    suspend fun delete(storageKey: String): PrivateAssetStorageResult<Unit>
}
