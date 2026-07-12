package com.changeyourlife.cyl.presentation.utility

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal fun decodeImageBytesToImageBitmap(
    bytes: ByteArray,
    maxDimensionPx: Int,
): ImageBitmap? {
    if (bytes.isEmpty() || maxDimensionPx <= 0) return null

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateBitmapSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            maxDimensionPx = maxDimensionPx,
        )
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

internal fun decodeBase64ImageDataUrlToImageBitmap(
    dataUrl: String,
    maxDimensionPx: Int,
): ImageBitmap? {
    if (dataUrl.isBlank() || !dataUrl.startsWith("data:image/", ignoreCase = true)) return null
    val base64Payload = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        .takeIf { payload -> payload.isNotBlank() }
        ?: return null

    return runCatching {
        decodeImageBytesToImageBitmap(
            bytes = Base64.decode(base64Payload, Base64.DEFAULT),
            maxDimensionPx = maxDimensionPx,
        )
    }.getOrNull()
}

private fun calculateBitmapSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maxDimensionPx: Int,
): Int {
    var sampleSize = 1
    var width = sourceWidth
    var height = sourceHeight
    while (width / 2 >= maxDimensionPx || height / 2 >= maxDimensionPx) {
        width /= 2
        height /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
