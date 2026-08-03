package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.domain.ContentAssetErrorCode
import com.changeyourlife.cyl.backend.domain.ContentAssetKind
import com.changeyourlife.cyl.backend.domain.ContentAssetRecord
import com.changeyourlife.cyl.backend.domain.ContentAssetResult
import com.changeyourlife.cyl.backend.model.asset.ContentAssetApiError
import com.changeyourlife.cyl.backend.model.asset.ContentAssetErrorResponse
import com.changeyourlife.cyl.backend.model.asset.ContentAssetResponse
import com.changeyourlife.cyl.backend.model.asset.ContentAssetUploadIntentResponse
import com.changeyourlife.cyl.backend.model.asset.CreateContentAssetUploadIntentRequest
import com.changeyourlife.cyl.backend.service.ContentAssetReadOutcome
import com.changeyourlife.cyl.backend.service.ContentAssetService
import com.changeyourlife.cyl.backend.service.CreateContentAssetCommand
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

fun Route.contentAssetRoutes(service: ContentAssetService) {
    authenticate("auth-jwt") {
        route("/api/v1/content-assets") {
            post("/upload-intents") {
                val ownerId = call.requireContentAssetOwner() ?: return@post
                val request = call.receive<CreateContentAssetUploadIntentRequest>()
                val result = service.createUploadIntent(
                    ownerId = ownerId,
                    idempotencyKey = call.request.headers[IdempotencyKeyHeader].orEmpty(),
                    command = request.toCommand(),
                )
                when (result) {
                    is ContentAssetResult.Success -> {
                        val outcome = result.value
                        call.respond(
                            if (outcome.replayed) HttpStatusCode.OK else HttpStatusCode.Created,
                            ContentAssetUploadIntentResponse(
                                assetId = outcome.record.assetId,
                                status = outcome.record.status.wireValue,
                                uploadUrl = outcome.uploadIntent?.uploadUrl,
                                requiredHeaders = outcome.uploadIntent?.requiredHeaders.orEmpty(),
                                expiresAtEpochMillis = outcome.uploadIntent?.expiresAtEpochMillis,
                                replayed = outcome.replayed,
                            ),
                        )
                    }
                    is ContentAssetResult.Failure -> call.respondContentAssetFailure(result)
                }
            }

            post("/{assetId}/complete") {
                val ownerId = call.requireContentAssetOwner() ?: return@post
                val assetId = call.parameters["assetId"]?.takeIf(String::isNotBlank)
                    ?: return@post call.respondContentAssetFailure(invalidAssetId())
                when (val result = service.completeUpload(ownerId, assetId)) {
                    is ContentAssetResult.Success -> call.respond(result.value.toResponse())
                    is ContentAssetResult.Failure -> call.respondContentAssetFailure(result)
                }
            }

            get("/{assetId}") {
                val ownerId = call.requireContentAssetOwner() ?: return@get
                val assetId = call.parameters["assetId"]?.takeIf(String::isNotBlank)
                    ?: return@get call.respondContentAssetFailure(invalidAssetId())
                val includeDownload = call.request.queryParameters["includeDownload"].isTrueFlag()
                when (val result = service.getAsset(ownerId, assetId, includeDownload)) {
                    is ContentAssetResult.Success -> call.respond(result.value.toResponse())
                    is ContentAssetResult.Failure -> call.respondContentAssetFailure(result)
                }
            }

            delete("/{assetId}") {
                val ownerId = call.requireContentAssetOwner() ?: return@delete
                val assetId = call.parameters["assetId"]?.takeIf(String::isNotBlank)
                    ?: return@delete call.respondContentAssetFailure(invalidAssetId())
                when (val result = service.deleteAsset(ownerId, assetId)) {
                    is ContentAssetResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is ContentAssetResult.Failure -> call.respondContentAssetFailure(result)
                }
            }
        }
    }
}

private fun CreateContentAssetUploadIntentRequest.toCommand() = CreateContentAssetCommand(
    assetId = assetId,
    workspaceId = workspaceId,
    pageId = pageId,
    kind = ContentAssetKind.fromWireValue(kind),
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    sha256 = sha256,
)

private fun ContentAssetReadOutcome.toResponse(): ContentAssetResponse = record.toResponse(
    downloadUrl = readHandle?.readUrl,
    expiresAt = readHandle?.expiresAtEpochMillis,
)

private fun ContentAssetRecord.toResponse(
    downloadUrl: String? = null,
    expiresAt: Long? = null,
) = ContentAssetResponse(
    assetId = assetId,
    workspaceId = workspaceId,
    pageId = pageId,
    kind = kind.wireValue,
    mimeType = mimeType,
    originalName = originalName,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    status = status.wireValue,
    errorCode = errorCode?.wireValue,
    downloadUrl = downloadUrl,
    downloadUrlExpiresAtEpochMillis = expiresAt,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private suspend fun ApplicationCall.requireContentAssetOwner(): String? {
    val ownerId = principal<JWTPrincipal>()?.payload?.subject
    if (ownerId.isNullOrBlank()) {
        respondContentAssetFailure(
            ContentAssetResult.Failure(ContentAssetErrorCode.Forbidden),
            HttpStatusCode.Unauthorized,
        )
        return null
    }
    return ownerId
}

private suspend fun ApplicationCall.respondContentAssetFailure(
    failure: ContentAssetResult.Failure,
    statusOverride: HttpStatusCode? = null,
) {
    val status = statusOverride ?: failure.code.httpStatus()
    application.environment.log.warn(
        "Content asset request rejected: code={}, status={}",
        failure.code.wireValue,
        status.value,
    )
    respond(
        status,
        ContentAssetErrorResponse(
            ContentAssetApiError(
                code = failure.code.wireValue,
                message = failure.code.safeMessage(),
                retryable = failure.retryable,
            ),
        ),
    )
}

private fun ContentAssetErrorCode.httpStatus(): HttpStatusCode = when (this) {
    ContentAssetErrorCode.InvalidRequest -> HttpStatusCode.BadRequest
    ContentAssetErrorCode.Forbidden -> HttpStatusCode.Forbidden
    ContentAssetErrorCode.NotFound -> HttpStatusCode.NotFound
    ContentAssetErrorCode.IdempotencyConflict,
    ContentAssetErrorCode.InvalidState -> HttpStatusCode.Conflict
    ContentAssetErrorCode.UploadValidationFailed -> HttpStatusCode.UnprocessableEntity
    ContentAssetErrorCode.FeatureDisabled,
    ContentAssetErrorCode.StorageUnavailable -> HttpStatusCode.ServiceUnavailable
}

private fun ContentAssetErrorCode.safeMessage(): String = when (this) {
    ContentAssetErrorCode.FeatureDisabled -> "File assets are not enabled."
    ContentAssetErrorCode.InvalidRequest -> "The file request is invalid."
    ContentAssetErrorCode.Forbidden -> "The file is not accessible."
    ContentAssetErrorCode.NotFound -> "The file was not found."
    ContentAssetErrorCode.IdempotencyConflict -> "This request key was already used for different content."
    ContentAssetErrorCode.InvalidState -> "The file is not ready for that operation."
    ContentAssetErrorCode.UploadValidationFailed -> "The uploaded file did not match the upload request."
    ContentAssetErrorCode.StorageUnavailable -> "Private file storage is temporarily unavailable."
}

private fun invalidAssetId() = ContentAssetResult.Failure(
    ContentAssetErrorCode.InvalidRequest,
    "assetId is required.",
)

private fun String?.isTrueFlag(): Boolean = equals("true", ignoreCase = true) || this == "1"
private const val IdempotencyKeyHeader = "Idempotency-Key"
