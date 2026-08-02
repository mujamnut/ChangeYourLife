package com.changeyourlife.cyl.data.remote.attachment

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentStatus
import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.repository.ChatAttachmentChecksumResult
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRemoteUploadResult
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadGateway
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

class HttpChatAttachmentUploadGateway @Inject constructor(
    private val api: ChatAttachmentApi,
    private val tokenStore: AuthTokenStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : ChatAttachmentUploadGateway {
    override suspend fun calculateSha256(
        attachment: ChatAttachment,
    ): ChatAttachmentChecksumResult = withContext(Dispatchers.IO) {
        val file = attachment.localPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: return@withContext checksumFailure(ChatAttachmentErrorCode.AttachmentNotFound)
        if (file.length() != attachment.sizeBytes) {
            return@withContext checksumFailure(
                code = ChatAttachmentErrorCode.UploadValidationFailed,
                retryable = false,
            )
        }
        runCatching { file.sha256() }
            .fold(
                onSuccess = ChatAttachmentChecksumResult::Success,
                onFailure = { checksumFailure(ChatAttachmentErrorCode.StorageUnavailable) },
            )
    }

    override suspend fun upload(
        attachment: ChatAttachment,
        onRemoteAccepted: (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): ChatAttachmentRemoteUploadResult {
        val token = tokenStore.token.value?.takeIf(String::isNotBlank)
            ?: return uploadFailure(ChatAttachmentErrorCode.AttachmentForbidden, retryable = true)
        val file = attachment.localPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: return uploadFailure(ChatAttachmentErrorCode.AttachmentNotFound)
        val sha256 = attachment.sha256.takeIf { it.matches(Sha256Pattern) }
            ?: return uploadFailure(ChatAttachmentErrorCode.UploadValidationFailed)
        val durationMs = attachment.durationMs
            ?.takeIf { it > 0L }
            ?: return uploadFailure(ChatAttachmentErrorCode.UploadValidationFailed)
        val authorization = "Bearer $token"

        val intentResponse = runApiCall {
            api.createUploadIntent(
                authorization = authorization,
                idempotencyKey = attachment.id,
                request = CreateChatAttachmentUploadIntentRequestDto(
                    kind = attachment.kind.wireValue,
                    mimeType = attachment.mimeType,
                    originalName = attachment.name,
                    sizeBytes = attachment.sizeBytes,
                    durationMs = durationMs,
                    sha256 = sha256,
                    sessionClientId = attachment.sessionId,
                    messageClientId = attachment.messageId,
                ),
            )
        }
        val intent = when (intentResponse) {
            is ApiCallResult.Success -> intentResponse.body
            is ApiCallResult.Failure -> return intentResponse.toUploadFailure()
        }
        val remoteAssetId = intent.attachmentId.takeIf(String::isNotBlank)
            ?: return uploadFailure(ChatAttachmentErrorCode.InvalidState)
        onRemoteAccepted(remoteAssetId)
        val intentStatus = ChatAttachmentStatus.fromWireValue(intent.status)
        if (intent.uploadUrl.isNullOrBlank()) {
            return if (intentStatus in RemoteUploadedStatuses) {
                ChatAttachmentRemoteUploadResult.Success(remoteAssetId, intentStatus)
            } else {
                uploadFailure(ChatAttachmentErrorCode.InvalidState, retryable = true)
            }
        }

        when (
            val putResult = uploadFile(
                uploadUrl = intent.uploadUrl,
                requiredHeaders = intent.requiredHeaders,
                file = file,
                mimeType = attachment.mimeType,
                onProgress = onProgress,
            )
        ) {
            is DirectUploadResult.Failure -> return uploadFailure(
                code = putResult.code,
                retryable = putResult.retryable,
            )
            DirectUploadResult.Success -> Unit
        }

        val completeResponse = runApiCall {
            api.completeUpload(
                authorization = authorization,
                attachmentId = remoteAssetId,
            )
        }
        return when (completeResponse) {
            is ApiCallResult.Success -> ChatAttachmentRemoteUploadResult.Success(
                remoteAssetId = completeResponse.body.attachmentId,
                status = ChatAttachmentStatus.fromWireValue(completeResponse.body.status),
            )
            is ApiCallResult.Failure -> completeResponse.toUploadFailure()
        }
    }

    private suspend fun uploadFile(
        uploadUrl: String,
        requiredHeaders: Map<String, String>,
        file: File,
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): DirectUploadResult {
        val requestBuilder = Request.Builder().url(uploadUrl)
        requiredHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
        val body = ProgressFileRequestBody(
            file = file,
            mimeType = mimeType,
            onProgress = onProgress,
        )
        val response = try {
            okHttpClient.newCall(requestBuilder.put(body).build()).await()
        } catch (_: IOException) {
            return DirectUploadResult.Failure(
                code = ChatAttachmentErrorCode.StorageUnavailable,
                retryable = true,
            )
        }
        response.use { result ->
            if (result.isSuccessful) return DirectUploadResult.Success
            return when (result.code) {
                401, 403 -> DirectUploadResult.Failure(
                    code = ChatAttachmentErrorCode.UploadUrlExpired,
                    retryable = true,
                )
                408, 425, 429 -> DirectUploadResult.Failure(
                    code = ChatAttachmentErrorCode.StorageUnavailable,
                    retryable = true,
                )
                in 500..599 -> DirectUploadResult.Failure(
                    code = ChatAttachmentErrorCode.StorageUnavailable,
                    retryable = true,
                )
                else -> DirectUploadResult.Failure(
                    code = ChatAttachmentErrorCode.UploadValidationFailed,
                    retryable = false,
                )
            }
        }
    }

    private suspend fun <T> runApiCall(call: suspend () -> Response<T>): ApiCallResult<T> {
        val response = try {
            call()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return ApiCallResult.Failure(
                code = ChatAttachmentErrorCode.StorageUnavailable,
                retryable = true,
            )
        } catch (_: Throwable) {
            return ApiCallResult.Failure(
                code = ChatAttachmentErrorCode.Unknown,
                retryable = false,
            )
        }
        val body = response.body()
        if (response.isSuccessful && body != null) return ApiCallResult.Success(body)

        val apiError = response.errorBody()
            ?.string()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<ChatAttachmentErrorResponseDto>(payload).error }
                    .getOrNull()
            }
        if (apiError != null) {
            return ApiCallResult.Failure(
                code = ChatAttachmentErrorCode.fromWireValue(apiError.code),
                retryable = apiError.retryable,
            )
        }
        val code = when (response.code()) {
            401, 403 -> ChatAttachmentErrorCode.AttachmentForbidden
            404 -> ChatAttachmentErrorCode.AttachmentNotFound
            408, 425, 429 -> ChatAttachmentErrorCode.StorageUnavailable
            409 -> ChatAttachmentErrorCode.InvalidState
            422 -> ChatAttachmentErrorCode.UploadValidationFailed
            in 500..599 -> ChatAttachmentErrorCode.StorageUnavailable
            else -> ChatAttachmentErrorCode.Unknown
        }
        return ApiCallResult.Failure(
            code = code,
            retryable = response.code() in listOf(401, 408, 425, 429) || response.code() >= 500,
        )
    }
}

private class ProgressFileRequestBody(
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

private suspend fun Call.await(): OkHttpResponse = suspendCancellableCoroutine { continuation ->
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

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(ChecksumBufferBytes)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

private sealed interface ApiCallResult<out T> {
    data class Success<T>(val body: T) : ApiCallResult<T>

    data class Failure(
        val code: ChatAttachmentErrorCode,
        val retryable: Boolean,
    ) : ApiCallResult<Nothing>
}

private sealed interface DirectUploadResult {
    data object Success : DirectUploadResult

    data class Failure(
        val code: ChatAttachmentErrorCode,
        val retryable: Boolean,
    ) : DirectUploadResult
}

private fun ApiCallResult.Failure.toUploadFailure(): ChatAttachmentRemoteUploadResult.Failure =
    uploadFailure(code = code, retryable = retryable)

private fun uploadFailure(
    code: ChatAttachmentErrorCode,
    retryable: Boolean = code.retryable,
): ChatAttachmentRemoteUploadResult.Failure =
    ChatAttachmentRemoteUploadResult.Failure(code = code, retryable = retryable)

private fun checksumFailure(
    code: ChatAttachmentErrorCode,
    retryable: Boolean = code.retryable,
): ChatAttachmentChecksumResult.Failure =
    ChatAttachmentChecksumResult.Failure(code = code, retryable = retryable)

private val Sha256Pattern = Regex("[A-Fa-f0-9]{64}")
private val RemoteUploadedStatuses = setOf(
    ChatAttachmentStatus.Uploaded,
    ChatAttachmentStatus.Transcribing,
    ChatAttachmentStatus.Ready,
    ChatAttachmentStatus.AiQueued,
    ChatAttachmentStatus.AiProcessing,
    ChatAttachmentStatus.Completed,
)
private const val ProgressStepPercent = 5
private const val UploadBufferBytes = 64 * 1024
private const val ChecksumBufferBytes = 64 * 1024
