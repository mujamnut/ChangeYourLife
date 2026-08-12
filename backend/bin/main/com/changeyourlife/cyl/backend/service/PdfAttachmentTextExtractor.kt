package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_PDF_BYTES
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.backend.config.AiTimeoutConfig
import com.changeyourlife.cyl.backend.model.ai.AiImageInput
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PdfAttachmentTextExtractor {
    /**
     * Compatibility entry point for callers that only need selectable PDF text.
     * New attachment routing should pass the request deadline and an explicit OCR
     * render budget to [extract].
     */
    fun extract(attachments: List<AiImageInput>): PdfAttachmentBatchResult = extract(
        attachments = attachments,
        deadline = AiRequestDeadline.start(AiTimeoutConfig()),
        maxRenderedPages = 0,
    )

    internal fun extract(
        attachments: List<AiImageInput>,
        deadline: AiRequestDeadline,
        maxRenderedPages: Int,
    ): PdfAttachmentBatchResult {
        val allPdfs = attachments.filter { attachment ->
            attachment.attachmentKind == ChatAttachmentKind.Pdf
        }
        if (allPdfs.isEmpty()) return PdfAttachmentBatchResult(status = PdfExtractionNotAttempted)
        deadline.checkpoint("PDF attachment selection")

        val future = try {
            PdfProcessingExecutor.submit<PdfAttachmentBatchResult> {
                extractOnWorker(
                    allPdfs = allPdfs,
                    deadline = deadline,
                    maxRenderedPages = maxRenderedPages,
                )
            }
        } catch (_: RejectedExecutionException) {
            return PdfAttachmentBatchResult(
                fileCount = allPdfs.size,
                omittedFileCount = allPdfs.size,
                truncated = true,
                status = PdfExtractionFailed,
                warning = PdfProcessingBusyWarning,
            )
        }

        val availableDeadlineMillis = deadline.availableRemoteWorkMillis()
        if (availableDeadlineMillis <= 0L) {
            future.cancel(true)
            throw AiDeadlineExceededException("AI job deadline exhausted before PDF processing could start.")
        }
        val waitMillis = min(availableDeadlineMillis, MaxPdfProcessingMillis)
        val waitIsDeadlineBound = availableDeadlineMillis <= MaxPdfProcessingMillis
        return try {
            future.get(waitMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            if (waitIsDeadlineBound) {
                throw AiDeadlineExceededException("AI job deadline exhausted during PDF processing.")
            }
            PdfAttachmentBatchResult(
                fileCount = allPdfs.size,
                omittedFileCount = allPdfs.size,
                truncated = true,
                status = PdfExtractionFailed,
                warning = PdfProcessingTimeoutWarning,
            )
        } catch (error: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw error
        } catch (error: CancellationException) {
            future.cancel(true)
            throw error
        } catch (error: ExecutionException) {
            val cause = error.cause ?: error
            cause.rethrowIfAiWorkMustStop()
            when (cause) {
                is Exception -> throw cause
                is Error -> throw cause
                else -> throw error
            }
        }
    }

    private fun extractOnWorker(
        allPdfs: List<AiImageInput>,
        deadline: AiRequestDeadline,
        maxRenderedPages: Int,
    ): PdfAttachmentBatchResult {
        deadline.checkpoint("PDF processing worker")

        val selectedPdfs = allPdfs.take(MaxPdfFiles)
        val omittedFileCount = (allPdfs.size - selectedPdfs.size).coerceAtLeast(0)
        val renderBudget = maxRenderedPages.coerceIn(0, MaxPdfFiles * MaxRenderedPagesPerFile)
        var remainingRenderPages = renderBudget
        var decodedBatchBytes = 0L
        val documents = ArrayList<PdfAttachmentDocumentResult>(selectedPdfs.size)

        selectedPdfs.forEachIndexed { fileIndex, attachment ->
            deadline.checkpoint("PDF payload validation")
            val safeName = attachment.name.toSafePdfName()
            val decoded = attachment.decodePdfPayload()
            val documentResult = when {
                decoded == null -> failed(safeName, PdfInvalidPayloadWarning)
                decodedBatchBytes + decoded.size > MaxPdfBatchBytes ->
                    failed(safeName, PdfAggregateLimitWarning)
                else -> {
                    decodedBatchBytes += decoded.size
                    extractOne(
                        attachment = attachment,
                        safeName = safeName,
                        fileOrdinal = fileIndex + 1,
                        bytes = decoded,
                        deadline = deadline,
                        maxRenderedPages = min(remainingRenderPages, MaxRenderedPagesPerFile),
                    )
                }
            }
            documents += documentResult
            remainingRenderPages = (remainingRenderPages - documentResult.renderedPages.size).coerceAtLeast(0)
            deadline.checkpoint("PDF attachment processing")
        }

        val renderedPages = documents.flatMap(PdfAttachmentDocumentResult::renderedPages)
        val ocrCandidatePageCount = documents.sumOf(PdfAttachmentDocumentResult::ocrCandidatePageCount)
        val omittedOcrPageCount = documents.sumOf(PdfAttachmentDocumentResult::omittedOcrPageCount)
        val rawContent = documents
            .filter { document -> document.text.isNotBlank() }
            .joinToString(separator = "\n\n") { document ->
                buildString {
                    append("PDF: ")
                    append(document.name)
                    append("\nPages inspected: ")
                    append(document.pagesInspected)
                    append('/')
                    append(document.totalPages)
                    append("\nSelectable text:\n")
                    append(document.text)
                }
            }
        val contentTruncated = rawContent.length > MaxPdfBatchContextChars
        val truncated = omittedFileCount > 0 ||
            contentTruncated ||
            documents.any(PdfAttachmentDocumentResult::truncated)
        val warnings = buildList {
            if (omittedFileCount > 0) {
                add("$omittedFileCount PDF file(s) were omitted by the $MaxPdfFiles-file processing limit.")
            }
            documents.flatMapTo(this) { document -> document.warnings }
            if (renderedPages.isNotEmpty()) {
                add("${renderedPages.size} PDF page(s) were prepared for OCR.")
            }
            if (omittedOcrPageCount > 0) {
                add("$omittedOcrPageCount PDF page(s) requiring OCR were omitted or could not be rendered safely.")
            }
            if (contentTruncated) {
                add("Selectable PDF text was truncated to the safe context limit.")
            }
        }.distinct()

        val result = PdfAttachmentBatchResult(
            content = rawContent.take(MaxPdfBatchContextChars),
            renderedPages = renderedPages,
            fileCount = allPdfs.size,
            processedFileCount = documents.size,
            omittedFileCount = omittedFileCount,
            pageCount = documents.sumOf(PdfAttachmentDocumentResult::pagesInspected),
            totalPageCount = documents.sumOf(PdfAttachmentDocumentResult::totalPages),
            ocrCandidatePageCount = ocrCandidatePageCount,
            renderedPageCount = renderedPages.size,
            omittedOcrPageCount = omittedOcrPageCount,
            truncated = truncated,
            status = batchStatus(
                documents = documents,
                omittedFileCount = omittedFileCount,
                contentTruncated = contentTruncated,
                ocrCandidatePageCount = ocrCandidatePageCount,
            ),
            warning = warnings.joinToString(" | ").take(MaxPdfWarningChars),
        )
        deadline.checkpoint("PDF processing completion")
        return result
    }

    private fun extractOne(
        attachment: AiImageInput,
        safeName: String,
        fileOrdinal: Int,
        bytes: ByteArray,
        deadline: AiRequestDeadline,
        maxRenderedPages: Int,
    ): PdfAttachmentDocumentResult {
        val pageText = ArrayList<String>()
        val renderedPages = ArrayList<AiImageInput>()
        val warnings = linkedSetOf<String>()
        var pagesInspected = 0
        var totalPages = 0
        var ocrCandidatePageCount = 0
        var omittedOcrPageCount = 0
        var fileTextChars = 0
        var truncated = false

        try {
            deadline.checkpoint("PDF parse")
            Loader.loadPDF(bytes, "", null, null, PdfMemorySettings.streamCache).use { document ->
                deadline.checkpoint("PDF parse")
                if (document.isEncrypted || !document.currentAccessPermission.canExtractContent()) {
                    return failed(safeName, PdfProcessingFailedWarning)
                }

                totalPages = document.numberOfPages.coerceAtLeast(0)
                if (totalPages == 0) {
                    return failed(safeName, PdfProcessingFailedWarning)
                }
                val pagesToInspect = min(totalPages, MaxPdfPagesPerFile)
                if (totalPages > pagesToInspect) {
                    truncated = true
                    warnings += "$safeName was limited to the first $MaxPdfPagesPerFile pages."
                }

                val renderer = PDFRenderer(document).apply { isSubsamplingAllowed = true }
                for (pageIndex in 0 until pagesToInspect) {
                    val pageNumber = pageIndex + 1
                    deadline.checkpoint("PDF page $pageNumber extraction")
                    pagesInspected++
                    val cleanedText = extractPageText(document, pageNumber, deadline)
                    if (cleanedText == null) {
                        warnings += PdfPageReadWarning
                    } else if (cleanedText.isNotBlank()) {
                        val remainingChars = (MaxPdfTextCharsPerFile - fileTextChars).coerceAtLeast(0)
                        val boundedText = cleanedText.take(min(MaxPdfTextCharsPerPage, remainingChars))
                        if (boundedText.isNotBlank()) {
                            pageText += "Page $pageNumber:\n$boundedText"
                            fileTextChars += boundedText.length
                        }
                        if (
                            cleanedText.length > boundedText.length ||
                            cleanedText.length > MaxPdfTextCharsPerPage
                        ) {
                            truncated = true
                            warnings += "$safeName selectable text was truncated to the safe extraction limit."
                        }
                    }

                    val printableChars = cleanedText?.countPrintableCharacters() ?: 0
                    if (printableChars < MinSelectablePrintableChars) {
                        ocrCandidatePageCount++
                        val canRender = renderedPages.size < maxRenderedPages &&
                            renderedPages.size < MaxRenderedPagesPerFile
                        if (!canRender) {
                            omittedOcrPageCount++
                            truncated = true
                        } else {
                            val rendered = renderPageForOcr(
                                renderer = renderer,
                                page = document.getPage(pageIndex),
                                pageIndex = pageIndex,
                                pageNumber = pageNumber,
                                fileOrdinal = fileOrdinal,
                                safeName = safeName,
                                source = attachment,
                                deadline = deadline,
                            )
                            if (rendered == null) {
                                omittedOcrPageCount++
                                truncated = true
                                warnings += PdfPageRenderWarning
                            } else {
                                renderedPages += rendered
                            }
                        }
                    }
                    deadline.checkpoint("PDF page $pageNumber processing")
                }
            }
        } catch (error: Exception) {
            error.rethrowIfAiWorkMustStop()
            warnings += PdfProcessingFailedWarning
            return PdfAttachmentDocumentResult(
                name = safeName,
                text = pageText.joinToString("\n\n"),
                pagesInspected = pagesInspected,
                totalPages = totalPages,
                renderedPages = renderedPages,
                ocrCandidatePageCount = ocrCandidatePageCount,
                omittedOcrPageCount = omittedOcrPageCount,
                failed = pageText.isEmpty() && renderedPages.isEmpty(),
                partial = true,
                truncated = truncated,
                warnings = warnings.toList(),
            )
        }

        return PdfAttachmentDocumentResult(
            name = safeName,
            text = pageText.joinToString("\n\n"),
            pagesInspected = pagesInspected,
            totalPages = totalPages,
            renderedPages = renderedPages,
            ocrCandidatePageCount = ocrCandidatePageCount,
            omittedOcrPageCount = omittedOcrPageCount,
            failed = false,
            partial = ocrCandidatePageCount > 0 || warnings.isNotEmpty() || truncated,
            truncated = truncated,
            warnings = warnings.toList(),
        )
    }

    private fun extractPageText(
        document: PDDocument,
        pageNumber: Int,
        deadline: AiRequestDeadline,
    ): String? = try {
        val rawText = PDFTextStripper().apply {
            startPage = pageNumber
            endPage = pageNumber
            sortByPosition = true
        }.getText(document)
        deadline.checkpoint("PDF page $pageNumber extraction")
        rawText.cleanPdfText()
    } catch (error: Exception) {
        error.rethrowIfAiWorkMustStop()
        null
    }

    private fun renderPageForOcr(
        renderer: PDFRenderer,
        page: PDPage,
        pageIndex: Int,
        pageNumber: Int,
        fileOrdinal: Int,
        safeName: String,
        source: AiImageInput,
        deadline: AiRequestDeadline,
    ): AiImageInput? {
        val renderScale = page.safeRenderScale() ?: return null
        var renderedImage: BufferedImage? = null
        return try {
            deadline.checkpoint("PDF page $pageNumber render")
            val originalImage = renderer.renderImage(pageIndex, renderScale, ImageType.RGB)
            renderedImage = originalImage
            deadline.checkpoint("PDF page $pageNumber render")
            val boundedImage = originalImage.boundedToRenderLimits()
            if (boundedImage !== originalImage) originalImage.flush()
            renderedImage = boundedImage
            val jpeg = boundedImage.encodeBoundedJpeg() ?: return null
            deadline.checkpoint("PDF page $pageNumber JPEG encoding")
            AiImageInput(
                dataUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(jpeg)}",
                mimeType = "image/jpeg",
                name = "$safeName - page $pageNumber.jpg",
                sizeBytes = jpeg.size.toLong(),
                kind = ChatAttachmentKind.Image.wireValue,
                source = PdfRenderSource,
                sourceReferenceId = "pdf:$fileOrdinal:page:$pageNumber",
                approvedAtEpochMillis = source.approvedAtEpochMillis,
            )
        } catch (error: Exception) {
            error.rethrowIfAiWorkMustStop()
            null
        } finally {
            renderedImage?.flush()
        }
    }

    private fun failed(name: String, warning: String) = PdfAttachmentDocumentResult(
        name = name,
        text = "",
        pagesInspected = 0,
        totalPages = 0,
        renderedPages = emptyList(),
        ocrCandidatePageCount = 0,
        omittedOcrPageCount = 0,
        failed = true,
        partial = false,
        truncated = false,
        warnings = listOf(warning),
    )
}

data class PdfAttachmentBatchResult(
    val content: String = "",
    val renderedPages: List<AiImageInput> = emptyList(),
    val fileCount: Int = 0,
    val processedFileCount: Int = 0,
    val omittedFileCount: Int = 0,
    val pageCount: Int = 0,
    val totalPageCount: Int = 0,
    val ocrCandidatePageCount: Int = 0,
    val renderedPageCount: Int = 0,
    val omittedOcrPageCount: Int = 0,
    val truncated: Boolean = false,
    val status: String = PdfExtractionNotAttempted,
    val warning: String = "",
)

private data class PdfAttachmentDocumentResult(
    val name: String,
    val text: String,
    val pagesInspected: Int,
    val totalPages: Int,
    val renderedPages: List<AiImageInput>,
    val ocrCandidatePageCount: Int,
    val omittedOcrPageCount: Int,
    val failed: Boolean,
    val partial: Boolean,
    val truncated: Boolean,
    val warnings: List<String>,
)

private fun batchStatus(
    documents: List<PdfAttachmentDocumentResult>,
    omittedFileCount: Int,
    contentTruncated: Boolean,
    ocrCandidatePageCount: Int,
): String {
    if (documents.isEmpty()) return PdfExtractionNotAttempted
    if (omittedFileCount > 0) return PdfExtractionPartial
    if (documents.all(PdfAttachmentDocumentResult::failed)) return PdfExtractionFailed
    if (
        contentTruncated ||
        ocrCandidatePageCount > 0 ||
        documents.any { document -> document.failed || document.partial }
    ) {
        return PdfExtractionPartial
    }
    return if (documents.any { document -> document.text.isNotBlank() }) {
        PdfExtractionSucceeded
    } else {
        PdfExtractionNoText
    }
}

private fun AiImageInput.decodePdfPayload(): ByteArray? {
    if (sizeBytes > CYL_MAX_AI_PDF_BYTES) return null
    val encoded = dataUrl.extractPdfBase64() ?: return null
    if (encoded.length > MaxEncodedPdfChars) return null
    val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
    return bytes.takeIf { decoded ->
        decoded.isNotEmpty() &&
            decoded.size.toLong() <= CYL_MAX_AI_PDF_BYTES &&
            decoded.startsWithPdfHeader()
    }
}

private fun String.extractPdfBase64(): String? {
    val markerIndex = indexOf(PdfDataUrlMarker, ignoreCase = true)
    if (!startsWith(PdfDataUrlPrefix, ignoreCase = true) || markerIndex < 0) return null
    return substring(markerIndex + PdfDataUrlMarker.length).takeIf(String::isNotBlank)
}

private fun ByteArray.startsWithPdfHeader(): Boolean =
    size >= PdfHeader.size && copyOfRange(0, PdfHeader.size).contentEquals(PdfHeader)

private fun String.toSafePdfName(): String {
    val safe = asSequence()
        .filterNot { character ->
            character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()
        }
        .joinToString("")
        .replace(FileNameSeparators, "_")
        .replace(RepeatedWhitespace, " ")
        .trim()
        .take(MaxPdfNameChars)
    return safe.ifBlank { "attached PDF" }
}

private fun String.cleanPdfText(): String = asSequence()
    .filterNot { character ->
        (character.isISOControl() && character != '\n' && character != '\r' && character != '\t') ||
            Character.getType(character) == Character.FORMAT.toInt()
    }
    .joinToString("")
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace('\t', ' ')
    .lines()
    .joinToString("\n") { line -> line.trimEnd() }
    .replace(ExcessBlankLines, "\n\n")
    .trim()

private fun String.countPrintableCharacters(): Int = count { character ->
    !character.isWhitespace() &&
        !character.isISOControl() &&
        Character.getType(character) != Character.FORMAT.toInt()
}

private fun PDPage.safeRenderScale(): Float? {
    val box = cropBox ?: return null
    val coordinates = listOf(box.lowerLeftX, box.lowerLeftY, box.upperRightX, box.upperRightY)
    val width = box.width
    val height = box.height
    if (coordinates.any { coordinate -> !coordinate.isFinite() }) return null
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    val shorterEdge = min(width, height)
    val longerEdge = max(width, height)
    if (longerEdge / shorterEdge > MaxPdfPageAspectRatio) return null

    val dimensionScale = MaxRenderedPageDimension.toDouble() / longerEdge.toDouble()
    val pixelScale = sqrt(MaxRenderedPagePixels.toDouble() / (width.toDouble() * height.toDouble()))
    return min(MinOfRenderScales, min(dimensionScale, pixelScale))
        .times(RenderScaleSafetyMargin)
        .toFloat()
        .takeIf { scale -> scale.isFinite() && scale > 0f }
}

private fun BufferedImage.boundedToRenderLimits(): BufferedImage {
    val longestEdge = max(width, height)
    val pixels = width.toLong() * height.toLong()
    if (longestEdge <= MaxRenderedPageDimension && pixels <= MaxRenderedPagePixels) return this
    val dimensionScale = MaxRenderedPageDimension.toDouble() / longestEdge.toDouble()
    val pixelScale = sqrt(MaxRenderedPagePixels.toDouble() / pixels.toDouble())
    val scale = min(dimensionScale, pixelScale).coerceAtMost(1.0) * RenderScaleSafetyMargin
    val targetWidth = max(1, floor(width * scale).toInt())
    val targetHeight = max(1, floor(height * scale).toInt())
    return resizeRgb(targetWidth, targetHeight)
}

private fun BufferedImage.encodeBoundedJpeg(): ByteArray? {
    var working = this
    var ownsWorking = false
    var quality = InitialJpegQuality
    return try {
        repeat(MaxJpegEncodingAttempts) {
            val encoded = working.encodeJpeg(quality) ?: return null
            if (encoded.size <= MaxRenderedPageJpegBytes) return encoded

            val byteRatio = sqrt(MaxRenderedPageJpegBytes.toDouble() / encoded.size.toDouble())
            val resizeRatio = min(MaxJpegResizeStep, byteRatio * JpegResizeSafetyMargin)
                .coerceAtLeast(MinJpegResizeStep)
            val targetWidth = max(1, floor(working.width * resizeRatio).toInt())
            val targetHeight = max(1, floor(working.height * resizeRatio).toInt())
            if (targetWidth == working.width && targetHeight == working.height) return null
            val resized = working.resizeRgb(targetWidth, targetHeight)
            if (ownsWorking) working.flush()
            working = resized
            ownsWorking = true
            quality = (quality - JpegQualityStep).coerceAtLeast(MinJpegQuality)
        }
        null
    } finally {
        if (ownsWorking) working.flush()
    }
}

private fun BufferedImage.resizeRgb(targetWidth: Int, targetHeight: Int): BufferedImage {
    val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = resized.createGraphics()
    try {
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, targetWidth, targetHeight)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return resized
}

private fun BufferedImage.encodeJpeg(quality: Float): ByteArray? {
    val writer = ImageIO.getImageWritersByFormatName("jpg").asSequence().firstOrNull() ?: return null
    val output = ByteArrayOutputStream()
    val imageOutput = ImageIO.createImageOutputStream(output) ?: run {
        writer.dispose()
        return null
    }
    return try {
        writer.output = imageOutput
        val params = writer.defaultWriteParam
        if (params.canWriteCompressed()) {
            params.compressionMode = ImageWriteParam.MODE_EXPLICIT
            params.compressionQuality = quality
        }
        writer.write(null, IIOImage(this, null, null), params)
        imageOutput.flush()
        output.toByteArray()
    } finally {
        imageOutput.close()
        writer.dispose()
        output.close()
    }
}

private const val PdfDataUrlPrefix = "data:application/pdf"
private const val PdfDataUrlMarker = ";base64,"
private val PdfHeader = "%PDF-".encodeToByteArray()
private const val MaxPdfFiles = 3
private const val MaxPdfPagesPerFile = 40
private const val MaxRenderedPagesPerFile = 3
private const val MaxPdfBatchBytes = 24L * 1024L * 1024L
private const val MaxPdfTextCharsPerPage = 20_000
private const val MaxPdfTextCharsPerFile = 60_000
private const val MaxPdfBatchContextChars = 100_000
private const val MaxPdfWarningChars = 1_000
private const val MaxPdfProcessingMillis = 25_000L
private const val MaxPdfNameChars = 120
private const val MaxEncodedPdfChars = 11_184_812
private const val MinSelectablePrintableChars = 40
private const val MaxPdfPageAspectRatio = 20.0f
private const val MaxRenderedPageDimension = 1_280
private const val MaxRenderedPagePixels = 2_000_000L
private const val MaxRenderedPageJpegBytes = 512 * 1024
private const val MinOfRenderScales = 4.0
private const val RenderScaleSafetyMargin = 0.995
private const val InitialJpegQuality = 0.82f
private const val MinJpegQuality = 0.32f
private const val JpegQualityStep = 0.08f
private const val MaxJpegResizeStep = 0.82
private const val MinJpegResizeStep = 0.20
private const val JpegResizeSafetyMargin = 0.92
private const val MaxJpegEncodingAttempts = 10
private const val PdfRenderSource = "pdf_render"
private const val PdfExtractionNotAttempted = "not_attempted"
private const val PdfExtractionSucceeded = "succeeded"
private const val PdfExtractionPartial = "partial"
private const val PdfExtractionNoText = "no_text"
private const val PdfExtractionFailed = "failed"
private const val PdfInvalidPayloadWarning = "PDF payload is invalid or too large."
private const val PdfAggregateLimitWarning = "PDF attachments exceed the aggregate processing limit."
private const val PdfProcessingFailedWarning = "PDF processing failed."
private const val PdfPageReadWarning = "Some PDF pages could not be read."
private const val PdfPageRenderWarning = "Some PDF pages could not be rendered safely for OCR."
private const val PdfProcessingBusyWarning = "PDF processing is temporarily unavailable."
private const val PdfProcessingTimeoutWarning = "PDF processing exceeded the safe time limit."
private val PdfMemorySettings = MemoryUsageSetting.setupMixed(8L * 1024L * 1024L, 64L * 1024L * 1024L)
private val PdfProcessingExecutor = ThreadPoolExecutor(
    1,
    1,
    30L,
    TimeUnit.SECONDS,
    ArrayBlockingQueue(2),
    { task ->
        Thread(task, "cyl-pdf-processor").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    },
    ThreadPoolExecutor.AbortPolicy(),
).apply {
    allowCoreThreadTimeOut(true)
}
private val ExcessBlankLines = Regex("\n{3,}")
private val RepeatedWhitespace = Regex("\\s+")
private val FileNameSeparators = Regex("[/\\\\]+")
