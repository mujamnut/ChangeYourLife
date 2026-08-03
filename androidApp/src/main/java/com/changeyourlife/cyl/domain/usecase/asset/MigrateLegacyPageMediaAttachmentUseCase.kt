package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetStageError
import com.changeyourlife.cyl.domain.model.ContentAssetStageResult
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.StageContentAssetRequest
import javax.inject.Inject

class MigrateLegacyPageMediaAttachmentUseCase @Inject constructor(
    private val stageContentAsset: StageContentAssetUseCase,
) {
    suspend operator fun invoke(
        workspaceId: String,
        pageId: String,
        attachment: PageMediaAttachment,
    ): LegacyPageMediaMigrationResult {
        if (attachment.assetId.isNotBlank()) {
            return LegacyPageMediaMigrationResult.NotRequired(attachment)
        }
        if (attachment.uri.isBlank()) {
            return LegacyPageMediaMigrationResult.Failure(ContentAssetStageError.INVALID_SOURCE)
        }

        return when (
            val staged = stageContentAsset(
                StageContentAssetRequest(
                    workspaceId = workspaceId,
                    ownerPageId = pageId,
                    sourceUri = attachment.uri,
                    suggestedName = attachment.name,
                    declaredMimeType = attachment.mimeType,
                ),
            )
        ) {
            is ContentAssetStageResult.Success -> {
                val asset = staged.asset
                LegacyPageMediaMigrationResult.Success(
                    attachment = attachment.copy(
                        assetId = asset.id,
                        name = asset.displayName,
                        mimeType = asset.mimeType,
                        sizeBytes = asset.sizeBytes,
                    ),
                    asset = asset,
                )
            }

            is ContentAssetStageResult.Failure -> {
                LegacyPageMediaMigrationResult.Failure(
                    error = staged.error,
                    detail = staged.detail,
                )
            }
        }
    }
}

sealed interface LegacyPageMediaMigrationResult {
    data class Success(
        val attachment: PageMediaAttachment,
        val asset: ContentAsset,
    ) : LegacyPageMediaMigrationResult

    data class NotRequired(
        val attachment: PageMediaAttachment,
    ) : LegacyPageMediaMigrationResult

    data class Failure(
        val error: ContentAssetStageError,
        val detail: String = "",
    ) : LegacyPageMediaMigrationResult
}
