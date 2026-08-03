package com.changeyourlife.cyl.backend.domain

enum class ContentAssetKind(val wireValue: String) {
    Image("image"),
    Pdf("pdf"),
    Text("text"),
    File("file"),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetKind? =
            entries.firstOrNull { kind -> kind.wireValue.equals(value.trim(), ignoreCase = true) }
    }
}

enum class ContentAssetStatus(val wireValue: String) {
    UploadQueued("upload_queued"),
    RemoteReady("remote_ready"),
    RetryableFailure("retryable_failure"),
    PermanentFailure("permanent_failure"),
    Deleted("deleted"),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetStatus =
            entries.firstOrNull { status -> status.wireValue == value } ?: PermanentFailure
    }
}

enum class ContentAssetErrorCode(
    val wireValue: String,
    val retryable: Boolean,
) {
    FeatureDisabled("feature_disabled", false),
    InvalidRequest("invalid_request", false),
    Forbidden("forbidden", false),
    NotFound("not_found", false),
    IdempotencyConflict("idempotency_conflict", false),
    InvalidState("invalid_state", false),
    UploadValidationFailed("upload_validation_failed", true),
    StorageUnavailable("storage_unavailable", true),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetErrorCode? =
            entries.firstOrNull { code -> code.wireValue == value }
    }
}

sealed interface ContentAssetResult<out T> {
    data class Success<T>(val value: T) : ContentAssetResult<T>

    data class Failure(
        val code: ContentAssetErrorCode,
        val developerMessage: String = "",
    ) : ContentAssetResult<Nothing> {
        val retryable: Boolean
            get() = code.retryable
    }
}

data class ContentAssetRecord(
    val assetId: String,
    val ownerId: String,
    val workspaceId: String,
    val pageId: String?,
    val kind: ContentAssetKind,
    val storageKey: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
    val status: ContentAssetStatus,
    val errorCode: ContentAssetErrorCode? = null,
    val idempotencyKey: String,
    val requestFingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val storageDeletedAtEpochMillis: Long? = null,
)

data class ContentAssetClaim(
    val record: ContentAssetRecord,
    val isNew: Boolean,
)

sealed interface ContentAssetUpdate {
    data class Updated(val record: ContentAssetRecord) : ContentAssetUpdate
    data object NotFound : ContentAssetUpdate
    data class Conflict(val current: ContentAssetRecord) : ContentAssetUpdate
}

interface ContentAssetRepository {
    suspend fun claim(record: ContentAssetRecord): ContentAssetClaim

    suspend fun get(
        ownerId: String,
        assetId: String,
        includeDeleted: Boolean = false,
    ): ContentAssetRecord?

    suspend fun updateLifecycle(
        ownerId: String,
        assetId: String,
        expectedStatuses: Set<ContentAssetStatus>,
        nextStatus: ContentAssetStatus,
        errorCode: ContentAssetErrorCode?,
        updatedAtEpochMillis: Long,
        deletedAtEpochMillis: Long? = null,
    ): ContentAssetUpdate

    suspend fun markStorageDeleted(
        ownerId: String,
        assetId: String,
        deletedAtEpochMillis: Long,
    ): ContentAssetRecord?

    suspend fun listOrphanedUploads(
        createdBeforeEpochMillis: Long,
        limit: Int,
    ): List<ContentAssetRecord>

    suspend fun listPendingStorageDeletes(limit: Int): List<ContentAssetRecord>
}

class ContentAssetIdempotencyConflictException :
    IllegalStateException("The content asset idempotency key was already used with a different request.")

fun ContentAssetStatus.canTransitionTo(next: ContentAssetStatus): Boolean = when (this) {
    ContentAssetStatus.UploadQueued -> next in setOf(
        ContentAssetStatus.RemoteReady,
        ContentAssetStatus.RetryableFailure,
        ContentAssetStatus.PermanentFailure,
        ContentAssetStatus.Deleted,
    )
    ContentAssetStatus.RetryableFailure -> next in setOf(
        ContentAssetStatus.UploadQueued,
        ContentAssetStatus.RemoteReady,
        ContentAssetStatus.PermanentFailure,
        ContentAssetStatus.Deleted,
    )
    ContentAssetStatus.RemoteReady,
    ContentAssetStatus.PermanentFailure -> next == ContentAssetStatus.Deleted
    ContentAssetStatus.Deleted -> next == ContentAssetStatus.Deleted
}
