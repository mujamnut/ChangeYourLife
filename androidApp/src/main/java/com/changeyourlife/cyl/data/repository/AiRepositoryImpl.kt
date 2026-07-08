package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.ai.AiApi
import com.changeyourlife.cyl.data.remote.ai.AiBlockContextDto
import com.changeyourlife.cyl.data.remote.ai.AiImageInputDto
import com.changeyourlife.cyl.data.remote.ai.AiPageContextDto
import com.changeyourlife.cyl.data.remote.ai.AiTaskContextDto
import com.changeyourlife.cyl.data.remote.ai.ChatMessageDto
import com.changeyourlife.cyl.data.remote.ai.ChatRequestDto
import com.changeyourlife.cyl.data.remote.ai.ChatWithActionsRequestDto
import com.changeyourlife.cyl.domain.repository.AiStatus
import com.changeyourlife.cyl.domain.repository.AiErrorKind
import com.changeyourlife.cyl.domain.repository.AiException
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import java.time.LocalDate
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
                apiKeyConfigured = response.apiKeyConfigured,
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
        messages: List<Pair<String, String>>,
        pages: List<AiPageContext>,
        tasks: List<Pair<String, String>>,
        clientDate: String,
        clientTimezone: String,
        images: List<AiImageAttachment>,
    ): Result<ChatActionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val header = getAuthHeader()
            val request = ChatWithActionsRequestDto(
                messages = messages.map { ChatMessageDto(role = it.first, content = it.second) },
                pages = pages.map { page ->
                    AiPageContextDto(
                        id = page.id,
                        title = page.title,
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
                            )
                        },
                    )
                },
                tasks = tasks.map { AiTaskContextDto(id = it.first, title = it.second) },
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
                    )
                },
            )
            val response = aiApi.chatWithActions(header, request)
            if (response.reply.isBlank() && response.actions.isEmpty() && response.validationIssues.isEmpty()) {
                throw AiErrorMapper.emptyResponse("chatWithActions")
            }
            AiActionContractMapper.toDomain(response)
        }.mapAiFailure()
    }
}
