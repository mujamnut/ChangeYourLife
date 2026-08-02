package com.changeyourlife.cyl.backend.storage

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.backend.domain.VoiceAssetMetadata
import com.changeyourlife.cyl.backend.domain.VoiceAssetReadHandle
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.VoiceAssetUploadRequest
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class R2VoiceAssetStorage(
    endpoint: String,
    private val bucket: String,
    accessKeyId: String,
    secretAccessKey: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : VoiceAssetStorage, AutoCloseable {
    private val credentialsProvider = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKeyId, secretAccessKey),
    )
    private val serviceConfiguration = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .chunkedEncodingEnabled(false)
        .build()
    private val endpointUri = URI.create(endpoint.trimEnd('/'))
    private val s3Client = S3Client.builder()
        .endpointOverride(endpointUri)
        .credentialsProvider(credentialsProvider)
        .region(Region.of(R2Region))
        .serviceConfiguration(serviceConfiguration)
        .httpClient(UrlConnectionHttpClient.create())
        .build()
    private val presigner = S3Presigner.builder()
        .endpointOverride(endpointUri)
        .credentialsProvider(credentialsProvider)
        .region(Region.of(R2Region))
        .serviceConfiguration(serviceConfiguration)
        .build()

    override suspend fun createUploadIntent(
        request: VoiceAssetUploadRequest,
    ): VoiceAttachmentResult<VoiceAssetUploadIntent> = withContext(Dispatchers.IO) {
        runStorageOperation {
            val metadata = mapOf(
                MetadataSha256 to request.sha256.lowercase(),
                MetadataDurationMs to request.durationMs.toString(),
                MetadataAttachmentId to request.attachmentId,
            )
            val putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(request.storageKey)
                .contentType(request.mimeType)
                .metadata(metadata)
                .build()
            val presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(signatureDuration(request.expiresAtEpochMillis))
                    .putObjectRequest(putRequest)
                    .build(),
            )
            VoiceAssetUploadIntent(
                attachmentId = request.attachmentId,
                uploadUrl = presigned.url().toString(),
                requiredHeaders = buildMap {
                    put(ContentTypeHeader, request.mimeType)
                    put("x-amz-meta-$MetadataSha256", request.sha256.lowercase())
                    put("x-amz-meta-$MetadataDurationMs", request.durationMs.toString())
                    put("x-amz-meta-$MetadataAttachmentId", request.attachmentId)
                },
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            )
        }
    }

    override suspend fun head(storageKey: String): VoiceAttachmentResult<VoiceAssetMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val response = s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .build(),
                )
                val metadata = response.metadata()
                VoiceAttachmentResult.Success(
                    VoiceAssetMetadata(
                        storageKey = storageKey,
                        mimeType = response.contentType().orEmpty(),
                        sizeBytes = response.contentLength() ?: 0L,
                        durationMs = metadata[MetadataDurationMs]?.toLongOrNull() ?: 0L,
                        sha256 = metadata[MetadataSha256].orEmpty(),
                    ),
                )
            } catch (failure: S3Exception) {
                if (failure.statusCode() == 404) {
                    missingAsset()
                } else {
                    storageFailure(failure)
                }
            } catch (failure: RuntimeException) {
                storageFailure(failure)
            }
        }

    override suspend fun createReadHandle(
        storageKey: String,
        expiresAtEpochMillis: Long,
    ): VoiceAttachmentResult<VoiceAssetReadHandle> = withContext(Dispatchers.IO) {
        runStorageOperation {
            val getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build()
            val presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(signatureDuration(expiresAtEpochMillis))
                    .getObjectRequest(getRequest)
                    .build(),
            )
            VoiceAssetReadHandle(
                readUrl = presigned.url().toString(),
                expiresAtEpochMillis = expiresAtEpochMillis,
            )
        }
    }

    override suspend fun delete(storageKey: String): VoiceAttachmentResult<Unit> = withContext(Dispatchers.IO) {
        runStorageOperation {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build(),
            )
            Unit
        }
    }

    override fun close() {
        presigner.close()
        s3Client.close()
    }

    private fun signatureDuration(expiresAtEpochMillis: Long): Duration {
        val remainingMillis = (expiresAtEpochMillis - nowEpochMillis())
            .coerceIn(MinSignatureDurationMillis, MaxSignatureDurationMillis)
        return Duration.ofMillis(remainingMillis)
    }

    private inline fun <T> runStorageOperation(block: () -> T): VoiceAttachmentResult<T> = try {
        VoiceAttachmentResult.Success(block())
    } catch (failure: RuntimeException) {
        storageFailure(failure)
    }

    private fun missingAsset(): VoiceAttachmentResult.Failure = VoiceAttachmentResult.Failure(
        code = ChatAttachmentErrorCode.AttachmentNotFound,
        developerMessage = "The private R2 object was not found.",
    )

    private fun storageFailure(failure: Throwable): VoiceAttachmentResult.Failure = VoiceAttachmentResult.Failure(
        code = ChatAttachmentErrorCode.StorageUnavailable,
        developerMessage = failure.message.orEmpty().take(MaxDeveloperMessageChars),
    )
}

private const val R2Region = "auto"
private const val ContentTypeHeader = "Content-Type"
private const val MetadataSha256 = "sha256"
private const val MetadataDurationMs = "duration-ms"
private const val MetadataAttachmentId = "attachment-id"
private const val MinSignatureDurationMillis = 1_000L
private const val MaxSignatureDurationMillis = 7L * 24L * 60L * 60L * 1_000L
private const val MaxDeveloperMessageChars = 500
