package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.aicontract.AiAttachmentInputWire
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_ATTACHMENTS
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_IMAGE_BYTES
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_PDF_BYTES
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_TEXT_BYTES
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.backend.model.ai.ChatRequest
import com.changeyourlife.cyl.backend.model.ai.ChatResponse
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobAcceptedResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobStatusResponse
import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.service.AiJobService
import com.changeyourlife.cyl.backend.service.AiRetrievalBoundaryResult
import com.changeyourlife.cyl.backend.service.AiRetrievalPrivacyBoundary
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.service.toContractWire
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.header
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import com.changeyourlife.cyl.backend.model.ai.AiStatusResponse
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun Route.aiRoutes(
    aiService: AiService,
    aiJobService: AiJobService,
    contentRepository: ContentRepository,
) {
    val retrievalPrivacyBoundary = AiRetrievalPrivacyBoundary(contentRepository)
    route("/ai") {
        get("/status") {
            call.respond(
                AiStatusResponse(
                    mode = aiService.statusMode,
                    provider = aiService.activeProvider,
                    model = aiService.activeModel,
                    visionPipelineVersion = aiService.visionPipelineVersion,
                    visionMaxImageDimension = aiService.visionMaxImageDimension,
                    visionMaxImageBytes = aiService.visionMaxImageBytes,
                    lmStudioVisionModels = aiService.lmStudioVisionModelLabel,
                )
            )
        }
    }

    authenticate("auth-jwt") {
        route("/ai") {
            install(RequestBodyLimit) {
                bodyLimit { AiRequestBodyLimitBytes }
            }

            post("/chat") {
                val request = call.receive<ChatRequest>()
                if (!call.validateAiAttachments(request.images)) return@post
                val reply = withContext(Dispatchers.IO) {
                    aiService.chat(
                        messages = request.messages,
                        images = request.images,
                    )
                }
                call.respond(ChatResponse(content = reply))
            }

            post("/chat-actions") {
                val userId = call.requireUserId() ?: return@post
                val rawRequest = call.receive<ChatWithActionsRequest>()
                if (!call.validateAiAttachments(rawRequest.images)) return@post
                val request = call.enforceRetrievalBoundary(
                    boundary = retrievalPrivacyBoundary,
                    userId = userId,
                    request = rawRequest,
                ) ?: return@post
                val result = withContext(Dispatchers.IO) {
                    aiService.chatWithActions(
                        messages = request.messages,
                        pages = request.pages,
                        tasks = request.tasks,
                        clientDate = request.clientDate,
                        clientTimezone = request.clientTimezone,
                        images = request.images,
                        webSearchEnabled = request.webSearchEnabled,
                        webSearchQuery = request.webSearchQuery,
                    )
                }
                call.respond(result.toResponse())
            }

            post("/chat-actions/jobs") {
                val userId = call.requireUserId() ?: return@post
                val idempotencyKey = call.requireIdempotencyKey() ?: return@post
                val rawRequest = call.receive<ChatWithActionsRequest>()
                if (!call.validateAiAttachments(rawRequest.images)) return@post
                val request = call.enforceRetrievalBoundary(
                    boundary = retrievalPrivacyBoundary,
                    userId = userId,
                    request = rawRequest,
                ) ?: return@post
                val job = try {
                    aiJobService.createChatActionsJob(
                        ownerId = userId,
                        idempotencyKey = idempotencyKey,
                        requestFingerprint = request.idempotencyFingerprint(),
                        diagnostics = aiService.initialDiagnosticsFor(request.images),
                    ) { progress ->
                        aiService.chatWithActions(
                            messages = request.messages,
                            pages = request.pages,
                            tasks = request.tasks,
                            clientDate = request.clientDate,
                            clientTimezone = request.clientTimezone,
                            images = request.images,
                            webSearchEnabled = request.webSearchEnabled,
                            webSearchQuery = request.webSearchQuery,
                            progress = progress,
                        ).toResponse()
                    }
                } catch (_: AiIdempotencyConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Idempotency-Key was already used with a different request."),
                    )
                    return@post
                }
                call.respond(HttpStatusCode.Accepted, job.toAcceptedResponse())
            }

            get("/chat-actions/jobs/{jobId}") {
                val userId = call.requireUserId() ?: return@get
                val jobId = call.parameters["jobId"].orEmpty()
                if (jobId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing jobId."))
                    return@get
                }
                val job = aiJobService.getChatActionsJob(ownerId = userId, jobId = jobId)
                if (job == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "AI job not found."))
                    return@get
                }
                call.respond(job.toStatusResponse())
            }

        }
    }
}

private suspend fun ApplicationCall.enforceRetrievalBoundary(
    boundary: AiRetrievalPrivacyBoundary,
    userId: String,
    request: ChatWithActionsRequest,
): ChatWithActionsRequest? {
    return when (val result = boundary.enforce(userId = userId, request = request)) {
        is AiRetrievalBoundaryResult.Allowed -> result.request
        is AiRetrievalBoundaryResult.Rejected -> {
            respond(
                if (result.forbidden) HttpStatusCode.Forbidden else HttpStatusCode.UnprocessableEntity,
                mapOf(
                    "error" to mapOf(
                        "code" to result.code,
                        "message" to result.message,
                    ),
                ),
            )
            null
        }
    }
}

private suspend fun ApplicationCall.validateAiAttachments(
    attachments: List<AiAttachmentInputWire>,
): Boolean {
    val issues = buildList {
        if (attachments.size > CYL_MAX_AI_ATTACHMENTS) {
            add(
                AiAttachmentValidationIssueResponse(
                    field = "images",
                    code = "too_many_attachments",
                    message = "A maximum of $CYL_MAX_AI_ATTACHMENTS AI attachments is allowed.",
                ),
            )
        }
        val pdfCount = attachments.count { attachment ->
            attachment.attachmentKind == ChatAttachmentKind.Pdf
        }
        if (pdfCount > MaxAiPdfAttachments) {
            add(
                AiAttachmentValidationIssueResponse(
                    field = "images",
                    code = "too_many_pdf_attachments",
                    message = "A maximum of $MaxAiPdfAttachments PDF attachments is allowed per AI request.",
                ),
            )
        }
        var aggregateInlineBytes = 0L
        var aggregatePdfBytes = 0L
        attachments.forEachIndexed { index, attachment ->
            attachment.validate().forEach { issue ->
                add(
                    AiAttachmentValidationIssueResponse(
                        field = "images[$index].${issue.field}",
                        code = issue.code,
                        message = issue.message,
                    ),
                )
            }
            val measuredBytes = attachment.measuredInlinePayloadBytes()
            if (attachment.attachmentKind in InlineAttachmentKinds) {
                if (measuredBytes == null && attachment.hasInlinePayload) {
                    add(
                        AiAttachmentValidationIssueResponse(
                            field = "images[$index].dataUrl",
                            code = "invalid_inline_attachment_payload",
                            message = "Attachment payload encoding does not match its declared kind.",
                        ),
                    )
                } else if (measuredBytes != null) {
                    aggregateInlineBytes += measuredBytes
                    if (attachment.attachmentKind == ChatAttachmentKind.Pdf) {
                        aggregatePdfBytes += measuredBytes
                    }
                    if (measuredBytes != attachment.sizeBytes) {
                        add(
                            AiAttachmentValidationIssueResponse(
                                field = "images[$index].sizeBytes",
                                code = "attachment_size_mismatch",
                                message = "Attachment sizeBytes does not match the inline payload.",
                            ),
                        )
                    }
                    val maximumBytes = when (attachment.attachmentKind) {
                        ChatAttachmentKind.Image -> CYL_MAX_AI_IMAGE_BYTES
                        ChatAttachmentKind.TextFile -> CYL_MAX_AI_TEXT_BYTES
                        ChatAttachmentKind.Pdf -> CYL_MAX_AI_PDF_BYTES
                        else -> 0L
                    }
                    if (measuredBytes > maximumBytes) {
                        add(
                            AiAttachmentValidationIssueResponse(
                                field = "images[$index].dataUrl",
                                code = "attachment_payload_too_large",
                                message = "Decoded attachment payload exceeds the maximum AI input size.",
                            ),
                        )
                    }
                }
            }
        }
        if (aggregatePdfBytes > MaxAiPdfAggregateBytes) {
            add(
                AiAttachmentValidationIssueResponse(
                    field = "images",
                    code = "pdf_payload_total_too_large",
                    message = "Combined decoded PDF payloads exceed the per-request limit.",
                ),
            )
        }
        if (aggregateInlineBytes > MaxAiInlineAggregateBytes) {
            add(
                AiAttachmentValidationIssueResponse(
                    field = "images",
                    code = "attachment_payload_total_too_large",
                    message = "Combined decoded attachment payloads exceed the per-request limit.",
                ),
            )
        }
    }
    if (issues.isEmpty()) return true
    respond(
        HttpStatusCode.UnprocessableEntity,
        AiAttachmentValidationResponse(
            error = AiAttachmentValidationErrorResponse(
                code = "invalid_ai_attachment",
                message = "One or more AI attachments are invalid.",
                issues = issues,
            ),
        ),
    )
    return false
}

@Serializable
private data class AiAttachmentValidationResponse(
    val error: AiAttachmentValidationErrorResponse,
)

@Serializable
private data class AiAttachmentValidationErrorResponse(
    val code: String,
    val message: String,
    val issues: List<AiAttachmentValidationIssueResponse>,
)

@Serializable
private data class AiAttachmentValidationIssueResponse(
    val field: String,
    val code: String,
    val message: String,
)

private fun AiAttachmentInputWire.measuredInlinePayloadBytes(): Long? = when (attachmentKind) {
    ChatAttachmentKind.Image -> dataUrl.estimatedBase64PayloadBytes(expectedMediaTypePrefix = "image/")
    ChatAttachmentKind.Pdf -> dataUrl.estimatedBase64PayloadBytes(expectedMediaType = "application/pdf")
    ChatAttachmentKind.TextFile -> textContent.toByteArray(StandardCharsets.UTF_8).size.toLong()
    else -> null
}

private fun String.estimatedBase64PayloadBytes(
    expectedMediaTypePrefix: String = "",
    expectedMediaType: String = "",
): Long? {
    if (!startsWith("data:", ignoreCase = true)) return null
    val commaIndex = indexOf(',')
    if (commaIndex <= "data:".length || commaIndex == lastIndex) return null
    if (commaIndex > MaxInlineDataUrlMetadataChars) return null
    val metadata = substring("data:".length, commaIndex)
    val segments = metadata.split(';')
    val mediaType = segments.firstOrNull().orEmpty()
    val mediaTypeMatches = when {
        expectedMediaType.isNotBlank() -> mediaType.equals(expectedMediaType, ignoreCase = true)
        else -> mediaType.startsWith(expectedMediaTypePrefix, ignoreCase = true)
    }
    if (!mediaTypeMatches) return null
    if (segments.none { segment -> segment.equals("base64", ignoreCase = true) }) return null

    val payload = substring(commaIndex + 1)
    val padding = payload.takeLastWhile { character -> character == '=' }.length
    if (payload.isEmpty() || payload.length % 4 != 0) return null
    if (padding > 2 || payload.dropLast(padding).contains('=')) return null
    val unpaddedLength = payload.length - padding
    val hasValidPadding = when (padding) {
        0 -> unpaddedLength % 4 == 0
        1 -> unpaddedLength % 4 == 3
        2 -> unpaddedLength % 4 == 2
        else -> false
    }
    if (!hasValidPadding) return null
    if (payload.take(unpaddedLength).any { character -> !character.isBase64Character() }) return null
    return (payload.length / 4L) * 3L - padding
}

private fun Char.isBase64Character(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/'

private suspend fun ApplicationCall.requireIdempotencyKey(): String? {
    val value = request.header(IdempotencyHeader)
        ?.trim()
        .orEmpty()
    if (value.length !in IdempotencyKeyLengthRange || !value.matches(IdempotencyKeyPattern)) {
        respond(
            HttpStatusCode.BadRequest,
            mapOf(
                "error" to
                    "Missing or invalid $IdempotencyHeader. Use 8-128 letters, numbers, '.', '_', ':', or '-'.",
            ),
        )
        return null
    }
    return value
}

private suspend fun ApplicationCall.requireUserId(): String? {
    val userId = principal<JWTPrincipal>()?.payload?.subject
    if (userId.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing authenticated user."))
        return null
    }
    return userId
}

private fun AiChatActionsJob.toAcceptedResponse(): AiChatActionsJobAcceptedResponse =
    AiChatActionsJobAcceptedResponse(
        jobId = jobId,
        status = status.wireValue,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        phase = phase,
        diagnostics = diagnostics,
    )

private const val IdempotencyHeader = "Idempotency-Key"
private const val MaxAiPdfAttachments = 3
private const val AiRequestBodyLimitBytes = 40L * 1024L * 1024L
private const val MaxAiPdfAggregateBytes = 24L * 1024L * 1024L
private const val MaxAiInlineAggregateBytes = 28L * 1024L * 1024L
private const val MaxInlineDataUrlMetadataChars = 256
private val InlineAttachmentKinds = setOf(
    ChatAttachmentKind.Image,
    ChatAttachmentKind.TextFile,
    ChatAttachmentKind.Pdf,
)
private val IdempotencyKeyLengthRange = 8..128
private val IdempotencyKeyPattern = Regex("[A-Za-z0-9._:-]+")

private fun ChatWithActionsRequest.idempotencyFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    messages.forEach { message ->
        digest.updateField(message.role)
        digest.updateField(message.content)
    }
    digest.updateField(retrievalScope.workspaceId)
    digest.updateField(retrievalScope.mode)
    digest.updateField(retrievalScope.currentPageId)
    retrievalScope.explicitPageIds.forEach(digest::updateField)
    retrievalScope.retrievedPageIds.forEach(digest::updateField)
    digest.updateField(retrievalScope.includeTasks.toString())
    pages.forEach { page ->
        digest.updateField(page.id)
        digest.updateField(page.title)
        digest.updateField(page.workspaceId)
        digest.updateField(page.access)
        digest.updateField(page.totalBlockCount.toString())
        digest.updateField(page.isFocused.toString())
        digest.updateField(page.contextComplete.toString())
        page.blocks.forEach { block ->
            digest.updateField(block.id)
            digest.updateField(block.type)
            digest.updateField(block.text)
            digest.updateField(block.path)
            digest.updateField(block.tableTitle)
            digest.updateField(block.tableBlockId)
            digest.updateField(block.rowId)
            digest.updateField(block.rowTitle)
            digest.updateField(block.rowBlockId)
            digest.updateField(block.isChecked?.toString().orEmpty())
            digest.updateField(block.totalRowCount.toString())
            digest.updateField(block.contextComplete.toString())
            block.tableColumns.forEach { column ->
                digest.updateField(column.id)
                digest.updateField(column.name)
                digest.updateField(column.type)
                digest.updateField(column.config)
            }
            block.tableRows.forEach { row ->
                digest.updateField(row.id)
                digest.updateField(row.title)
                digest.updateField(row.totalBlockCount.toString())
                row.cells.forEach { cell ->
                    digest.updateField(cell.columnId)
                    digest.updateField(cell.columnName)
                    digest.updateField(cell.value)
                }
            }
        }
    }
    tasks.forEach { task ->
        digest.updateField(task.id)
        digest.updateField(task.title)
        digest.updateField(task.workspaceId)
    }
    digest.updateField(clientDate)
    digest.updateField(clientTimezone)
    images.forEach { image ->
        digest.updateField(image.assetId)
        digest.updateField(image.dataUrl)
        digest.updateField(image.textContent)
        digest.updateField(image.mimeType)
        digest.updateField(image.name)
        digest.updateField(image.sizeBytes.toString())
        digest.updateField(image.kind)
        digest.updateField(image.durationMs?.toString().orEmpty())
        digest.updateField(image.sha256)
        digest.updateField(image.source)
        digest.updateField(image.sourceReferenceId)
        digest.updateField(image.approvedAtEpochMillis.toString())
    }
    digest.updateField(webSearchEnabled.toString())
    digest.updateField(webSearchQuery)
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateField(value: String) {
    update(value.length.toString().toByteArray(StandardCharsets.UTF_8))
    update(':'.code.toByte())
    update(value.toByteArray(StandardCharsets.UTF_8))
}

private fun AiChatActionsJob.toStatusResponse(): AiChatActionsJobStatusResponse =
    AiChatActionsJobStatusResponse(
        jobId = jobId,
        status = status.wireValue,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        result = result,
        error = error,
        phase = phase,
        diagnostics = diagnostics,
    )

private fun AiService.AiActionResult.toResponse(): ChatWithActionsResponse =
    ChatWithActionsResponse(
        reply = reply,
        validationIssues = validationIssues,
        actions = actions.map(AiService.AiActionItem::toContractWire),
        diagnostics = diagnostics,
    )
