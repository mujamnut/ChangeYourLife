package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetStageError
import com.changeyourlife.cyl.domain.model.IncomingShareErrorCode
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.model.IncomingShareLimits
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyRequest
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyResult
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import javax.inject.Inject

class StageIncomingShareItemUseCase @Inject constructor(
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(
        item: IncomingShareItem,
        remainingTotalBytes: Long,
    ): IncomingShareItem {
        if (item.kind != IncomingShareItemKind.STREAM) {
            val bytes = (item.html ?: item.text).orEmpty().toByteArray(Charsets.UTF_8).size.toLong()
            return if (bytes in 1..IncomingShareLimits.MAX_TEXT_BYTES && bytes <= remainingTotalBytes) {
                item.copy(
                    resolvedMimeType = if (item.kind == IncomingShareItemKind.HTML) "text/html" else "text/plain",
                    assetKind = ContentAssetKind.TEXT,
                    sizeBytes = bytes,
                    status = IncomingShareItemStatus.STAGED,
                    errorCode = null,
                )
            } else {
                item.failed(
                    if (bytes > remainingTotalBytes) {
                        IncomingShareErrorCode.TOTAL_TOO_LARGE
                    } else {
                        IncomingShareErrorCode.TEXT_TOO_LARGE
                    },
                )
            }
        }

        val sourceUri = item.sourceUri?.takeIf(String::isNotBlank)
            ?: return item.failed(IncomingShareErrorCode.INVALID_URI)
        val copyLimit = minOf(
            remainingTotalBytes,
            IncomingShareLimits.MAX_PDF_BYTES,
        )
        if (copyLimit <= 0L) return item.failed(IncomingShareErrorCode.TOTAL_TOO_LARGE)
        return when (
            val copied = localStore.copyIntoAssetStorage(
                LocalContentAssetCopyRequest(
                    assetId = item.id,
                    sourceUri = sourceUri,
                    suggestedName = item.displayName,
                    declaredMimeType = item.declaredMimeType,
                    maxBytes = copyLimit,
                ),
            )
        ) {
            is LocalContentAssetCopyResult.Failure -> item.failed(copied.error.toShareError())
            is LocalContentAssetCopyResult.Success -> {
                val kindLimit = when (copied.kind) {
                    ContentAssetKind.IMAGE -> IncomingShareLimits.MAX_IMAGE_BYTES
                    ContentAssetKind.PDF -> IncomingShareLimits.MAX_PDF_BYTES
                    ContentAssetKind.TEXT -> IncomingShareLimits.MAX_TEXT_BYTES
                    ContentAssetKind.FILE -> 0L
                }
                if (kindLimit <= 0L || copied.sizeBytes > kindLimit) {
                    localStore.delete(copied.localPath)
                    item.failed(
                        if (kindLimit <= 0L) {
                            IncomingShareErrorCode.UNSUPPORTED_TYPE
                        } else {
                            IncomingShareErrorCode.FILE_TOO_LARGE
                        },
                    )
                } else {
                    item.copy(
                        displayName = copied.displayName,
                        stagedPath = copied.localPath,
                        resolvedMimeType = copied.mimeType,
                        assetKind = copied.kind,
                        sizeBytes = copied.sizeBytes,
                        sha256 = copied.sha256,
                        status = IncomingShareItemStatus.STAGED,
                        errorCode = null,
                    )
                }
            }
        }
    }
}

private fun IncomingShareItem.failed(error: IncomingShareErrorCode) = copy(
    status = IncomingShareItemStatus.FAILED,
    errorCode = error.wireValue,
)

private fun ContentAssetStageError.toShareError(): IncomingShareErrorCode = when (this) {
    ContentAssetStageError.INVALID_REQUEST,
    ContentAssetStageError.INVALID_SOURCE -> IncomingShareErrorCode.INVALID_URI
    ContentAssetStageError.PERMISSION_DENIED -> IncomingShareErrorCode.PERMISSION_DENIED
    ContentAssetStageError.SOURCE_UNAVAILABLE -> IncomingShareErrorCode.SOURCE_UNAVAILABLE
    ContentAssetStageError.EMPTY_FILE -> IncomingShareErrorCode.EMPTY_FILE
    ContentAssetStageError.TOO_LARGE -> IncomingShareErrorCode.FILE_TOO_LARGE
    ContentAssetStageError.STORAGE_UNAVAILABLE -> IncomingShareErrorCode.STORAGE_UNAVAILABLE
    ContentAssetStageError.PERSISTENCE_FAILED -> IncomingShareErrorCode.PERSISTENCE_FAILED
    ContentAssetStageError.UNKNOWN -> IncomingShareErrorCode.UNKNOWN
}
