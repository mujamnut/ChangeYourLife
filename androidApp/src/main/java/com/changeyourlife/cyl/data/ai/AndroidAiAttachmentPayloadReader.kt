package com.changeyourlife.cyl.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_IMAGE_BYTES
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_PDF_BYTES
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_TEXT_BYTES
import com.changeyourlife.cyl.domain.model.AiAttachment
import com.changeyourlife.cyl.domain.model.AiAttachmentPreparationError
import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReadResult
import com.changeyourlife.cyl.domain.repository.AiAttachmentPayloadReader
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidAiAttachmentPayloadReader @Inject constructor(
    private val localStore: ContentAssetLocalStore,
) : AiAttachmentPayloadReader {
    override suspend fun read(
        asset: ContentAsset,
        source: String,
        sourceReferenceId: String,
    ): AiAttachmentPayloadReadResult = withContext(Dispatchers.IO) {
        val localPath = asset.localPath?.takeIf(String::isNotBlank)
            ?: return@withContext rejected(
                AiAttachmentPreparationError.SOURCE_UNAVAILABLE,
                "The attachment is not available on this device.",
            )
        if (!localStore.isAvailable(localPath)) {
            return@withContext rejected(
                AiAttachmentPreparationError.SOURCE_UNAVAILABLE,
                "The attachment is no longer available on this device.",
            )
        }
        val limit = when (asset.kind) {
            ContentAssetKind.IMAGE -> CYL_MAX_AI_IMAGE_BYTES
            ContentAssetKind.TEXT -> CYL_MAX_AI_TEXT_BYTES
            ContentAssetKind.PDF -> CYL_MAX_AI_PDF_BYTES
            ContentAssetKind.FILE -> return@withContext rejected(
                AiAttachmentPreparationError.UNSUPPORTED_TYPE,
                "This file type is not readable by AI yet.",
            )
        }
        if (asset.sizeBytes <= 0L) {
            return@withContext rejected(AiAttachmentPreparationError.EMPTY_FILE, "The attachment is empty.")
        }
        if (asset.sizeBytes > limit) {
            return@withContext rejected(
                AiAttachmentPreparationError.TOO_LARGE,
                when (asset.kind) {
                    ContentAssetKind.IMAGE -> "Use an image under 4 MB."
                    ContentAssetKind.TEXT -> "Use a text file under 256 KB."
                    ContentAssetKind.PDF -> "Use a PDF under 8 MB for AI analysis."
                    ContentAssetKind.FILE -> "The attachment is too large."
                },
            )
        }

        val bytes = try {
            FileInputStream(File(localPath)).use { input -> input.readBytesBounded(limit) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AttachmentLimitExceededException) {
            return@withContext rejected(
                AiAttachmentPreparationError.TOO_LARGE,
                "The attachment exceeds the maximum AI input size.",
            )
        } catch (_: SecurityException) {
            return@withContext rejected(
                AiAttachmentPreparationError.PERMISSION_DENIED,
                "CYL no longer has permission to read this attachment.",
            )
        } catch (_: Throwable) {
            return@withContext rejected(
                AiAttachmentPreparationError.SOURCE_UNAVAILABLE,
                "The attachment could not be read.",
            )
        }
        if (bytes.isEmpty()) {
            return@withContext rejected(AiAttachmentPreparationError.EMPTY_FILE, "The attachment is empty.")
        }

        val base = AiAttachment(
            id = asset.id,
            assetId = asset.id,
            mimeType = asset.mimeType,
            name = asset.displayName,
            sizeBytes = asset.sizeBytes,
            sha256 = asset.sha256,
            localPath = localPath,
            status = asset.status.wireValue,
            source = source,
            sourceReferenceId = sourceReferenceId,
        )
        when (asset.kind) {
            ContentAssetKind.IMAGE -> {
                if (!bytes.isSupportedImage()) {
                    return@withContext rejected(
                        AiAttachmentPreparationError.CORRUPT_CONTENT,
                        "The selected image is invalid or unsupported.",
                    )
                }
                AiAttachmentPayloadReadResult.Success(
                    base.copy(
                        kind = ChatAttachmentKind.Image.wireValue,
                        dataUrl = bytes.toDataUrl(asset.mimeType),
                        previewDataUrl = bytes.toPreviewDataUrl(),
                    ),
                )
            }
            ContentAssetKind.TEXT -> AiAttachmentPayloadReadResult.Success(
                base.copy(
                    kind = ChatAttachmentKind.TextFile.wireValue,
                    textContent = bytes.toString(Charsets.UTF_8).cleanTextForAi(),
                ),
            )
            ContentAssetKind.PDF -> {
                if (!bytes.isPdf()) {
                    return@withContext rejected(
                        AiAttachmentPreparationError.CORRUPT_CONTENT,
                        "The selected PDF is invalid.",
                    )
                }
                AiAttachmentPayloadReadResult.Success(
                    base.copy(
                        kind = ChatAttachmentKind.Pdf.wireValue,
                        mimeType = PdfMimeType,
                        dataUrl = bytes.toDataUrl(PdfMimeType),
                    ),
                )
            }
            ContentAssetKind.FILE -> error("Handled before payload read.")
        }
    }
}

private suspend fun InputStream.readBytesBounded(limit: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DefaultBufferSize)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (total > limit - read) throw AttachmentLimitExceededException()
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}

private fun ByteArray.toDataUrl(mimeType: String): String =
    "data:$mimeType;base64,${Base64.encodeToString(this, Base64.NO_WRAP)}"

private fun ByteArray.toPreviewDataUrl(): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > PreviewMaxDimension * 2 ||
        bounds.outHeight / sampleSize > PreviewMaxDimension * 2
    ) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        this,
        0,
        size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return ""
    val scale = minOf(
        1f,
        PreviewMaxDimension.toFloat() / maxOf(decoded.width, decoded.height).toFloat(),
    )
    val width = (decoded.width * scale).toInt().coerceAtLeast(1)
    val height = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (width == decoded.width && height == decoded.height) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, width, height, true)
    }
    val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(scaled, 0f, 0f, null)
    }
    var quality = 78
    var preview = ByteArray(0)
    do {
        val output = ByteArrayOutputStream()
        flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)
        preview = output.toByteArray()
        quality -= 10
    } while (preview.size > PreviewMaxBytes && quality >= 38)

    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    flattened.recycle()
    return if (preview.size <= PreviewMaxBytes) preview.toDataUrl("image/jpeg") else ""
}

private fun ByteArray.isSupportedImage(): Boolean = isJpeg() || isPng() || isGif() || isWebp()

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isPng(): Boolean =
    size >= 8 && this[0] == 0x89.toByte() && this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() && this[3] == 0x47.toByte()

private fun ByteArray.isGif(): Boolean =
    size >= 6 && this[0] == 'G'.code.toByte() && this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte()

private fun ByteArray.isWebp(): Boolean =
    size >= 12 && copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
        copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray())

private fun ByteArray.isPdf(): Boolean =
    size >= 5 && copyOfRange(0, 5).contentEquals("%PDF-".encodeToByteArray())

private fun String.cleanTextForAi(): String = replace("\u0000", "")
    .lines()
    .joinToString("\n") { line -> line.trimEnd() }
    .trim()

private fun rejected(error: AiAttachmentPreparationError, detail: String) =
    AiAttachmentPayloadReadResult.Rejected(error, detail)

private class AttachmentLimitExceededException : IllegalStateException()

private const val PreviewMaxDimension = 384
private const val PreviewMaxBytes = 96 * 1024
private const val DefaultBufferSize = 64 * 1024
private const val PdfMimeType = "application/pdf"
