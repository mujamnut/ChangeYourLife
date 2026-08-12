package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.AiAttachmentInputWire
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.backend.config.AiTimeoutConfig
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject

class PdfAttachmentTextExtractorTest {
    private val extractor = PdfAttachmentTextExtractor()

    @Test
    fun selectablePdfUsesLocalTextAndDoesNotRenderAnyPage() {
        val localToken = "LOCAL_SELECTABLE_TOKEN_8472"
        val result = extract(
            attachments = listOf(pdfAttachment(selectablePdf(localToken), "selectable.pdf")),
            maxRenderedPages = 4,
        )

        assertEquals("succeeded", result.status)
        assertEquals(1, result.fileCount)
        assertEquals(1, result.pageCount)
        assertContains(result.content, localToken)
        assertTrue(result.renderedPages.isEmpty())
        assertEquals(0, result.ocrCandidatePageCount)
        assertEquals(0, result.renderedPageCount)
        assertEquals(0, result.omittedOcrPageCount)
        assertFalse(result.truncated)
    }

    @Test
    fun scannedPdfProducesOneBoundedAuditableImageWithoutPdfBytes() {
        val sourceBytes = scannedPdf(pageCount = 1)
        val source = pdfAttachment(sourceBytes, "scanned receipt.pdf")
        val result = extract(listOf(source), maxRenderedPages = 2)

        assertEquals("partial", result.status)
        assertEquals(1, result.fileCount)
        assertEquals(1, result.pageCount)
        assertEquals(1, result.ocrCandidatePageCount)
        assertEquals(1, result.renderedPageCount)
        assertEquals(0, result.omittedOcrPageCount)
        assertEquals(1, result.renderedPages.size)

        val rendered = result.renderedPages.single()
        assertEquals(ChatAttachmentKind.Image, rendered.attachmentKind)
        assertEquals("image/jpeg", rendered.mimeType)
        assertContains(rendered.name, "scanned receipt.pdf - page 1")
        assertEquals("pdf_render", rendered.source)
        assertEquals("pdf:1:page:1", rendered.sourceReferenceId)
        assertEquals(source.approvedAtEpochMillis, rendered.approvedAtEpochMillis)
        assertTrue(rendered.validate().isEmpty(), rendered.validate().joinToString())
        assertTrue(rendered.dataUrl.startsWith("data:image/jpeg;base64,"))

        val renderedBytes = rendered.dataUrl.decodeDataUrl()
        assertEquals(renderedBytes.size.toLong(), rendered.sizeBytes)
        assertTrue(renderedBytes.size <= MaxRenderedPageBytes)
        assertFalse(renderedBytes.startsWithPdfHeader())
        assertFalse(rendered.dataUrl.contains(Base64.getEncoder().encodeToString(sourceBytes)))
    }

    @Test
    fun mixedPdfRetainsLocalTextAndRendersOnlyThePageWithoutSelectableText() {
        val localToken = "MIXED_LOCAL_TOKEN_3819"
        val result = extract(
            attachments = listOf(
                pdfAttachment(
                    pdf(PageFixture.Selectable(localToken), PageFixture.Scanned),
                    "mixed.pdf",
                ),
            ),
            maxRenderedPages = 4,
        )

        assertEquals("partial", result.status)
        assertEquals(2, result.pageCount)
        assertContains(result.content, localToken)
        assertEquals(1, result.ocrCandidatePageCount)
        assertEquals(1, result.renderedPageCount)
        assertEquals(0, result.omittedOcrPageCount)
        assertContains(result.renderedPages.single().name, "mixed.pdf - page 2")
        assertEquals("pdf:1:page:2", result.renderedPages.single().sourceReferenceId)
    }

    @Test
    fun encryptedAndCorruptPdfsFailWithTheSameGenericWarning() {
        val encrypted = extract(
            listOf(pdfAttachment(encryptedPdf("do-not-leak-password"), "locked.pdf")),
            maxRenderedPages = 2,
        )
        val corrupt = extract(
            listOf(pdfAttachment("%PDF-not-a-real-document".encodeToByteArray(), "broken.pdf")),
            maxRenderedPages = 2,
        )

        listOf(encrypted, corrupt).forEach { result ->
            assertEquals("failed", result.status)
            assertEquals("", result.content)
            assertTrue(result.renderedPages.isEmpty())
            assertEquals(0, result.renderedPageCount)
            assertFalse(result.warning.contains("password", ignoreCase = true))
            assertFalse(result.warning.contains("exception", ignoreCase = true))
        }
        assertEquals("PDF processing failed.", encrypted.warning)
        assertEquals(encrypted.warning, corrupt.warning)
    }

    @Test
    fun globalRenderCapReturnsPartialResultAndCountsEveryOcrCandidate() {
        val result = extract(
            attachments = listOf(pdfAttachment(scannedPdf(pageCount = 3), "three-scans.pdf")),
            maxRenderedPages = 1,
        )

        assertEquals("partial", result.status)
        assertEquals(3, result.pageCount)
        assertEquals(3, result.ocrCandidatePageCount)
        assertEquals(1, result.renderedPageCount)
        assertEquals(2, result.omittedOcrPageCount)
        assertEquals(1, result.renderedPages.size)
        assertTrue(result.truncated)
        assertTrue(result.warning.isNotBlank())
        assertContains(result.renderedPages.single().name, "page 1")
    }

    @Test
    fun fourthPdfIsExplicitlyOmittedAtTheFileLimit() {
        val attachments = (1..4).map { ordinal ->
            pdfAttachment(
                bytes = selectablePdf("PDF_FILE_TOKEN_$ordinal"),
                name = "file-$ordinal.pdf",
                sourceReferenceId = "fixture:file-$ordinal",
            )
        }

        val result = extract(attachments, maxRenderedPages = 4)

        assertEquals("partial", result.status)
        assertEquals(4, result.fileCount)
        assertEquals(3, result.processedFileCount)
        assertEquals(1, result.omittedFileCount)
        assertEquals(3, result.pageCount)
        assertContains(result.content, "PDF_FILE_TOKEN_1")
        assertContains(result.content, "PDF_FILE_TOKEN_3")
        assertFalse(result.content.contains("PDF_FILE_TOKEN_4"))
        assertTrue(result.truncated)
        assertTrue(result.warning.isNotBlank())
        assertTrue(result.renderedPages.isEmpty())
    }

    private fun extract(
        attachments: List<AiAttachmentInputWire>,
        maxRenderedPages: Int,
    ): PdfAttachmentBatchResult = extractor.extract(
        attachments = attachments,
        deadline = testDeadline(),
        maxRenderedPages = maxRenderedPages,
    )

    private fun testDeadline(): AiRequestDeadline = AiRequestDeadline.start(
        AiTimeoutConfig(
            jobDeadlineMs = 60_000L,
            finalizationReserveMs = 1_000L,
        ),
    )

    private fun selectablePdf(text: String): ByteArray = pdf(PageFixture.Selectable(text))

    private fun scannedPdf(pageCount: Int): ByteArray = pdf(
        *Array(pageCount) { PageFixture.Scanned },
    )

    private fun pdf(vararg pages: PageFixture): ByteArray = PDDocument().use { document ->
        pages.forEachIndexed { index, fixture ->
            val page = PDPage(PDRectangle(TestPageWidth, TestPageHeight))
            document.addPage(page)
            when (fixture) {
                is PageFixture.Selectable -> addSelectableText(document, page, fixture.text)
                PageFixture.Scanned -> addScannedImage(document, page, index + 1)
            }
        }
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }

    private fun addSelectableText(document: PDDocument, page: PDPage, text: String) {
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            stream.newLineAtOffset(20f, TestPageHeight - 32f)
            stream.showText("$text. This page has enough meaningful selectable text for local extraction.")
            stream.endText()
        }
    }

    private fun addScannedImage(document: PDDocument, page: PDPage, pageNumber: Int) {
        val image = BufferedImage(180, 80, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.BLACK
            graphics.drawString("SCANNED PAGE $pageNumber", 15, 42)
        } finally {
            graphics.dispose()
        }
        val png = ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
        val pdfImage = PDImageXObject.createFromByteArray(document, png, "scan-$pageNumber.png")
        PDPageContentStream(document, page).use { stream ->
            stream.drawImage(pdfImage, 20f, 80f, 200f, 90f)
        }
    }

    private fun encryptedPdf(userPassword: String): ByteArray = PDDocument().use { document ->
        val page = PDPage(PDRectangle(TestPageWidth, TestPageHeight))
        document.addPage(page)
        addSelectableText(document, page, "SECRET_ENCRYPTED_TEXT")
        document.protect(
            StandardProtectionPolicy(
                "owner-secret",
                userPassword,
                AccessPermission(),
            ),
        )
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }

    private fun pdfAttachment(
        bytes: ByteArray,
        name: String,
        sourceReferenceId: String = "fixture:${name.hashCode()}",
    ): AiAttachmentInputWire = AiAttachmentInputWire(
        dataUrl = "data:application/pdf;base64,${Base64.getEncoder().encodeToString(bytes)}",
        mimeType = "application/pdf",
        name = name,
        sizeBytes = bytes.size.toLong(),
        kind = ChatAttachmentKind.Pdf.wireValue,
        source = "incoming_share",
        sourceReferenceId = sourceReferenceId,
        approvedAtEpochMillis = TestApprovalEpochMillis,
    ).also { attachment ->
        check(attachment.validate().isEmpty()) { attachment.validate().joinToString() }
    }

    private fun String.decodeDataUrl(): ByteArray =
        Base64.getDecoder().decode(substringAfter(',', missingDelimiterValue = ""))

    private fun ByteArray.startsWithPdfHeader(): Boolean =
        size >= 5 && copyOfRange(0, 5).contentEquals("%PDF-".encodeToByteArray())

    private sealed interface PageFixture {
        data class Selectable(val text: String) : PageFixture
        data object Scanned : PageFixture
    }

    private companion object {
        const val TestPageWidth = 240f
        const val TestPageHeight = 320f
        const val TestApprovalEpochMillis = 1_728_000_000_000L
        const val MaxRenderedPageBytes = 512 * 1024
    }
}
