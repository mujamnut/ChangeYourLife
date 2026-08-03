package com.changeyourlife.cyl.data.remote.asset

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentAssetApi {
    @POST("api/v1/content-assets/upload-intents")
    suspend fun createUploadIntent(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateContentAssetUploadIntentRequestDto,
    ): Response<ContentAssetUploadIntentResponseDto>

    @POST("api/v1/content-assets/{assetId}/complete")
    suspend fun completeUpload(
        @Header("Authorization") authorization: String,
        @Path("assetId") assetId: String,
    ): Response<ContentAssetResponseDto>

    @GET("api/v1/content-assets/{assetId}")
    suspend fun getAsset(
        @Header("Authorization") authorization: String,
        @Path("assetId") assetId: String,
        @Query("includeDownload") includeDownload: Boolean = false,
    ): Response<ContentAssetResponseDto>

    @DELETE("api/v1/content-assets/{assetId}")
    suspend fun deleteAsset(
        @Header("Authorization") authorization: String,
        @Path("assetId") assetId: String,
    ): Response<Unit>
}

@Serializable
data class CreateContentAssetUploadIntentRequestDto(
    val assetId: String,
    val workspaceId: String,
    val pageId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class ContentAssetUploadIntentResponseDto(
    val assetId: String,
    val status: String,
    val uploadUrl: String? = null,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMillis: Long? = null,
    val replayed: Boolean = false,
)

@Serializable
data class ContentAssetResponseDto(
    val assetId: String,
    val workspaceId: String,
    val pageId: String? = null,
    val kind: String,
    val mimeType: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
    val status: String,
    val errorCode: String? = null,
    val downloadUrl: String? = null,
    val downloadUrlExpiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ContentAssetErrorResponseDto(val error: ContentAssetApiErrorDto)

@Serializable
data class ContentAssetApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
