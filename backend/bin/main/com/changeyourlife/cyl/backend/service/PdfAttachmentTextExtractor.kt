package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_PDF_BYTES
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.backend.model.ai.AiImageInput
import java.util.Base64
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfAttachmentTextExtractor {
    fun extract(attachments: List<AiImageInput>): PdfAttachmentBatchResult {
        val pdfs = attachments
            .asSequence()
            .filter { attachment -> attachment.attachmentKind == ChatAttachmentKind.Pdf }
            .take(MaxPdfFiles)
            .toList()
        if (pdfs.isEmpty()) return PdfAttachmentBatchResult(status = PdfExtractionNotAttempted)

        val documents = pdfs.map(::extractOne)
        val readable = documents.filter { document -> document.text.isNotBlank() }
        val status = when {
            readable.isEmpty() && documents.any { it.status == PdfExtractionFailed } -> PdfExtractionFailed
            readable.isEmpty() -> PdfExtractionNoText
            documents.any { it.status != PdfExtractionSucceeded } -> PdfExtractionPartial
            else -> PdfExtractionSucceeded
        }
        return PdfAttachmentBatchResult(
            content = readable.joinToString(separator = "\n\n") { document ->
                buildString {
                    append("PDF: ")
                    append(document.name)
                    append("\nPages read: ")
                    append(document.pagesRead)
                    append('/')
                    append(document.totalPages)
                    append("\nExtracted text:\n")
                    append(document.text)
                }
            }.take(MaxPdfBatchContextChars),
            fileCount = pdfs.size,
            pageCount = documents.sumOf(PdfAttachmentDocumentResult::pagesRead),
            status = status,
            warning = documents
                .mapNotNull(PdfAttachmentDocumentResult::warning)
                .distinct()
                .joinToString(" | ")
                .take(MaxPdfWarningChars),
        )
    }

    private fun extractOne(attachment: AiImageInput): PdfAttachmentDocumentResult {
        val safeName = attachment.name.trim().take(MaxPdfNameChars).ifBlank { "attached PDF" }
        val encoded = attachment.dataUrl.extractPdfBase64()
            ?: return failed(safeName, "PDF payload is missing or malformed.")
        if (encoded.length > MaxEncodedPdfChars) {
            return failed(safeName, "PDF exceeds the AI extraction limit.")
        }
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: return failed(safeName, "PDF payload is not valid base64.")
        if (
            bytes.isEmpty() ||
            bytes.size.toLong() > CYL_MAX_AI_PDF_BYTES ||
            !bytes.startsWithPdfHeader()
        ) {
            return failed(safeName, "PDF payload is invalid or too large.")
        }

        return try {
            Loader.loadPDF(bytes).use { document ->
                val totalPages = document.numberOfPages.coerceAtLeast(0)
                if (totalPages == 0) return@use failed(safeName, "PDF has no pages.")
                val pagesRead = minOf(totalPages, MaxPdfPagesPerFile)
                val rawText = PDFTextStripper().apply {
                    startPage = 1
                    endPage = pagesRead
                    sortByPosition = true
                }.getText(document)
                val cleaned = rawText.cleanPdfText().take(MaxPdfTextCharsPerFile)
                when {
                    cleaned.isBlank() -> PdfAttachmentDocumentResult(
                        name = safeName,
                        text = "",
                        pagesRead = pagesRead,
                        totalPages = totalPages,
                        status = PdfExtractionNoText,
                        warning = "No selectable text was found in $safeName; OCR was not attempted.",
                    )
                    totalPages > pagesRead || rawText.length > MaxPdfTextCharsPerFile ->
                        PdfAttachmentDocumentResult(
                            name = safeName,
                            text = cleaned,
                            pagesRead = pagesRead,
                            totalPages = totalPages,
                            status = PdfExtractionPartial,
                            warning = "$safeName was truncated to the safe extraction limit.",
                        )
                    else -> PdfAttachmentDocumentResult(
                        name = safeName,
                        text = cleaned,
                        pagesRead = pagesRead,
                        totalPages = totalPages,
                        status = PdfExtractionSucceeded,
                    )
                }
            }
        } catch (_: Exception) {
            failed(safeName, "PDF text extraction failed.")
        }
    }

    private fun failed(name: String, warning: String) = PdfAttachmentDocumentResult(
        name = name,
        text = "",
        pagesRead = 0,
        totalPages = 0,
        status = PdfExtractionFailed,
        warning = warning,
    )
}

data class PdfAttachmentBatchResult(
    val content: String = "",
    val fileCount: Int = 0,
    val pageCount: Int = 0,
    val status: String = PdfExtractionNotAttempted,
    val warning: String = "",
)

private data class PdfAttachmentDocumentResult(
    val name: String,
    val text: String,
    val pagesRead: Int,
    val totalPages: Int,
    val status: String,
    val warning: String? = null,
)

private fun String.extractPdfBase64(): String? {
    val markerIndex = indexOf(PdfDataUrlMarker, ignoreCase = true)
    if (!startsWith(PdfDataUrlPrefix, ignoreCase = true) || markerIndex < 0) return null
    return substring(markerIndex + PdfDataUrlMarker.length).takeIf(String::isNotBlank)
}

private fun ByteArray.startsWithPdfHeader(): Boolean =
    size >= 5 && copyOfRange(0, 5).contentEquals("%PDF-".encodeToByteArray())

private fun String.cleanPdfText(): String = replace("\u0000", "")
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .lines()
    .joinToString("\n") { line -> line.trimEnd() }
    .replace(ExcessBlankLines, "\n\n")
    .trim()

private const val PdfDataUrlPrefix = "data:application/pdf"
private const val PdfDataUrlMarker = ";base64,"
private const val MaxPdfFiles = 3
private const val MaxPdfPagesPerFile = 40
private const val MaxPdfTextCharsPerFile = 60_000
private const val MaxPdfBatchContextChars = 100_000
private const val MaxPdfWarningChars = 500
private const val MaxPdfNameChars = 120
private const val MaxEncodedPdfChars = 11_184_812
private const val PdfExtractionNotAttempted = "not_attempted"
private const val PdfExtractionSucceeded = "succeeded"
private const val PdfExtractionPartial = "partial"
private const val PdfExtractionNoText = "no_text"
private const val PdfExtractionFailed = "failed"
private val ExcessBlankLines = Regex("\n{3,}")
