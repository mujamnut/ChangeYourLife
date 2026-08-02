package com.changeyourlife.cyl.domain.model

import com.changeyourlife.cyl.aicontract.ChatAttachmentKind

data class AiAttachment(
    val id: String = "",
    val assetId: String = "",
    val dataUrl: String = "",
    val previewDataUrl: String = "",
    val textContent: String = "",
    val mimeType: String = "",
    val name: String = "",
    val sizeBytes: Long = 0,
    val kind: String = ChatAttachmentKind.Image.wireValue,
    val durationMs: Long? = null,
    val sha256: String = "",
    val localPath: String = "",
    val waveform: List<Int> = emptyList(),
    val status: String = "",
) {
    val attachmentKind: ChatAttachmentKind
        get() = ChatAttachmentKind.fromWireValue(kind)

    val isRemoteReference: Boolean
        get() = assetId.isNotBlank()
}
