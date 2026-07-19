package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.model.ai.ChatRequest
import com.changeyourlife.cyl.backend.model.ai.ChatResponse
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobAcceptedResponse
import com.changeyourlife.cyl.backend.model.ai.AiChatActionsJobStatusResponse
import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiIdempotencyConflictException
import com.changeyourlife.cyl.backend.service.AiJobService
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.service.toContractWire
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import com.changeyourlife.cyl.backend.model.ai.AiStatusResponse
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun Route.aiRoutes(
    aiService: AiService,
    aiJobService: AiJobService,
) {
    route("/ai") {
        get("/status") {
            call.respond(
                AiStatusResponse(
                    mode = if (aiService.isMockMode) "sandbox" else "live",
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
            post("/chat") {
                val request = call.receive<ChatRequest>()
                val reply = withContext(Dispatchers.IO) {
                    aiService.chat(
                        messages = request.messages,
                        images = request.images,
                    )
                }
                call.respond(ChatResponse(content = reply))
            }

            post("/chat-actions") {
                val request = call.receive<ChatWithActionsRequest>()
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
                val request = call.receive<ChatWithActionsRequest>()
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
private val IdempotencyKeyLengthRange = 8..128
private val IdempotencyKeyPattern = Regex("[A-Za-z0-9._:-]+")

private fun ChatWithActionsRequest.idempotencyFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    messages.forEach { message ->
        digest.updateField(message.role)
        digest.updateField(message.content)
    }
    pages.forEach { page ->
        digest.updateField(page.id)
        digest.updateField(page.title)
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
        }
    }
    tasks.forEach { task ->
        digest.updateField(task.id)
        digest.updateField(task.title)
    }
    digest.updateField(clientDate)
    digest.updateField(clientTimezone)
    images.forEach { image ->
        digest.updateField(image.dataUrl)
        digest.updateField(image.textContent)
        digest.updateField(image.mimeType)
        digest.updateField(image.name)
        digest.updateField(image.sizeBytes.toString())
        digest.updateField(image.kind)
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
