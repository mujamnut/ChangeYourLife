package com.changeyourlife.cyl.backend.storage

import com.changeyourlife.cyl.backend.domain.PrivateAssetMetadata
import com.changeyourlife.cyl.backend.domain.PrivateAssetDigest
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadHandle
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadRequest
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorage
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageError
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageResult
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadRequest
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

class R2PrivateAssetStorage(
    endpoint: String,
    private val bucket: String,
    accessKeyId: String,
    secretAccessKey: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : PrivateAssetStorage, AutoCloseable {
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
        request: PrivateAssetUploadRequest,
    ): PrivateAssetStorageResult<PrivateAssetUploadIntent> = withContext(Dispatchers.IO) {
        runStorageOperation {
            val metadata = buildMap {
                put(MetadataSha256, request.sha256.lowercase())
                request.metadata.forEach { (key, value) ->
                    if (key.matches(SafeMetadataKey) && value.length <= MaxMetadataValueChars) {
                        put(key, value)
                    }
                }
            }
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
            PrivateAssetUploadIntent(
                assetId = request.assetId,
                uploadUrl = presigned.url().toString(),
                requiredHeaders = buildMap {
                    put(ContentTypeHeader, request.mimeType)
                    metadata.forEach { (key, value) -> put("x-amz-meta-$key", value) }
                },
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            )
        }
    }

    override suspend fun head(storageKey: String): PrivateAssetStorageResult<PrivateAssetMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val response = s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .build(),
                )
                val metadata = response.metadata()
                PrivateAssetStorageResult.Success(
                    PrivateAssetMetadata(
                        storageKey = storageKey,
                        mimeType = response.contentType().orEmpty(),
                        sizeBytes = response.contentLength() ?: 0L,
                        sha256 = metadata[MetadataSha256].orEmpty(),
                        metadata = metadata,
                    ),
                )
            } catch (failure: S3Exception) {
                if (failure.statusCode() == 404) missingAsset() else storageFailure(failure)
            } catch (failure: RuntimeException) {
                storageFailure(failure)
            }
        }

    override suspend fun calculateDigest(
        storageKey: String,
        maxBytes: Long,
    ): PrivateAssetStorageResult<PrivateAssetDigest> = withContext(Dispatchers.IO) {
        if (maxBytes <= 0L) {
            return@withContext PrivateAssetStorageResult.Failure(
                error = PrivateAssetStorageError.InvalidObject,
                developerMessage = "The digest byte limit is invalid.",
            )
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build(),
            ).use { source ->
                val buffer = ByteArray(DigestBufferSize)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (totalBytes > maxBytes - read) {
                        return@withContext PrivateAssetStorageResult.Failure(
                            error = PrivateAssetStorageError.InvalidObject,
                            developerMessage = "The stored object exceeds its verified byte limit.",
                        )
                    }
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            }
            PrivateAssetStorageResult.Success(
                PrivateAssetDigest(
                    sizeBytes = totalBytes,
                    sha256 = digest.digest().joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xFF)
                    },
                ),
            )
        } catch (failure: S3Exception) {
            if (failure.statusCode() == 404) missingAsset() else storageFailure(failure)
        } catch (failure: RuntimeException) {
            storageFailure(failure)
        }
    }

    override suspend fun createReadHandle(
        request: PrivateAssetReadRequest,
    ): PrivateAssetStorageResult<PrivateAssetReadHandle> = withContext(Dispatchers.IO) {
        runStorageOperation {
            val getBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(request.storageKey)
            if (request.forceDownload) {
                getBuilder.responseContentDisposition(
                    contentDisposition(request.downloadFileName),
                )
            }
            val presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(signatureDuration(request.expiresAtEpochMillis))
                    .getObjectRequest(getBuilder.build())
                    .build(),
            )
            PrivateAssetReadHandle(
                readUrl = presigned.url().toString(),
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            )
        }
    }

    override suspend fun delete(storageKey: String): PrivateAssetStorageResult<Unit> =
        withContext(Dispatchers.IO) {
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

    private fun contentDisposition(rawFileName: String): String {
        val safeName = rawFileName
            .filterNot(Char::isISOControl)
            .replace('"', '_')
            .take(MaxDownloadFileNameChars)
            .ifBlank { DefaultDownloadFileName }
        val encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8)
            .replace("+", "%20")
        return "attachment; filename=\"download\"; filename*=UTF-8''$encoded"
    }

    private inline fun <T> runStorageOperation(block: () -> T): PrivateAssetStorageResult<T> = try {
        PrivateAssetStorageResult.Success(block())
    } catch (failure: RuntimeException) {
        storageFailure(failure)
    }

    private fun missingAsset(): PrivateAssetStorageResult.Failure = PrivateAssetStorageResult.Failure(
        error = PrivateAssetStorageError.NotFound,
        developerMessage = "The private R2 object was not found.",
    )

    private fun storageFailure(failure: Throwable): PrivateAssetStorageResult.Failure =
        PrivateAssetStorageResult.Failure(
            error = PrivateAssetStorageError.Unavailable,
            developerMessage = failure.message.orEmpty().take(MaxDeveloperMessageChars),
        )

    private companion object {
        const val R2Region = "auto"
        const val ContentTypeHeader = "Content-Type"
        const val MetadataSha256 = "sha256"
        const val MinSignatureDurationMillis = 1_000L
        const val MaxSignatureDurationMillis = 7L * 24L * 60L * 60L * 1_000L
        const val MaxDeveloperMessageChars = 500
        const val MaxMetadataValueChars = 500
        const val MaxDownloadFileNameChars = 180
        const val DigestBufferSize = 64 * 1024
        const val DefaultDownloadFileName = "attachment"
        val SafeMetadataKey = Regex("[a-z0-9-]{1,64}")
    }
}
