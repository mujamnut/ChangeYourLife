package com.changeyourlife.cyl.presentation.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MaxAiChatImages = 4

private const val MaxAiChatImageBytes = 4L * 1024L * 1024L
private const val MaxAiChatTextFileBytes = 256L * 1024L
private const val MaxAiChatPreviewDimension = 384
private const val MaxAiChatPreviewBytes = 96 * 1024

internal data class AiAttachmentReadResult(
    val attachment: AiImageAttachment? = null,
    val userMessage: String? = null,
)

internal fun Context.readAiImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiAttachmentReadResult {
    val rawMimeType = contentResolver.getType(uri).orEmpty()
    val name = queryOpenableName(uri).ifBlank { fallbackName }
    val bytes = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytesWithLimit(maxOf(MaxAiChatImageBytes, MaxAiChatTextFileBytes) + 1)
        }
    }.getOrNull()

    if (bytes == null || bytes.isEmpty()) {
        return AiAttachmentReadResult(userMessage = "Could not read that file.")
    }
    val mimeType = inferAttachmentMimeType(
        rawMimeType = rawMimeType,
        name = name,
        bytes = bytes,
    )

    return when {
        mimeType.startsWith("image/", ignoreCase = true) -> {
            if (bytes.size.toLong() > MaxAiChatImageBytes) {
                AiAttachmentReadResult(userMessage = "Image is too large. Use an image under 4 MB.")
            } else {
                AiAttachmentReadResult(
                    attachment = AiImageAttachment(
                        dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
                        previewDataUrl = bytes.toAiChatPreviewDataUrl(),
                        mimeType = mimeType,
                        name = name,
                        sizeBytes = bytes.size.toLong(),
                        kind = "image",
                    ),
                )
            }
        }
        isReadableTextAttachment(mimeType = mimeType, name = name) -> {
            if (bytes.size.toLong() > MaxAiChatTextFileBytes) {
                AiAttachmentReadResult(userMessage = "Text file is too large. Use a file under 256 KB.")
            } else {
                AiAttachmentReadResult(
                    attachment = AiImageAttachment(
                        textContent = bytes.toString(Charsets.UTF_8).cleanTextFileForAi(),
                        mimeType = mimeType,
                        name = name,
                        sizeBytes = bytes.size.toLong(),
                        kind = "text",
                    ),
                )
            }
        }
        else -> AiAttachmentReadResult(
            userMessage = "This file type is not readable yet. Use image, TXT, CSV, Markdown, or JSON.",
        )
    }
}

internal fun Context.readPastedImageAttachment(
    uri: Uri,
    fallbackName: String,
): AiAttachmentReadResult {
    val result = readAiImageAttachment(uri = uri, fallbackName = fallbackName)
    val attachment = result.attachment ?: return result
    return if (attachment.kind == "image" || attachment.mimeType.startsWith("image/", ignoreCase = true)) {
        AiAttachmentReadResult(attachment = attachment.copy(name = attachment.name.ifBlank { fallbackName }))
    } else {
        AiAttachmentReadResult(userMessage = "Only images can be pasted into the chat composer.")
    }
}

internal fun Bitmap.toAiImageAttachmentResult(
    name: String,
): AiAttachmentReadResult {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 86, output)
    val bytes = output.toByteArray()
    if (bytes.size.toLong() > MaxAiChatImageBytes) {
        return AiAttachmentReadResult(userMessage = "Camera image is too large. Try a smaller image.")
    }
    return AiAttachmentReadResult(
        attachment = AiImageAttachment(
            dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
            previewDataUrl = bytes.toAiChatPreviewDataUrl(),
            mimeType = "image/jpeg",
            name = "$name.jpg",
            sizeBytes = bytes.size.toLong(),
            kind = "image",
        ),
    )
}

private fun inferAttachmentMimeType(
    rawMimeType: String,
    name: String,
    bytes: ByteArray,
): String {
    val normalized = rawMimeType.lowercase().trim()
    if (normalized.startsWith("image/") || normalized.startsWith("text/")) return normalized
    if (normalized.isNotBlank() && normalized != "application/octet-stream") {
        return normalized
    }

    val lowerName = name.lowercase()
    return when {
        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || bytes.isJpeg() -> "image/jpeg"
        lowerName.endsWith(".png") || bytes.isPng() -> "image/png"
        lowerName.endsWith(".webp") || bytes.isWebp() -> "image/webp"
        lowerName.endsWith(".gif") || bytes.isGif() -> "image/gif"
        isReadableTextAttachment(mimeType = normalized, name = name) -> "text/plain"
        else -> normalized.ifBlank { "application/octet-stream" }
    }
}

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isPng(): Boolean =
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte()

private fun ByteArray.isGif(): Boolean =
    size >= 6 &&
        this[0] == 'G'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte()

private fun ByteArray.isWebp(): Boolean =
    size >= 12 &&
        this[0] == 'R'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == 'F'.code.toByte() &&
        this[8] == 'W'.code.toByte() &&
        this[9] == 'E'.code.toByte() &&
        this[10] == 'B'.code.toByte() &&
        this[11] == 'P'.code.toByte()

private fun Context.queryOpenableName(uri: Uri): String {
    return runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            } else {
                ""
            }
        }.orEmpty()
    }.getOrDefault("")
}

private fun ByteArray.toAiChatPreviewDataUrl(): String {
    if (isEmpty()) return ""
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MaxAiChatPreviewDimension * 2 ||
        bounds.outHeight / sampleSize > MaxAiChatPreviewDimension * 2
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
        MaxAiChatPreviewDimension.toFloat() / maxOf(decoded.width, decoded.height).toFloat(),
    )
    val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (targetWidth == decoded.width && targetHeight == decoded.height) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
    }
    val flattened = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(scaled, 0f, 0f, null)
    }

    var quality = 78
    var previewBytes: ByteArray
    do {
        val output = ByteArrayOutputStream()
        flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)
        previewBytes = output.toByteArray()
        quality -= 10
    } while (previewBytes.size > MaxAiChatPreviewBytes && quality >= 38)

    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    flattened.recycle()
    if (previewBytes.size > MaxAiChatPreviewBytes) return ""
    return "data:image/jpeg;base64,${Base64.encodeToString(previewBytes, Base64.NO_WRAP)}"
}

private fun InputStream.readBytesWithLimit(limitBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > limitBytes) {
            return output.toByteArray() + buffer.copyOf(read)
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun isReadableTextAttachment(
    mimeType: String,
    name: String,
): Boolean {
    val lowerMime = mimeType.lowercase()
    val lowerName = name.lowercase()
    return lowerMime.startsWith("text/") ||
        lowerMime in setOf(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/csv",
            "application/sql",
            "application/javascript",
        ) ||
        lowerName.endsWith(".txt") ||
        lowerName.endsWith(".md") ||
        lowerName.endsWith(".markdown") ||
        lowerName.endsWith(".csv") ||
        lowerName.endsWith(".tsv") ||
        lowerName.endsWith(".json") ||
        lowerName.endsWith(".xml") ||
        lowerName.endsWith(".yaml") ||
        lowerName.endsWith(".yml") ||
        lowerName.endsWith(".sql") ||
        lowerName.endsWith(".log")
}

private fun String.cleanTextFileForAi(): String {
    return replace("\u0000", "")
        .lines()
        .joinToString("\n") { line -> line.trimEnd() }
        .trim()
}
