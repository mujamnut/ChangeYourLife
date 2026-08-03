package com.changeyourlife.cyl.data.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.changeyourlife.cyl.domain.model.IncomingShareErrorCode
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareLimits
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareIntentParser @Inject constructor() {
    fun parse(intent: Intent): ShareIntentParseResult {
        val action = intent.action.orEmpty()
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return ShareIntentParseResult.Failure(IncomingShareErrorCode.UNSUPPORTED_ACTION)
        }

        val declaredMimeType = intent.type.orEmpty().normalizedMimeType()
        val parsedItems = mutableListOf<ParsedIncomingShareItem>()
        val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.trim().orEmpty()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        when {
            html.isNotBlank() -> parsedItems += ParsedIncomingShareItem(
                kind = IncomingShareItemKind.HTML,
                text = text.takeIf(String::isNotBlank),
                html = html,
                declaredMimeType = "text/html",
            )
            text.isNotBlank() -> parsedItems += ParsedIncomingShareItem(
                kind = if (text.isSingleHttpUrl()) IncomingShareItemKind.URL else IncomingShareItemKind.TEXT,
                text = text,
                declaredMimeType = declaredMimeType.takeIf { it.startsWith("text/") } ?: "text/plain",
            )
        }

        val streamUris = buildList {
            if (action == Intent.ACTION_SEND) {
                intent.parcelableUriExtra(Intent.EXTRA_STREAM)?.let(::add)
            } else {
                addAll(intent.parcelableUriListExtra(Intent.EXTRA_STREAM))
            }
            addAll(intent.clipData.contentUris())
        }.distinctBy(Uri::toString)
        streamUris.forEach { uri ->
            if (uri.scheme.equals("content", ignoreCase = true)) {
                parsedItems += ParsedIncomingShareItem(
                    kind = IncomingShareItemKind.STREAM,
                    sourceUri = uri.toString(),
                    displayName = uri.lastPathSegment.orEmpty().substringAfterLast('/'),
                    declaredMimeType = declaredMimeType,
                )
            }
        }

        val items = parsedItems.distinctBy { item ->
            listOf(item.kind.wireValue, item.sourceUri.orEmpty(), item.text.orEmpty(), item.html.orEmpty())
                .joinToString("\u001f")
        }
        if (items.isEmpty()) return ShareIntentParseResult.Failure(IncomingShareErrorCode.EMPTY_SHARE)
        if (items.size > IncomingShareLimits.MAX_ITEMS) {
            return ShareIntentParseResult.Failure(IncomingShareErrorCode.TOO_MANY_ITEMS)
        }
        val oversizedText = items.any { item ->
            val content = item.html ?: item.text
            content != null && content.toByteArray(Charsets.UTF_8).size > IncomingShareLimits.MAX_TEXT_BYTES
        }
        if (oversizedText) return ShareIntentParseResult.Failure(IncomingShareErrorCode.TEXT_TOO_LARGE)

        return ShareIntentParseResult.Success(
            action = action,
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?.trim()
                ?.take(MaxSubjectChars)
                .orEmpty(),
            items = items,
        )
    }
}

data class ParsedIncomingShareItem(
    val kind: IncomingShareItemKind,
    val sourceUri: String? = null,
    val text: String? = null,
    val html: String? = null,
    val displayName: String = "",
    val declaredMimeType: String = "",
)

sealed interface ShareIntentParseResult {
    data class Success(
        val action: String,
        val subject: String,
        val items: List<ParsedIncomingShareItem>,
    ) : ShareIntentParseResult

    data class Failure(val error: IncomingShareErrorCode) : ShareIntentParseResult
}

private fun Intent.parcelableUriExtra(name: String): Uri? = if (Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(name, Uri::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(name)
}

private fun Intent.parcelableUriListExtra(name: String): List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
    getParcelableArrayListExtra(name, Uri::class.java).orEmpty()
} else {
    @Suppress("DEPRECATION")
    getParcelableArrayListExtra<Uri>(name).orEmpty()
}

private fun ClipData?.contentUris(): List<Uri> = buildList {
    val clip = this@contentUris ?: return@buildList
    repeat(clip.itemCount) { index ->
        clip.getItemAt(index).uri?.let(::add)
    }
}

private fun String.normalizedMimeType(): String = substringBefore(';').trim().lowercase()

private fun String.isSingleHttpUrl(): Boolean {
    if (any(Char::isWhitespace)) return false
    return startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
}

private const val MaxSubjectChars = 200
