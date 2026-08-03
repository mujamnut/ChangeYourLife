package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.AiAttachment
import com.changeyourlife.cyl.domain.model.AiAttachmentPreparationError
import com.changeyourlife.cyl.domain.model.ContentAsset

interface AiAttachmentPayloadReader {
    suspend fun read(
        asset: ContentAsset,
        source: String,
        sourceReferenceId: String,
    ): AiAttachmentPayloadReadResult
}

sealed interface AiAttachmentPayloadReadResult {
    data class Success(val attachment: AiAttachment) : AiAttachmentPayloadReadResult

    data class Rejected(
        val error: AiAttachmentPreparationError,
        val detail: String = "",
    ) : AiAttachmentPayloadReadResult
}
