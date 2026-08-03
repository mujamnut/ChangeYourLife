package com.changeyourlife.cyl.data.remote.asset

import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteDeleteResult
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteReadResult
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteUploadResult
import com.changeyourlife.cyl.domain.repository.ContentAssetRemoteDescriptor
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferError
import com.changeyourlife.cyl.domain.repository.ContentAssetTransferGateway
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response as OkHttpResponse
import okio.BufferedSink
import retrofit2.Response

class HttpContentAssetTransferGateway @Inject constructor(
    private val api: ContentAssetApi,
    private val tokenStore: AuthTokenStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : ContentAssetTransferGateway {
    override suspend fun upload(
        asset: ContentAsset,
        onRemoteAccepted: (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): ContentAssetRemoteUploadResult {
        val authorization = authorization()
            ?: return uploadFailure(ContentAssetTransferError.AUTH_REQUIRED, retryable = true)
        val file = asset.localPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: return uploadFailure(ContentAssetTransferError.NOT_FOUND)
        if (
            file.length() != asset.sizeBytes ||
            !asset.sha256.matches(Sha256Pattern) ||
            asset.workspaceId.isBlank()
        ) {
            return uploadFailure(ContentAssetTransferError.UPLOAD_VALIDATION_FAILED)
        }

        val intent = when (
            val response = runApiCall {
                api.createUploadIntent(
                    authorization = authorization,
                    idempotencyKey = asset.id,
                    request = CreateContentAssetUploadIntentRequestDto(
                        assetId = asset.id,
                        workspaceId = asset.workspaceId,
                        pageId = asset.ownerPageId,
                        kind = asset.kind.wireValue,
                        mimeType = asset.mimeType,
                        originalName = asset.displayName,
                        sizeBytes = asset.sizeBytes,
                        sha256 = asset.sha256.lowercase(),
                    ),
                )
            }
        ) {
            is AssetApiCallResult.Success -> response.body
            is AssetApiCallResult.Failure -> return response.toUploadFailure()
        }
        val remoteAssetId = intent.assetId.takeIf(String::isNotBlank)
            ?: return uploadFailure(ContentAssetTransferError.INVALID_STATE)
        if (remoteAssetId != asset.id) {
            return uploadFailure(ContentAssetTransferError.IDEMPOTENCY_CONFLICT)
        }
        onRemoteAccepted(remoteAssetId)
        val intentStatus = ContentAssetStatus.fromWireValue(intent.status)
        if (intent.uploadUrl.isNullOrBlank()) {
            return if (intentStatus == ContentAssetStatus.REMOTE_READY) {
                ContentAssetRemoteUploadResult.Success(remoteAssetId, intentStatus)
            } else {
                uploadFailure(ContentAssetTransferError.INVALID_STATE, retryable = true)
            }
        }

        when (
            val direct = uploadFile(
                uploadUrl = intent.uploadUrl,
                requiredHeaders = intent.requiredHeaders,
                file = file,
                mimeType = asset.mimeType,
                onProgress = onProgress,
            )
        ) {
            DirectContentUploadResult.Success -> Unit
            is DirectContentUploadResult.Failure -> return uploadFailure(
                direct.error,
                direct.retryable,
            )
        }

        return when (
            val response = runApiCall {
                api.completeUpload(authorization = authorization, assetId = remoteAssetId)
            }
        ) {
            is AssetApiCallResult.Success -> {
                val status = ContentAssetStatus.fromWireValue(response.body.status)
                if (status == ContentAssetStatus.REMOTE_READY) {
                    ContentAssetRemoteUploadResult.Success(response.body.assetId, status)
                } else {
                    uploadFailure(ContentAssetTransferError.INVALID_STATE, retryable = true)
                }
            }
            is AssetApiCallResult.Failure -> response.toUploadFailure()
        }
    }

    override suspend fun getDownloadHandle(remoteAssetId: String): ContentAssetRemoteReadResult {
        val authorization = authorization()
            ?: return readFailure(ContentAssetTransferError.AUTH_REQUIRED, retryable = true)
        return when (
            val response = runApiCall {
                api.getAsset(
                    authorization = authorization,
                    assetId = remoteAssetId,
                    includeDownload = true,
                )
            }
        ) {
            is AssetApiCallResult.Success -> {
                val url = response.body.downloadUrl?.takeIf(String::isNotBlank)
                val expiresAt = response.body.downloadUrlExpiresAtEpochMillis
                if (url == null || expiresAt == null) {
                    readFailure(ContentAssetTransferError.INVALID_STATE)
                } else {
                    ContentAssetRemoteReadResult.Success(
                        asset = ContentAssetRemoteDescriptor(
                            remoteAssetId = response.body.assetId,
                            workspaceId = response.body.workspaceId,
                            ownerPageId = response.body.pageId,
                            kind = ContentAssetKind.fromWireValue(response.body.kind),
                            displayName = response.body.originalName,
                            mimeType = response.body.mimeType,
                            sizeBytes = response.body.sizeBytes,
                            sha256 = response.body.sha256,
                            createdAtEpochMillis = response.body.createdAtEpochMillis,
                            updatedAtEpochMillis = response.body.updatedAtEpochMillis,
                        ),
                        downloadUrl = url,
                        expiresAtEpochMillis = expiresAt,
                    )
                }
            }
            is AssetApiCallResult.Failure -> ContentAssetRemoteReadResult.Failure(
                response.error,
                response.retryable,
            )
        }
    }

    override suspend fun delete(remoteAssetId: String): ContentAssetRemoteDeleteResult {
        val authorization = authorization()
            ?: return deleteFailure(ContentAssetTransferError.AUTH_REQUIRED, retryable = true)
        val response = try {
            api.deleteAsset(authorization, remoteAssetId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return deleteFailure(ContentAssetTransferError.STORAGE_UNAVAILABLE, retryable = true)
        } catch (_: Throwable) {
            return deleteFailure(ContentAssetTransferError.UNKNOWN, retryable = true)
        }
        if (response.isSuccessful || response.code() == 404) return ContentAssetRemoteDeleteResult.Success
        val failure = response.toAssetFailure()
        return deleteFailure(failure.error, failure.retryable)
    }

    private fun authorization(): String? = tokenStore.token.value
        ?.takeIf(String::isNotBlank)
        ?.let { token -> "Bearer $token" }

    private suspend fun uploadFile(
        uploadUrl: String,
        requiredHeaders: Map<String, String>,
        file: File,
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): DirectContentUploadResult {
        val request = Request.Builder()
            .url(uploadUrl)
            .apply { requiredHeaders.forEach { (name, value) -> header(name, value) } }
            .put(ContentAssetRequestBody(file, mimeType, onProgress))
            .build()
        val response = try {
            okHttpClient.newCall(request).awaitContentResponse()
        } catch (_: IOException) {
            return DirectContentUploadResult.Failure(
                ContentAssetTransferError.STORAGE_UNAVAILABLE,
                retryable = true,
            )
        }
        response.use { result ->
            if (result.isSuccessful) return DirectContentUploadResult.Success
            return when (result.code) {
                401, 403 -> DirectContentUploadResult.Failure(
                    ContentAssetTransferError.STORAGE_UNAVAILABLE,
                    retryable = true,
                )
                408, 425, 429 -> DirectContentUploadResult.Failure(
                    ContentAssetTransferError.STORAGE_UNAVAILABLE,
                    retryable = true,
                )
                in 500..599 -> DirectContentUploadResult.Failure(
                    ContentAssetTransferError.STORAGE_UNAVAILABLE,
                    retryable = true,
                )
                else -> DirectContentUploadResult.Failure(
                    ContentAssetTransferError.UPLOAD_VALIDATION_FAILED,
                    retryable = false,
                )
            }
        }
    }

    private suspend fun <T> runApiCall(call: suspend () -> Response<T>): AssetApiCallResult<T> {
        val response = try {
            call()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return AssetApiCallResult.Failure(
                ContentAssetTransferError.STORAGE_UNAVAILABLE,
                retryable = true,
            )
        } catch (_: Throwable) {
            return AssetApiCallResult.Failure(ContentAssetTransferError.UNKNOWN, retryable = true)
        }
        val body = response.body()
        if (response.isSuccessful && body != null) return AssetApiCallResult.Success(body)
        return response.toAssetFailure()
    }

    private fun <T> Response<T>.toAssetFailure(): AssetApiCallResult.Failure {
        val apiError = errorBody()
            ?.string()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<ContentAssetErrorResponseDto>(payload).error }
                    .getOrNull()
            }
        if (apiError != null) {
            return AssetApiCallResult.Failure(
                ContentAssetTransferError.fromWireValue(apiError.code),
                apiError.retryable,
            )
        }
        val error = when (code()) {
            401 -> ContentAssetTransferError.AUTH_REQUIRED
            403 -> ContentAssetTransferError.FORBIDDEN
            404 -> ContentAssetTransferError.NOT_FOUND
            409 -> ContentAssetTransferError.INVALID_STATE
            422 -> ContentAssetTransferError.UPLOAD_VALIDATION_FAILED
            408, 425, 429 -> ContentAssetTransferError.STORAGE_UNAVAILABLE
            in 500..599 -> ContentAssetTransferError.STORAGE_UNAVAILABLE
            else -> ContentAssetTransferError.UNKNOWN
        }
        return AssetApiCallResult.Failure(
            error = error,
            retryable = error.retryableByDefault,
        )
    }
}

private class ContentAssetRequestBody(
    private val file: File,
    mimeType: String,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    private val mediaType = mimeType.toMediaTypeOrNull()

    override fun contentType() = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        var written = 0L
        var lastProgress = -ProgressStepPercent
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(UploadBufferBytes)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                sink.write(buffer, 0, read)
                written += read
                val progress = ((written * 100L) / total).toInt().coerceIn(0, 100)
                if (progress == 100 || progress - lastProgress >= ProgressStepPercent) {
                    lastProgress = progress
                    onProgress(progress)
                }
            }
        }
    }
}

private suspend fun Call.awaitContentResponse(): OkHttpResponse =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: OkHttpResponse) {
                    continuation.resumeWith(Result.success(response))
                }
            },
        )
    }

private sealed interface AssetApiCallResult<out T> {
    data class Success<T>(val body: T) : AssetApiCallResult<T>

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : AssetApiCallResult<Nothing>
}

private sealed interface DirectContentUploadResult {
    data object Success : DirectContentUploadResult

    data class Failure(
        val error: ContentAssetTransferError,
        val retryable: Boolean,
    ) : DirectContentUploadResult
}

private fun AssetApiCallResult.Failure.toUploadFailure() =
    uploadFailure(error, retryable)

private fun uploadFailure(
    error: ContentAssetTransferError,
    retryable: Boolean = error.retryableByDefault,
) = ContentAssetRemoteUploadResult.Failure(error, retryable)

private fun readFailure(
    error: ContentAssetTransferError,
    retryable: Boolean = error.retryableByDefault,
) = ContentAssetRemoteReadResult.Failure(error, retryable)

private fun deleteFailure(
    error: ContentAssetTransferError,
    retryable: Boolean = error.retryableByDefault,
) = ContentAssetRemoteDeleteResult.Failure(error, retryable)

private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")
private const val ProgressStepPercent = 5
private const val UploadBufferBytes = 64 * 1024
