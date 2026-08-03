package com.changeyourlife.cyl.domain.model

enum class ContentAssetKind(val wireValue: String) {
    IMAGE("image"),
    PDF("pdf"),
    TEXT("text"),
    FILE("file"),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetKind =
            entries.firstOrNull { kind -> kind.wireValue.equals(value.trim(), ignoreCase = true) } ?: FILE

        fun fromMimeType(mimeType: String): ContentAssetKind = when {
            mimeType.startsWith("image/", ignoreCase = true) -> IMAGE
            mimeType.equals("application/pdf", ignoreCase = true) -> PDF
            mimeType.startsWith("text/", ignoreCase = true) -> TEXT
            mimeType.substringBefore(';').trim().lowercase() in ReadableApplicationTextMimeTypes -> TEXT
            else -> FILE
        }
    }
}

private val ReadableApplicationTextMimeTypes = setOf(
    "application/json",
    "application/xml",
    "application/yaml",
    "application/x-yaml",
    "application/csv",
    "application/sql",
    "application/javascript",
)

enum class ContentAssetStatus(val wireValue: String) {
    LOCAL_READY("local_ready"),
    UPLOAD_QUEUED("upload_queued"),
    UPLOADING("uploading"),
    REMOTE_READY("remote_ready"),
    DOWNLOAD_REQUIRED("download_required"),
    RETRYABLE_FAILURE("retryable_failure"),
    PERMANENT_FAILURE("permanent_failure"),
    DELETED("deleted"),
    ;

    companion object {
        fun fromWireValue(value: String): ContentAssetStatus =
            entries.firstOrNull { status ->
                status.wireValue.equals(value.trim(), ignoreCase = true)
            } ?: PERMANENT_FAILURE
    }
}

data class ContentAsset(
    val id: String,
    val workspaceId: String,
    val ownerPageId: String? = null,
    val kind: ContentAssetKind,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val localPath: String? = null,
    val remoteAssetId: String? = null,
    val status: ContentAssetStatus,
    val progressPercent: Int = 0,
    val errorCode: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class StageContentAssetRequest(
    val workspaceId: String,
    val ownerPageId: String? = null,
    val sourceUri: String,
    val suggestedName: String = "",
    val declaredMimeType: String = "",
    val maxBytes: Long = ContentAssetLimits.MAX_SINGLE_ASSET_BYTES,
)

sealed interface ContentAssetStageResult {
    data class Success(val asset: ContentAsset) : ContentAssetStageResult

    data class Failure(
        val error: ContentAssetStageError,
        val detail: String = "",
    ) : ContentAssetStageResult
}

enum class ContentAssetStageError {
    INVALID_REQUEST,
    INVALID_SOURCE,
    PERMISSION_DENIED,
    SOURCE_UNAVAILABLE,
    EMPTY_FILE,
    TOO_LARGE,
    STORAGE_UNAVAILABLE,
    PERSISTENCE_FAILED,
    UNKNOWN,
}

data class LocalContentAssetCopyRequest(
    val assetId: String,
    val sourceUri: String,
    val suggestedName: String,
    val declaredMimeType: String,
    val maxBytes: Long,
)

sealed interface LocalContentAssetCopyResult {
    data class Success(
        val localPath: String,
        val displayName: String,
        val mimeType: String,
        val kind: ContentAssetKind,
        val sizeBytes: Long,
        val sha256: String,
    ) : LocalContentAssetCopyResult

    data class Failure(
        val error: ContentAssetStageError,
        val detail: String = "",
    ) : LocalContentAssetCopyResult
}

object ContentAssetLimits {
    const val MAX_SINGLE_ASSET_BYTES = 100L * 1024L * 1024L
    const val MAX_DISPLAY_NAME_LENGTH = 120
}
