package com.changeyourlife.cyl.domain.model

enum class IncomingShareDraftStatus(val wireValue: String) {
    RECEIVED("received"),
    VALIDATING("validating"),
    STAGED("staged"),
    IMPORTING("importing"),
    UPLOAD_QUEUED("upload_queued"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWireValue(value: String): IncomingShareDraftStatus =
            entries.firstOrNull { status -> status.wireValue == value } ?: FAILED
    }
}

enum class IncomingShareItemKind(val wireValue: String) {
    TEXT("text"),
    HTML("html"),
    URL("url"),
    STREAM("stream"),
    ;

    companion object {
        fun fromWireValue(value: String): IncomingShareItemKind =
            entries.firstOrNull { kind -> kind.wireValue == value } ?: STREAM
    }
}

enum class IncomingShareItemStatus(val wireValue: String) {
    RECEIVED("received"),
    STAGING("staging"),
    STAGED("staged"),
    FAILED("failed"),
    REMOVED("removed"),
    ;

    companion object {
        fun fromWireValue(value: String): IncomingShareItemStatus =
            entries.firstOrNull { status -> status.wireValue == value } ?: FAILED
    }
}

enum class IncomingShareErrorCode(val wireValue: String) {
    UNSUPPORTED_ACTION("unsupported_action"),
    EMPTY_SHARE("empty_share"),
    TOO_MANY_ITEMS("too_many_items"),
    TEXT_TOO_LARGE("text_too_large"),
    INVALID_URI("invalid_uri"),
    PERMISSION_DENIED("permission_denied"),
    SOURCE_UNAVAILABLE("source_unavailable"),
    UNSUPPORTED_TYPE("unsupported_type"),
    EMPTY_FILE("empty_file"),
    FILE_TOO_LARGE("file_too_large"),
    TOTAL_TOO_LARGE("total_too_large"),
    STORAGE_UNAVAILABLE("storage_unavailable"),
    PERSISTENCE_FAILED("persistence_failed"),
    PARTIAL_FAILURE("partial_failure"),
    UNKNOWN("unknown"),
}

data class IncomingShareDraft(
    val id: String,
    val eventId: String,
    val action: String,
    val subject: String,
    val status: IncomingShareDraftStatus,
    val items: List<IncomingShareItem>,
    val errorCode: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
)

data class IncomingShareItem(
    val id: String,
    val draftId: String,
    val position: Int,
    val kind: IncomingShareItemKind,
    val sourceUri: String? = null,
    val text: String? = null,
    val html: String? = null,
    val displayName: String = "",
    val declaredMimeType: String = "",
    val stagedPath: String? = null,
    val resolvedMimeType: String = "",
    val assetKind: ContentAssetKind? = null,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val status: IncomingShareItemStatus,
    val errorCode: String? = null,
)

data class IncomingShareDraftSeed(
    val id: String,
    val eventId: String,
    val action: String,
    val subject: String,
    val status: IncomingShareDraftStatus,
    val items: List<IncomingShareItem>,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
)

object IncomingShareLimits {
    const val MAX_ITEMS = 10
    const val MAX_TOTAL_BYTES = 100L * 1024L * 1024L
    const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
    const val MAX_PDF_BYTES = 50L * 1024L * 1024L
    const val MAX_TEXT_BYTES = 1L * 1024L * 1024L
    const val DRAFT_TTL_MILLIS = 24L * 60L * 60L * 1_000L
}
