package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.aicontract.CYL_ACTION_SCHEMA_VERSION
import com.changeyourlife.cyl.backend.domain.AiJobPhases
import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import com.changeyourlife.cyl.backend.model.ai.AiImageInput
import com.changeyourlife.cyl.backend.model.ai.AiDiagnostics
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class AiService(
    private val lmStudioBaseUrl: String? = null,
    private val lmStudioApiKey: String? = null,
    private val lmStudioModel: String = "qwen/qwen3.5-9b",
    private val lmStudioVisionModels: List<String> = listOf("qwen/qwen3.5-9b"),
    private val glmApiKey: String? = null,
    private val geminiApiKey: String? = null,
    private val openRouterApiKey: String? = null,
    private val openRouterModel: String = "openai/gpt-oss-20b:free",
    private val openRouterVisionModels: List<String> = listOf(
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-3-4b-it:free",
        "google/gemini-2.0-flash-exp:free",
    ),
    private val actionPlanner: AiActionPlanner = AiActionPlanner(),
    private val actionSchemaValidator: AiActionSchemaValidator = AiActionSchemaValidator(),
    private val modelActionNormalizer: AiModelActionNormalizer = AiModelActionNormalizer(actionSchemaValidator),
    private val promptActionRecovery: AiPromptActionRecovery = AiPromptActionRecovery(actionSchemaValidator),
    private val completionProvider: ((List<ChatMessage>, Boolean, Double) -> String)? = null,
    private val webSearchService: WebSearchService? = null,
    private val pdfAttachmentTextExtractor: PdfAttachmentTextExtractor = PdfAttachmentTextExtractor(),
) {
    private val logger = LoggerFactory.getLogger(AiService::class.java)
    private val actionContextBuilder = AiActionContextBuilder()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val requestJson = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    // Timeouts for AI provider requests
    private val AiConnectTimeout: Duration = Duration.ofSeconds(10)
    private val AiRequestTimeout: Duration = Duration.ofSeconds(60)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(AiConnectTimeout)
        .build()

    private val completionEndpoints: List<CompletionEndpoint> = buildList {
        if (!lmStudioBaseUrl.isNullOrBlank()) {
            add(
                CompletionEndpoint(
                    provider = "lmstudio",
                    model = lmStudioModel.ifBlank { DefaultLmStudioModel },
                    url = lmStudioBaseUrl.orEmpty().toChatCompletionsUrl(),
                    apiKey = lmStudioApiKey,
                ),
            )
        }
        if (!openRouterApiKey.isNullOrBlank()) {
            add(
                CompletionEndpoint(
                    provider = "openrouter",
                    model = openRouterModel.ifBlank { "openai/gpt-oss-20b:free" },
                    url = OpenRouterCompletionsUrl,
                    apiKey = openRouterApiKey,
                ),
            )
        }
        if (!geminiApiKey.isNullOrBlank()) {
            add(
                CompletionEndpoint(
                    provider = "gemini",
                    model = "gemini-3.5-flash",
                    url = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    apiKey = geminiApiKey,
                ),
            )
        }
        if (!glmApiKey.isNullOrBlank()) {
            add(
                CompletionEndpoint(
                    provider = "glm",
                    model = "glm-4-flash",
                    url = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    apiKey = glmApiKey,
                ),
            )
        }
    }

    val activeProvider: String = completionEndpoints.firstOrNull()?.provider ?: "sandbox"

    val activeModel: String = completionEndpoints.firstOrNull()?.model ?: "mock"

    val isMockMode: Boolean = activeProvider == "sandbox"

    val visionPipelineVersion: String = VisionPipelineVersion
    val visionMaxImageDimension: Int = VisionMaxImageDimension
    val visionMaxImageBytes: Int = VisionMaxImageBytes
    val lmStudioVisionModelLabel: String = lmStudioVisionModels.joinToString(",")

    fun chat(
        messages: List<ChatMessage>,
        images: List<AiImageInput> = emptyList(),
    ): String {
        val preparedMessages = messages.withImageContext(images)
        completionProvider?.invoke(preparedMessages, false, 0.7)?.let { reply -> return reply }
        if (isMockMode) {
            val userMsg = preparedMessages.lastOrNull { it.role == "user" }?.content.orEmpty()
            return "[AI Sandbox Mode - No API Key]\nHere is a simulated response to your question: \"$userMsg\". Add LMSTUDIO_BASE_URL or OPENROUTER_API_KEY to enable live AI answers."
        }

        return try {
            chatCompletions(preparedMessages, temperature = 0.7)
        } catch (e: Exception) {
            "Error contacting AI completions endpoint: ${e.localizedMessage}"
        }
    }

    @Serializable
    data class AiTableColumnItem(
        val name: String = "",
        val type: String = "Text",
        val options: List<String> = emptyList(),
        val dateFormat: String = "",
        val timeFormat: String = "",
        val dateReminder: String = "",
        val timezoneLabel: String = "",
        val isHidden: Boolean? = null,
        val isRequired: Boolean? = null,
        val wrapContent: Boolean? = null,
        val widthDp: Int? = null,
        val defaultValue: String = "",
        val description: String = "",
        val formula: String = "",
        val relationTargetTableId: String = "",
        val rollupRelationColumnName: String = "",
        val rollupTargetColumnName: String = "",
        val rollupAggregation: String = "",
    )

    @Serializable
    data class AiActionItem(
        val type: String = "",
        val title: String = "",
        val targetTitle: String = "",
        val content: String = "",
        val blockType: String = "",
        val blockId: String = "",
        val blockText: String = "",
        val textToFormat: String = "",
        val format: String = "",
        val linkUrl: String = "",
        val color: String = "",
        val highlight: String = "",
        val rangeStart: Int? = null,
        val rangeEnd: Int? = null,
        val mediaUri: String = "",
        val mediaName: String = "",
        val mediaMimeType: String = "",
        val mediaSizeBytes: Long = 0,
        val mediaId: String = "",
        val isChecked: Boolean? = null,
        val propertyName: String = "",
        val newPropertyName: String = "",
        val propertyType: String = "",
        val value: String = "",
        val moveDirection: String = "",
        val parentPageId: String = "",
        val parentPageTitle: String = "",
        val sourcePageId: String = "",
        val sourcePageTitle: String = "",
        val sourceTableBlockId: String = "",
        val sourceTableTitle: String = "",
        val moduleType: String = "",
        val tableTitle: String = "",
        val tableView: String = "",
        val viewId: String = "",
        val viewName: String = "",
        val newViewName: String = "",
        val calendarDateColumnId: String = "",
        val calendarDateColumnName: String = "",
        val timelineStartColumnId: String = "",
        val timelineStartColumnName: String = "",
        val timelineEndColumnId: String = "",
        val timelineEndColumnName: String = "",
        val dashboardMetricColumnId: String = "",
        val dashboardMetricColumnName: String = "",
        val dashboardGroupColumnId: String = "",
        val dashboardGroupColumnName: String = "",
        val columnId: String = "",
        val columnName: String = "",
        val newColumnName: String = "",
        val columnType: String = "",
        val options: List<String> = emptyList(),
        val optionId: String = "",
        val optionName: String = "",
        val newOptionName: String = "",
        val optionColor: String = "",
        val dateFormat: String = "",
        val timeFormat: String = "",
        val dateReminder: String = "",
        val timezoneLabel: String = "",
        val isHidden: Boolean? = null,
        val isRequired: Boolean? = null,
        val wrapContent: Boolean? = null,
        val widthDp: Int? = null,
        val defaultValue: String = "",
        val clearDefaultValue: Boolean? = null,
        val description: String = "",
        val clearDescription: Boolean? = null,
        val formula: String = "",
        val relationTargetTableId: String = "",
        val relationTargetTableTitle: String = "",
        val rollupRelationColumnId: String = "",
        val rollupRelationColumnName: String = "",
        val rollupTargetColumnId: String = "",
        val rollupTargetColumnName: String = "",
        val rollupAggregation: String = "",
        val sortDirection: String = "",
        val filterQuery: String = "",
        val filterOperator: String = "",
        val groupByColumnId: String = "",
        val groupByColumnName: String = "",
        val rowId: String = "",
        val rowIds: List<String> = emptyList(),
        val rowTitle: String = "",
        val newRowTitle: String = "",
        val rowBlockId: String = "",
        val targetIndex: Int? = null,
        val cellValues: Map<String, String> = emptyMap(),
        val relationRowIds: List<String> = emptyList(),
        val tableColumns: List<AiTableColumnItem> = emptyList(),
        val tableRows: List<Map<String, String>> = emptyList(),
        val delayMinutes: Long? = null
    )

    data class AiActionResult(
        val reply: String,
        val actions: List<AiActionItem>,
        val validationIssues: List<AiActionValidationIssue> = emptyList(),
        val diagnostics: AiDiagnostics = AiDiagnostics(),
        val source: AiActionSource = AiActionSource.None,
    )

    enum class AiActionSource {
        None,
        Model,
        PromptRecovery,
    }

    fun initialDiagnosticsFor(images: List<AiImageInput>): AiDiagnostics =
        images.toAttachmentDiagnostics(phase = "queued")

    suspend fun chatWithActions(
        messages: List<ChatMessage>,
        pages: List<AiPageContext> = emptyList(),
        tasks: List<AiTaskContext> = emptyList(),
        clientDate: String = "",
        clientTimezone: String = "",
        images: List<AiImageInput> = emptyList(),
        webSearchEnabled: Boolean = false,
        webSearchQuery: String = "",
        progress: AiJobProgressSink? = null,
    ): AiActionResult {
        if (images.isNotEmpty()) {
            progress?.invoke(
                AiJobPhases.VisionProcessing,
                initialDiagnosticsFor(images).copy(phase = AiJobPhases.VisionProcessing),
            )
        }
        val attachmentPreparedMessages = messages.withAttachmentContext(images)
        val userMessage = messages.lastOrNull { message -> message.role.equals("user", ignoreCase = true) }
            ?.content
            .orEmpty()
        var preparedMessages = attachmentPreparedMessages
        val shouldUseWebSearch = webSearchEnabled || userMessage.shouldAutoUseWebSearch()
        if (shouldUseWebSearch) {
            val searchQuery = webSearchQuery.ifBlank { userMessage }
            progress?.invoke(
                AiJobPhases.WebSearching,
                preparedMessages.diagnostics.copy(
                    phase = AiJobPhases.WebSearching,
                    webSearchAttempted = true,
                    webSearchStatus = "running",
                ),
            )
            val webContext = webSearchService?.search(searchQuery)
                ?: WebSearchContext(query = searchQuery, status = "disabled")
            logger.info(
                "AI web search prepared: requested={}, auto={}, status={}, provider={}, results={}, query='{}'",
                webSearchEnabled,
                !webSearchEnabled,
                webContext.status,
                webContext.provider,
                webContext.results.size,
                searchQuery.take(160),
            )
            preparedMessages = preparedMessages.copy(
                messages = preparedMessages.messages.withWebSearchContext(webContext),
                diagnostics = preparedMessages.diagnostics.withWebSearchContext(webContext),
            )
        }
        progress?.invoke(
            AiJobPhases.Planning,
            preparedMessages.diagnostics.copy(phase = AiJobPhases.Planning),
        )

        val reply = if (isMockMode) {
            "[AI Sandbox Mode - No API Key]\nHere is a simulated response to your question: \"$userMessage\". Add LMSTUDIO_BASE_URL or OPENROUTER_API_KEY to enable live AI answers."
        } else {
            chatForActions(
                messages = preparedMessages.messages,
                pages = pages,
                tasks = tasks,
                clientDate = clientDate,
                clientTimezone = clientTimezone,
            ).ifBlank { "I received your message, but the AI returned an empty reply." }
        }

        progress?.invoke(
            AiJobPhases.ExecutingAction,
            preparedMessages.diagnostics.copy(phase = AiJobPhases.ExecutingAction),
        )
        val modelResult = recoverActionFromModelReply(
            reply = reply,
            prompt = userMessage,
            pages = pages,
        )?.copy(source = AiActionSource.Model)

        val promptResult = if (modelResult != null && reply.canUsePromptActionRecovery()) {
            recoverActionFromPrompt(prompt = userMessage, pages = pages)
                ?.copy(source = AiActionSource.PromptRecovery)
        } else {
            null
        }
        selectActionResultForPrompt(
            prompt = userMessage,
            modelResult = modelResult,
            promptResult = promptResult,
        )?.let { result ->
            return AiRetrievalActionBoundary.enforce(
                result = result.copy(diagnostics = preparedMessages.diagnostics),
                pages = pages,
            )
        }

        return AiActionResult(
            reply = reply,
            actions = emptyList(),
            diagnostics = preparedMessages.diagnostics,
        )
    }

    private fun chatForActions(
        messages: List<ChatMessage>,
        pages: List<AiPageContext>,
        tasks: List<AiTaskContext>,
        clientDate: String,
        clientTimezone: String,
    ): String {
        val actionMessages = buildActionPlannerMessages(
            messages = messages,
            pages = pages,
            tasks = tasks,
            clientDate = clientDate,
            clientTimezone = clientTimezone,
        )
        completionProvider?.invoke(actionMessages, true, 0.15)?.let { reply -> return reply }
        return chatCompletionsForActions(actionMessages, temperature = 0.15)
    }

    private fun buildActionPlannerMessages(
        messages: List<ChatMessage>,
        pages: List<AiPageContext>,
        tasks: List<AiTaskContext>,
        clientDate: String,
        clientTimezone: String,
    ): List<ChatMessage> {
        val latestUserPrompt = messages
            .lastOrNull { message -> message.role.equals("user", ignoreCase = true) }
            ?.content
            .orEmpty()
        val contextResult = actionContextBuilder.build(
            pages = pages,
            tasks = tasks,
            latestUserPrompt = latestUserPrompt,
            clientDate = clientDate,
            clientTimezone = clientTimezone,
        )
        if (contextResult.coverage == "PARTIAL") {
            logger.warn(
                "AI action context is partial: detailedPages={}/{}, rows={}/{}",
                contextResult.detailedPageCount,
                contextResult.totalPageCount,
                contextResult.includedRowCount,
                contextResult.totalRowCount,
            )
        } else {
            logger.debug(
                "AI action context prepared: detailedPages={}/{}, rows={}/{}",
                contextResult.detailedPageCount,
                contextResult.totalPageCount,
                contextResult.includedRowCount,
                contextResult.totalRowCount,
            )
        }
        val context = contextResult.text

        val actionContractPrompt = AiActionContractSchema.promptInstructions()
        val systemPrompt = """
            You are CYL AI, the planner and editor for the ChangeYourLife app.
            Understand Malay, Indonesian, and English naturally, including typos and mixed language.

            Return ONLY one valid JSON object:
            {
              "reply": "short natural reply in the user's language",
              "actions": []
            }

            If the user is only chatting, asking questions, brainstorming, or planning, keep "actions" empty.
            If the user asks to create, update, delete, rename, add a row, edit a table, edit a page, or change a property, produce CYL actions.
            Only the latest user message authorizes actions. Never repeat a mutation from an older message when the latest message is only a greeting or unrelated chat.
            Do not answer with markdown tables when the user wants data created in the app. Convert the idea into table/page actions.
            Internal ids supplied by CYL context may be used inside action fields such as rowId, blockId, or columnId.
            Never expose those ids in the user-visible reply.
            If CYL_WEB_CONTEXT is present, use those web results for current/live questions and cite URLs when useful.
            If CYL_WEB_CONTEXT says no reliable web result is available, say the web source could not retrieve results. Do not claim you cannot browse based only on model limitations.
            CYL_WORKSPACE_MANIFEST lists every supplied page/table and its authoritative total counts.
            CYL_CONTEXT_DETAILS contains prioritized data. CYL_CONTEXT_COVERAGE=FULL means every supplied detail is present.
            CYL_CONTEXT_COVERAGE=PARTIAL means omitted records still exist. Never treat omitted rows/blocks as empty or claim a workspace-wide total from partial context.
            CYL_RETRIEVAL_BOUNDARY is a privacy boundary, not a relevance hint.
