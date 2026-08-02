package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.aicontract.ChatAttachmentKind
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRecord
import com.changeyourlife.cyl.backend.domain.VoiceAttachmentResult
import com.changeyourlife.cyl.backend.model.attachment.ChatAttachmentApiError
import com.changeyourlife.cyl.backend.model.attachment.ChatAttachmentErrorResponse
import com.changeyourlife.cyl.backend.model.attachment.ChatAttachmentResponse
import com.changeyourlife.cyl.backend.model.attachment.ChatAttachmentUploadIntentResponse
import com.changeyourlife.cyl.backend.model.attachment.CreateChatAttachmentUploadIntentRequest
import com.changeyourlife.cyl.backend.service.ChatAttachmentReadOutcome
import com.changeyourlife.cyl.backend.service.ChatAttachmentService
import com.changeyourlife.cyl.backend.service.CreateChatAttachmentCommand
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.chatAttachmentRoutes(service: ChatAttachmentService) {
    authenticate("auth-jwt") {
        route("/api/v1/chat-attachments") {
            post("/upload-intents") {
                val ownerId = call.requireAttachmentOwnerId() ?: return@post
                val idempotencyKey = call.request.headers[IdempotencyKeyHeader].orEmpty()
                val request = call.receive<CreateChatAttachmentUploadIntentRequest>()
                when (
                    val result = service.createUploadIntent(
                        ownerId = ownerId,
                        idempotencyKey = idempotencyKey,
                        command = request.toCommand(),
                    )
                ) {
                    is VoiceAttachmentResult.Success -> {
                        val outcome = result.value
                        val intent = outcome.uploadIntent
                        call.respond(
                            status = if (outcome.replayed) HttpStatusCode.OK else HttpStatusCode.Created,
                            message = ChatAttachmentUploadIntentResponse(
                                attachmentId = outcome.record.attachmentId,
                                status = outcome.record.status.wireValue,
                                uploadUrl = intent?.uploadUrl,
                                requiredHeaders = intent?.requiredHeaders.orEmpty(),
                                expiresAtEpochMillis = intent?.expiresAtEpochMillis,
                                replayed = outcome.replayed,
                            ),
                        )
                    }
                    is VoiceAttachmentResult.Failure -> call.respondAttachmentFailure(result)
                }
            }

            post("/{attachmentId}/complete") {
                val ownerId = call.requireAttachmentOwnerId() ?: return@post
                val attachmentId = call.parameters["attachmentId"]
                    ?.takeIf(String::isNotBlank)
                    ?: return@post call.respondAttachmentFailure(
                        VoiceAttachmentResult.Failure(
                            ChatAttachmentErrorCode.InvalidRequest,
                            "attachmentId is required.",
                        ),
                    )
                when (val result = service.completeUpload(ownerId, attachmentId)) {
                    is VoiceAttachmentResult.Success -> call.respond(result.value.toResponse())
                    is VoiceAttachmentResult.Failure -> call.respondAttachmentFailure(result)
                }
            }

            get("/{attachmentId}") {
                val ownerId = call.requireAttachmentOwnerId() ?: return@get
                val attachmentId = call.parameters["attachmentId"]
                    ?.takeIf(String::isNotBlank)
                    ?: return@get call.respondAttachmentFailure(
                        VoiceAttachmentResult.Failure(
                            ChatAttachmentErrorCode.InvalidRequest,
                            "attachmentId is required.",
                        ),
                    )
                val includePlayback = call.request.queryParameters["includePlayback"].isTrueFlag()
                when (val result = service.getAttachment(ownerId, attachmentId, includePlayback)) {
                    is VoiceAttachmentResult.Success -> call.respond(result.value.toResponse())
                    is VoiceAttachmentResult.Failure -> call.respondAttachmentFailure(result)
                }
            }

            delete("/{attachmentId}") {
                val ownerId = call.requireAttachmentOwnerId() ?: return@delete
                val attachmentId = call.parameters["attachmentId"]
                    ?.takeIf(String::isNotBlank)
                    ?: return@delete call.respondAttachmentFailure(
                        VoiceAttachmentResult.Failure(
                            ChatAttachmentErrorCode.InvalidRequest,
                            "attachmentId is required.",
                        ),
                    )
                when (val result = service.deleteAttachment(ownerId, attachmentId)) {
                    is VoiceAttachmentResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is VoiceAttachmentResult.Failure -> call.respondAttachmentFailure(result)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.requireAttachmentOwnerId(): String? {
    val ownerId = principal<JWTPrincipal>()?.payload?.subject
    if (ownerId.isNullOrBlank()) {
        respondAttachmentFailure(
            VoiceAttachmentResult.Failure(
                ChatAttachmentErrorCode.AttachmentForbidden,
                "Authenticated owner is missing.",
            ),
            statusOverride = HttpStatusCode.Unauthorized,
        )
        return null
    }
    return ownerId
}

private suspend fun ApplicationCall.respondAttachmentFailure(
    failure: VoiceAttachmentResult.Failure,
    statusOverride: HttpStatusCode? = null,
) {
    val status = statusOverride ?: failure.code.httpStatus()
    application.environment.log.warn(
        "Chat attachment request rejected: code={}, status={}",
        failure.code.wireValue,
        status.value,
    )
    respond(
        status,
        ChatAttachmentErrorResponse(
            error = ChatAttachmentApiError(
                code = failure.code.wireValue,
                message = failure.code.safeMessage(),
                retryable = failure.retryable,
            ),
        ),
    )
}

private fun CreateChatAttachmentUploadIntentRequest.toCommand(): CreateChatAttachmentCommand =
    CreateChatAttachmentCommand(
        kind = ChatAttachmentKind.fromWireValue(kind),
        mimeType = mimeType,
        originalName = originalName,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        sha256 = sha256,
        sessionClientId = sessionClientId,
        messageClientId = messageClientId,
    )

private fun ChatAttachmentReadOutcome.toResponse(): ChatAttachmentResponse = record.toResponse(
    playbackUrl = readHandle?.readUrl,
    playbackExpiresAt = readHandle?.expiresAtEpochMillis,
)

private fun ChatAttachmentRecord.toResponse(
    playbackUrl: String? = null,
    playbackExpiresAt: Long? = null,
): ChatAttachmentResponse = ChatAttachmentResponse(
    attachmentId = attachmentId,
    sessionClientId = sessionClientId,
    messageClientId = messageClientId,
    kind = kind.wireValue,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    sha256 = sha256,
    status = status.wireValue,
    transcript = transcript,
    transcriptLanguage = transcriptLanguage,
    errorCode = errorCode?.wireValue,
    playbackUrl = playbackUrl,
    playbackUrlExpiresAtEpochMillis = playbackExpiresAt,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ChatAttachmentErrorCode.httpStatus(): HttpStatusCode = when (this) {
    ChatAttachmentErrorCode.InvalidRequest -> HttpStatusCode.BadRequest
    ChatAttachmentErrorCode.UploadValidationFailed -> HttpStatusCode.UnprocessableEntity
    ChatAttachmentErrorCode.AttachmentForbidden -> HttpStatusCode.Forbidden
    ChatAttachmentErrorCode.AttachmentNotFound -> HttpStatusCode.NotFound
    ChatAttachmentErrorCode.IdempotencyConflict,
    ChatAttachmentErrorCode.InvalidState -> HttpStatusCode.Conflict
    ChatAttachmentErrorCode.FeatureDisabled,
    ChatAttachmentErrorCode.StorageUnavailable,
    ChatAttachmentErrorCode.UploadOffline,
    ChatAttachmentErrorCode.UploadUrlExpired -> HttpStatusCode.ServiceUnavailable
    else -> if (retryable) HttpStatusCode.ServiceUnavailable else HttpStatusCode.BadRequest
}

private fun ChatAttachmentErrorCode.safeMessage(): String = when (this) {
    ChatAttachmentErrorCode.FeatureDisabled -> "Voice notes are not enabled."
    ChatAttachmentErrorCode.InvalidRequest -> "The attachment request is invalid."
    ChatAttachmentErrorCode.InvalidState -> "The attachment is not ready for that operation."
    ChatAttachmentErrorCode.IdempotencyConflict ->
        "This attachment request key was already used for different content."
    ChatAttachmentErrorCode.UploadValidationFailed -> "The uploaded audio did not match the upload request."
    ChatAttachmentErrorCode.AttachmentForbidden -> "The attachment is not accessible."
    ChatAttachmentErrorCode.AttachmentNotFound -> "The attachment was not found."
    ChatAttachmentErrorCode.StorageUnavailable -> "Private audio storage is temporarily unavailable."
    ChatAttachmentErrorCode.UploadOffline -> "The audio upload is waiting for a connection."
    ChatAttachmentErrorCode.UploadUrlExpired -> "The audio upload link expired. Please retry."
    else -> "The voice-note operation could not be completed."
}

private fun String?.isTrueFlag(): Boolean = equals("true", ignoreCase = true) || this == "1"

private const val IdempotencyKeyHeader = "Idempotency-Key"
