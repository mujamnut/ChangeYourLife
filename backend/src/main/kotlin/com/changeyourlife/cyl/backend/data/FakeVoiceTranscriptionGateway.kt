package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult
import com.changeyourlife.cyl.backend.domain.VoiceTranscriptResult
import com.changeyourlife.cyl.backend.domain.VoiceTranscriptionGateway
import com.changeyourlife.cyl.backend.domain.VoiceTranscriptionRequest
import java.util.concurrent.ConcurrentHashMap

class FakeVoiceTranscriptionGateway : VoiceTranscriptionGateway {
    private val results = ConcurrentHashMap<String, VoiceAttachmentResult<VoiceTranscriptResult>>()

    override suspend fun transcribe(
        request: VoiceTranscriptionRequest,
    ): VoiceAttachmentResult<VoiceTranscriptResult> =
        results[request.attachmentId]
            ?: VoiceAttachmentResult.Failure(
                code = ChatAttachmentErrorCode.TranscriptionUnavailable,
                developerMessage = "No fake transcription fixture exists for ${request.attachmentId}.",
            )

    fun setResult(
        attachmentId: String,
        result: VoiceAttachmentResult<VoiceTranscriptResult>,
    ) {
        require(attachmentId.isNotBlank()) { "attachmentId must not be blank." }
        results[attachmentId] = result
    }
}
