package com.changeyourlife.cyl.domain.repository

interface ChatAttachmentUploadScheduler {
    fun enqueue(attachmentId: String)

    suspend fun resumePendingUploads()
}
