package com.changeyourlife.cyl.data.remote.attachment

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatAttachmentApi {
    @POST("api/v1/chat-attachments/upload-intents")
    suspend fun createUploadIntent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateChatAttachmentUploadIntentRequestDto,
    ): Response<ChatAttachmentUploadIntentResponseDto>

    @POST("api/v1/chat-attachments/{attachmentId}/complete")
    suspend fun completeUpload(
        @Header("Authorization") authorization: String,
        @Path("attachmentId") attachmentId: String,
    ): Response<ChatAttachmentResponseDto>

    @GET("api/v1/chat-attachments/{attachmentId}")
    suspend fun getAttachment(
        @Header("Authorization") authorization: String,
        @Path("attachmentId") attachmentId: String,
        @Query("includePlayback") includePlayback: Boolean = false,
    ): Response<ChatAttachmentResponseDto>

    @DELETE("api/v1/chat-attachments/{attachmentId}")
    suspend fun deleteAttachment(
        @Header("Authorization") authorization: String,
        @Path("attachmentId") attachmentId: String,
    ): Response<Unit>
}

@Serializable
data class CreateChatAttachmentUploadIntentRequestDto(
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val sessionClientId: String,
    val messageClientId: String?,
)

@Serializable
data class ChatAttachmentUploadIntentResponseDto(
    val attachmentId: String,
    val status: String,
    val uploadUrl: String? = null,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMillis: Long? = null,
    val replayed: Boolean = false,
)

@Serializable
data class ChatAttachmentResponseDto(
    val attachmentId: String,
    val sessionClientId: String,
    val messageClientId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val status: String,
    val transcript: String? = null,
    val transcriptLanguage: String? = null,
    val errorCode: String? = null,
    val playbackUrl: String? = null,
    val playbackUrlExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ChatAttachmentErrorResponseDto(
    val error: ChatAttachmentApiErrorDto,
)

@Serializable
data class ChatAttachmentApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
