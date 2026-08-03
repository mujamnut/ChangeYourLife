package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.ai.AiApi
import com.changeyourlife.cyl.data.remote.ai.AiBlockContextDto
import com.changeyourlife.cyl.data.remote.ai.AiImageInputDto
import com.changeyourlife.cyl.data.remote.ai.AiPageContextDto
import com.changeyourlife.cyl.data.remote.ai.AiRetrievalScopeDto
import com.changeyourlife.cyl.data.remote.ai.AiTableCellContextDto
import com.changeyourlife.cyl.data.remote.ai.AiTableColumnContextDto
import com.changeyourlife.cyl.data.remote.ai.AiTableRowContextDto
import com.changeyourlife.cyl.data.remote.ai.AiTaskContextDto
import com.changeyourlife.cyl.data.remote.ai.ChatMessageDto
import com.changeyourlife.cyl.data.remote.ai.ChatRequestDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsRequestDto
import com.changeyourlife.cyl.domain.repository.AiError
import com.changeyourlife.cyl.domain.repository.AiStatus
import com.changeyourlife.cyl.domain.repository.AiErrorKind
import com.changeyourlife.cyl.domain.repository.AiException
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.AiRetrievalScope
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import java.time.LocalDate
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class AiRepositoryImpl @Inject constructor(
    private val aiApi: AiApi,
    private val tokenStore: AuthTokenStore,
    private val json: Json,
) : AiRepository {

    private suspend fun getAuthHeader(): String {
        val token = tokenStore.token.value ?: error("No active auth session.")
        return "Bearer $token"
    }

    private fun clearSessionIfUnauthorized(error: AiException) {
        if (error.aiError.kind == AiErrorKind.Unauthorized) {
            tokenStore.clearToken()
        }
    }

    private fun <T> Result<T>.mapAiFailure(): Result<T> {
        return recoverCatching { error ->
            val aiException = AiErrorMapper.fromThrowable(error = error, json = json)
            clearSessionIfUnauthorized(aiException)
            throw aiException
        }
    }

    override suspend fun status(): Result<AiStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val response = aiApi.status()
            AiStatus(
                mode = response.mode,
                provider = response.provider,
                model = response.model,
                visionPipelineVersion = response.visionPipelineVersion,
                visionMaxImageDimension = response.visionMaxImageDimension,
                visionMaxImageBytes = response.visionMaxImageBytes,
                lmStudioVisionModels = response.lmStudioVisionModels,
            )
        }.mapAiFailure()
    }

    override suspend fun chat(messages: List<Pair<String, String>>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = ChatRequestDto(
                messages = messages.map { ChatMessageDto(role = it.first, content = it.second) }
            )
            val response = aiApi.chat(header, request)
            response.content.ifBlank { throw AiErrorMapper.emptyResponse("chat") }
        }.mapAiFailure()
    }

    override suspend fun chatWithActions(
        idempotencyKey: String,
        messages: List<Pair<String, String>>,
        retrievalScope: AiRetrievalScope,
        pages: List<AiPageContext>,
        tasks: List<Pair<String, String>>,
        clientDate: String,
        clientTimezone: String,
        images: List<AiImageAttachment>,
        webSearchEnabled: Boolean,
        webSearchQuery: String,
    ): Result<ChatActionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = ChatWithActionsRequestDto(
                messages = messages.map { ChatMessageDto(role = it.first, content = it.second) },
                retrievalScope = AiRetrievalScopeDto(
                    workspaceId = retrievalScope.workspaceId,
                    mode = retrievalScope.mode.name,
                    currentPageId = retrievalScope.currentPageId,
                    explicitPageIds = retrievalScope.explicitPageIds,
                    retrievedPageIds = retrievalScope.retrievedPageIds,
                    includeTasks = retrievalScope.includeTasks,
                ),
                pages = pages.map { page ->
                    AiPageContextDto(
                        id = page.id,
                        title = page.title,
                        workspaceId = page.workspaceId,
                        access = page.access.name,
                        totalBlockCount = page.totalBlockCount,
                        isFocused = page.isFocused,
                        contextComplete = page.contextComplete,
                        blocks = page.blocks.map { block ->
                            AiBlockContextDto(
                                id = block.id,
                                type = block.type,
                                text = block.text,
                                path = block.path,
                                tableTitle = block.tableTitle,
                                tableBlockId = block.tableBlockId,
                                rowId = block.rowId,
                                rowTitle = block.rowTitle,
                                rowBlockId = block.rowBlockId,
                                isChecked = block.isChecked,
                                tableColumns = block.tableColumns.map { column ->
                                    AiTableColumnContextDto(
                                        id = column.id,
                                        name = column.name,
                                        type = column.type,
                                        config = column.config,
                                    )
                                },
                                tableRows = block.tableRows.map { row ->
                                    AiTableRowContextDto(
                                        id = row.id,
                                        title = row.title,
                                        totalBlockCount = row.totalBlockCount,
                                        cells = row.cells.map { cell ->
                                            AiTableCellContextDto(
                                                columnId = cell.columnId,
                                                columnName = cell.columnName,
                                                value = cell.value,
                                            )
                                        },
                                    )
                                },
                                totalRowCount = block.totalRowCount,
                                contextComplete = block.contextComplete,
                            )
                        },
                    )
                },
                tasks = tasks.map {
                    AiTaskContextDto(
                        id = it.first,
                        title = it.second,
                        workspaceId = retrievalScope.workspaceId,
                    )
                },
                clientDate = clientDate.ifBlank { LocalDate.now().toString() },
                clientTimezone = clientTimezone.ifBlank { TimeZone.getDefault().id },
                images = images.map { image ->
                    AiImageInputDto(
                        dataUrl = image.dataUrl,
                        textContent = image.textContent,
                        mimeType = image.mimeType,
                        name = image.name,
                        sizeBytes = image.sizeBytes,
                        kind = image.kind,
                        assetId = image.assetId,
                        durationMs = image.durationMs,
                        sha256 = image.sha256,
                        source = image.source,
                        sourceReferenceId = image.sourceReferenceId,
                        approvedAtEpochMillis = image.approvedAtEpochMillis,
                    )
                },
                webSearchEnabled = webSearchEnabled,
                webSearchQuery = webSearchQuery,
            )
            val response = runChatWithActionsJob(
                header = header,
                idempotencyKey = idempotencyKey,
                request = request,
            )
            if (response.reply.isBlank() && response.actions.isEmpty() && response.validationIssues.isEmpty()) {
                throw AiErrorMapper.emptyResponse("chatWithActions")
            }
            AiActionContractMapper.toDomain(response)
        }.mapAiFailure()
    }

    private suspend fun runChatWithActionsJob(
        header: String,
        idempotencyKey: String,
        request: ChatWithActionsRequestDto,
    ) = withContext(Dispatchers.IO) {
        val accepted = try {
            aiApi.createChatWithActionsJob(header, idempotencyKey, request)
        } catch (error: HttpException) {
            if (error.isMissingAiJobEndpoint()) {
                return@withContext aiApi.chatWithActions(header, idempotencyKey, request)
            }
            throw error
        }
        val jobId = accepted.jobId.ifBlank {
            throw AiErrorMapper.emptyResponse("chatWithActionsJob")
        }

        var pollDelayMs = AiJobInitialPollDelayMillis
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < AiJobPollingTimeoutMillis) {
            delay(pollDelayMs)
            val status = try {
                aiApi.chatWithActionsJobStatus(header, jobId)
            } catch (error: HttpException) {
                if (error.isMissingAiJobEndpoint()) {
                    return@withContext aiApi.chatWithActions(header, idempotencyKey, request)
                }
                throw error
            }
            when (status.status.lowercase()) {
                AiJobSucceeded -> {
                    return@withContext status.result
                        ?: throw AiErrorMapper.emptyResponse("chatWithActionsJobResult")
                }
                AiJobFailed -> {
                    throw AiException(
                        AiError(
                            kind = AiErrorKind.ProviderError,
                            userMessage = "AI job failed before it could update the page. Please try again.",
                            developerMessage = listOf(
                                status.error.ifBlank { "AI job $jobId failed without an error message." },
                                status.diagnostics.toDeveloperSummary(),
                            ).filter { value -> value.isNotBlank() }.joinToString(" | "),
                            retryable = true,
                        ),
                    )
                }
                AiJobQueued, AiJobRunning -> Unit
                else -> Unit
            }
            pollDelayMs = minOf((pollDelayMs * 1.35).toLong(), AiJobMaxPollDelayMillis)
        }

        throw AiException(
            AiError(
                kind = AiErrorKind.ProviderError,
                userMessage = "AI is still processing. Please try again shortly, or use a smaller/faster model.",
                developerMessage = "AI job $jobId did not complete within ${AiJobPollingTimeoutMillis}ms.",
                retryable = true,
            ),
        )
    }

    private fun HttpException.isMissingAiJobEndpoint(): Boolean {
        return code() == 404 || code() == 405
    }

    private companion object {
        private const val AiJobQueued = "queued"
        private const val AiJobRunning = "running"
        private const val AiJobSucceeded = "succeeded"
        private const val AiJobFailed = "failed"
        private const val AiJobInitialPollDelayMillis = 900L
        private const val AiJobMaxPollDelayMillis = 3_000L
        private const val AiJobPollingTimeoutMillis = 10L * 60L * 1000L
    }
}

private fun com.changeyourlife.cyl.data.remote.ai.AiDiagnosticsDto.toDeveloperSummary(): String =
    listOf(
        phase.takeIf { it.isNotBlank() }?.let { "phase=$it" },
        imageCount.takeIf { it > 0 }?.let { "images=$it" },
        textFileCount.takeIf { it > 0 }?.let { "textFiles=$it" },
        pdfFileCount.takeIf { it > 0 }?.let { "pdfFiles=$it" },
        pdfPageCount.takeIf { it > 0 }?.let { "pdfPages=$it" },
        pdfExtractionStatus.takeIf { it.isNotBlank() }?.let { "pdfStatus=$it" },
        visionProvider.takeIf { it.isNotBlank() }?.let { "visionProvider=$it" },
        visionModel.takeIf { it.isNotBlank() }?.let { "visionModel=$it" },
        visionStatus.takeIf { it.isNotBlank() }?.let { "visionStatus=$it" },
        webSearchProvider.takeIf { it.isNotBlank() }?.let { "webSearchProvider=$it" },
        webSearchStatus.takeIf { it.isNotBlank() }?.let { "webSearchStatus=$it" },
        webSearchResultCount.takeIf { it > 0 }?.let { "webSearchResults=$it" },
    )
        .filterNotNull()
        .joinToString(", ")
