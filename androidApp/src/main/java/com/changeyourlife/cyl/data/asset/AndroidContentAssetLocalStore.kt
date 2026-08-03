package com.changeyourlife.cyl.data.asset

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetLimits
import com.changeyourlife.cyl.domain.model.ContentAssetStageError
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyRequest
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyResult
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidContentAssetLocalStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ContentAssetLocalStore {
    override suspend fun copyIntoAssetStorage(
        request: LocalContentAssetCopyRequest,
    ): LocalContentAssetCopyResult = withContext(Dispatchers.IO) {
        if (!request.assetId.matches(SafeAssetIdRegex)) {
            return@withContext failure(
                ContentAssetStageError.INVALID_REQUEST,
                "Invalid asset identifier.",
            )
        }

        val uri = runCatching { Uri.parse(request.sourceUri) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase(Locale.ROOT) != ContentResolver.SCHEME_CONTENT) {
            return@withContext failure(
                ContentAssetStageError.INVALID_SOURCE,
                "Only Android content sources are supported.",
            )
        }

        val maxBytes = request.maxBytes.coerceAtMost(ContentAssetLimits.MAX_SINGLE_ASSET_BYTES)
        if (maxBytes <= 0L) {
            return@withContext failure(
                ContentAssetStageError.INVALID_REQUEST,
                "Invalid asset size limit.",
            )
        }

        val assetDirectory = File(context.filesDir, AssetDirectoryName)
        var temporaryFile: File? = null
        try {
            if (!assetDirectory.exists() && !assetDirectory.mkdirs()) {
                throw AssetCopyException(
                    ContentAssetStageError.STORAGE_UNAVAILABLE,
                    "Private asset storage is unavailable.",
                )
            }
            if (!assetDirectory.isDirectory) {
                throw AssetCopyException(
                    ContentAssetStageError.STORAGE_UNAVAILABLE,
                    "Private asset storage is unavailable.",
                )
            }

            val metadata = querySourceMetadata(uri)
            if (metadata.sizeBytes != null && metadata.sizeBytes > maxBytes) {
                throw AssetCopyException(
                    ContentAssetStageError.TOO_LARGE,
                    "Asset exceeds the configured size limit.",
                )
            }

            val displayName = sanitizeDisplayName(
                metadata.displayName.ifBlank { request.suggestedName },
            )
            val stagingFile = File(assetDirectory, ".${request.assetId}.partial")
            temporaryFile = stagingFile
            if (stagingFile.exists() && !stagingFile.delete()) {
                throw AssetCopyException(
                    ContentAssetStageError.STORAGE_UNAVAILABLE,
                    "A previous partial asset could not be removed.",
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val signature = ByteArray(SignatureByteCount)
            var signatureSize = 0
            var totalBytes = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw AssetCopyException(
                    ContentAssetStageError.SOURCE_UNAVAILABLE,
                    "The shared content cannot be opened.",
                )

            input.use { source ->
                FileOutputStream(stagingFile).use { destination ->
                    val buffer = ByteArray(CopyBufferSize)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        if (totalBytes > maxBytes - read) {
                            throw AssetCopyException(
                                ContentAssetStageError.TOO_LARGE,
                                "Asset exceeds the configured size limit.",
                            )
                        }

                        if (signatureSize < signature.size) {
                            val count = minOf(read, signature.size - signatureSize)
                            buffer.copyInto(
                                destination = signature,
                                destinationOffset = signatureSize,
                                startIndex = 0,
                                endIndex = count,
                            )
                            signatureSize += count
                        }
                        digest.update(buffer, 0, read)
                        destination.write(buffer, 0, read)
                        totalBytes += read
                    }
                    destination.fd.sync()
                }
            }

            if (totalBytes == 0L) {
                throw AssetCopyException(
                    ContentAssetStageError.EMPTY_FILE,
                    "The shared content is empty.",
                )
            }

            val providerMimeType = metadata.mimeType
            val mimeType = inferMimeType(
                signature = signature.copyOf(signatureSize),
                providerMimeType = providerMimeType,
                declaredMimeType = request.declaredMimeType,
                displayName = displayName,
            )
            val extension = safeExtension(displayName, mimeType)
            val finalName = if (extension.isBlank()) request.assetId else "${request.assetId}.$extension"
            val finalFile = File(assetDirectory, finalName)
            if (finalFile.exists() && !finalFile.delete()) {
                throw AssetCopyException(
                    ContentAssetStageError.STORAGE_UNAVAILABLE,
                    "An existing asset file could not be replaced.",
                )
            }
            if (!stagingFile.renameTo(finalFile)) {
                throw AssetCopyException(
                    ContentAssetStageError.STORAGE_UNAVAILABLE,
                    "The staged asset could not be finalized.",
                )
            }
            temporaryFile = null

            LocalContentAssetCopyResult.Success(
                localPath = finalFile.absolutePath,
                displayName = displayName,
                mimeType = mimeType,
                kind = ContentAssetKind.fromMimeType(mimeType),
                sizeBytes = totalBytes,
                sha256 = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xFF)
                },
            )
        } catch (cancellation: CancellationException) {
            temporaryFile?.delete()
            throw cancellation
        } catch (error: AssetCopyException) {
            temporaryFile?.delete()
            failure(error.stageError, error.safeDetail)
        } catch (_: SecurityException) {
            temporaryFile?.delete()
            failure(
                ContentAssetStageError.PERMISSION_DENIED,
                "Permission to read the shared content was denied.",
            )
        } catch (_: FileNotFoundException) {
            temporaryFile?.delete()
            failure(
                ContentAssetStageError.SOURCE_UNAVAILABLE,
                "The shared content is no longer available.",
            )
        } catch (_: IOException) {
            temporaryFile?.delete()
            failure(
                ContentAssetStageError.STORAGE_UNAVAILABLE,
                "The asset could not be copied to private storage.",
            )
        } catch (_: Exception) {
            temporaryFile?.delete()
            failure(
                ContentAssetStageError.UNKNOWN,
                "The asset could not be staged.",
            )
        }
    }

    override suspend fun delete(localPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.filesDir, AssetDirectoryName).canonicalFile
            val candidate = File(localPath).canonicalFile
            val isInsideRoot = candidate.path.startsWith(root.path + File.separator)
            if (!isInsideRoot || candidate == root || candidate.isDirectory) {
                return@runCatching false
            }
            !candidate.exists() || candidate.delete()
        }.getOrDefault(false)
    }

    override suspend fun isAvailable(localPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.filesDir, AssetDirectoryName).canonicalFile
            val candidate = File(localPath).canonicalFile
            candidate.path.startsWith(root.path + File.separator) &&
                candidate.isFile &&
                candidate.length() > 0L
        }.getOrDefault(false)
    }

    private fun querySourceMetadata(uri: Uri): SourceMetadata {
        var displayName = ""
        var sizeBytes: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex).orEmpty()
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { size -> size >= 0L }
                }
            }
        }
        return SourceMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = normalizeMimeType(context.contentResolver.getType(uri).orEmpty()),
        )
    }

    private fun sanitizeDisplayName(rawName: String): String {
        val cleaned = rawName
            .replace(UnsafeFilenameCharacters, "_")
            .filterNot(Char::isISOControl)
            .trim()
            .trim('.')
            .ifBlank { DefaultDisplayName }
        return cleaned.take(ContentAssetLimits.MAX_DISPLAY_NAME_LENGTH)
    }

    private fun inferMimeType(
        signature: ByteArray,
        providerMimeType: String,
        declaredMimeType: String,
        displayName: String,
    ): String {
        sniffMimeType(signature)?.let { return it }
        providerMimeType.takeUnless(::isGenericMimeType)?.let { return it }
        normalizeMimeType(declaredMimeType).takeUnless(::isGenericMimeType)?.let { return it }
        return mimeTypeFromName(displayName).ifBlank { GenericMimeType }
    }

    private fun sniffMimeType(bytes: ByteArray): String? = when {
        bytes.startsWith(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)) -> "application/pdf"
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> "image/jpeg"
        bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) ->
            "image/png"
        bytes.startsWith("GIF87a".encodeToByteArray()) || bytes.startsWith("GIF89a".encodeToByteArray()) ->
            "image/gif"
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray()) -> "image/webp"
        else -> null
    }

    private fun safeExtension(displayName: String, mimeType: String): String {
        val fromName = displayName.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { extension -> extension.matches(SafeExtensionRegex) }
        return fromName ?: when (mimeType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "text/plain" -> "txt"
            "text/html" -> "html"
            "text/csv" -> "csv"
            "application/json" -> "json"
            else -> ""
        }
    }

    private fun mimeTypeFromName(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt", "md", "markdown", "log" -> "text/plain"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> ""
        }
    }

    private fun normalizeMimeType(value: String): String =
        value.substringBefore(';').trim().lowercase(Locale.ROOT)

    private fun isGenericMimeType(value: String): Boolean =
        value.isBlank() || value == GenericMimeType || value == "*/*"

    private fun failure(
        error: ContentAssetStageError,
        detail: String,
    ): LocalContentAssetCopyResult.Failure = LocalContentAssetCopyResult.Failure(error, detail)

    private data class SourceMetadata(
        val displayName: String,
        val sizeBytes: Long?,
        val mimeType: String,
    )

    private class AssetCopyException(
        val stageError: ContentAssetStageError,
        val safeDetail: String,
    ) : IOException(safeDetail)

    private companion object {
        const val AssetDirectoryName = "content_assets"
        const val DefaultDisplayName = "Untitled file"
        const val CopyBufferSize = 64 * 1024
        const val SignatureByteCount = 16
        const val GenericMimeType = "application/octet-stream"

        val SafeAssetIdRegex = Regex("[A-Za-z0-9_-]{1,80}")
        val SafeExtensionRegex = Regex("[a-z0-9]{1,10}")
        val UnsafeFilenameCharacters = Regex("[\\\\/:*?\"<>|]")
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { index -> this[index] == prefix[index] }
}
