package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import com.changeyourlife.cyl.backend.model.ai.AiImageInput
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
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

    val apiKeyLength: Int = completionEndpoints.firstOrNull()?.apiKey?.length ?: 0

    val apiKeyInspect: String
        get() {
            val endpoint = completionEndpoints.firstOrNull() ?: return "null"
            val key = endpoint.apiKey ?: return "provider=${endpoint.provider}, key=null"
            if (key.isBlank()) return "blank"
            val len = key.length
            val first = key.substring(0, minOf(5, len))
            val last = key.substring(maxOf(0, len - 5))
            return "provider=$activeProvider, len=$len, first='$first', last='$last'"
        }

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
        val isChecked: Boolean? = null,
        val propertyName: String = "",
        val propertyType: String = "Text",
        val value: String = "",
        val moduleType: String = "",
        val tableTitle: String = "",
        val tableView: String = "Table",
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
        val columnType: String = "Text",
        val options: List<String> = emptyList(),
        val formula: String = "",
        val relationTargetTableId: String = "",
        val relationTargetTableTitle: String = "",
        val rollupRelationColumnId: String = "",
        val rollupRelationColumnName: String = "",
        val rollupTargetColumnId: String = "",
        val rollupTargetColumnName: String = "",
        val rollupAggregation: String = "",
        val sortDirection: String = "Ascending",
        val filterQuery: String = "",
        val groupByColumnId: String = "",
        val groupByColumnName: String = "",
        val rowId: String = "",
        val rowTitle: String = "",
        val newRowTitle: String = "",
        val rowBlockId: String = "",
        val targetIndex: Int? = null,
        val cellValues: Map<String, String> = emptyMap(),
        val tableColumns: List<AiTableColumnItem> = emptyList(),
        val tableRows: List<Map<String, String>> = emptyList(),
        val delayMinutes: Long? = null
    )

    data class AiActionResult(
        val reply: String,
        val actions: List<AiActionItem>,
        val validationIssues: List<AiActionValidationIssue> = emptyList(),
    )

    fun chatWithActions(
        messages: List<ChatMessage>,
        pages: List<AiPageContext> = emptyList(),
        tasks: List<AiTaskContext> = emptyList(),
        clientDate: String = "",
        clientTimezone: String = "",
        images: List<AiImageInput> = emptyList(),
    ): AiActionResult {
        val preparedMessages = messages.withImageContext(images)
        val userMessage = messages.lastOrNull { message -> message.role.equals("user", ignoreCase = true) }
            ?.content
            .orEmpty()

        val reply = if (isMockMode) {
            "[AI Sandbox Mode - No API Key]\nHere is a simulated response to your question: \"$userMessage\". Add LMSTUDIO_BASE_URL or OPENROUTER_API_KEY to enable live AI answers."
        } else {
            chatForActions(
                messages = preparedMessages,
                pages = pages,
                tasks = tasks,
                clientDate = clientDate,
                clientTimezone = clientTimezone,
            ).ifBlank { "I received your message, but the AI returned an empty reply." }
        }

        val modelResult = recoverActionFromModelReply(
            reply = reply,
            prompt = userMessage,
            pages = pages,
        )

        val promptResult = if (reply.canUsePromptActionRecovery()) {
            recoverActionFromPrompt(prompt = userMessage, pages = pages)
        } else {
            null
        }
        selectActionResultForPrompt(
            prompt = userMessage,
            modelResult = modelResult,
            promptResult = promptResult,
        )?.let { result -> return result }

        return AiActionResult(reply = reply, actions = emptyList())
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
        return runCatching {
            chatCompletionsJson(actionMessages, temperature = 0.15)
        }.getOrElse {
            chat(actionMessages)
        }
    }

    private fun buildActionPlannerMessages(
        messages: List<ChatMessage>,
        pages: List<AiPageContext>,
        tasks: List<AiTaskContext>,
        clientDate: String,
        clientTimezone: String,
    ): List<ChatMessage> {
        val context = buildString {
            appendLine("Client date: ${clientDate.ifBlank { "unknown" }}")
            appendLine("Client timezone: ${clientTimezone.ifBlank { "unknown" }}")
            appendLine()
            appendLine("Pages:")
            if (pages.isEmpty()) {
                appendLine("- none")
            } else {
                pages.take(MaxActionContextPages).forEach { page ->
                    appendLine("- title=\"${page.title.ifBlank { "Untitled" }}\"")
                    val tableBlocks = page.blocks.filter { block ->
                        block.type.equals("DatabaseTable", ignoreCase = true) ||
                            block.tableTitle.isNotBlank()
                    }
                    if (tableBlocks.isNotEmpty()) {
                        tableBlocks.take(MaxActionContextTablesPerPage).forEach { block ->
                            appendLine("  table=\"${block.tableTitle.ifBlank { "Table" }}\" ${block.text.take(MaxActionContextBlockText)}")
                        }
                    }
                    val normalBlocks = page.blocks.filterNot { block -> block in tableBlocks }
                    if (normalBlocks.isNotEmpty()) {
                        normalBlocks.take(MaxActionContextBlocksPerPage).forEach { block ->
                            appendLine("  block ${block.type}: ${block.text.take(MaxActionContextBlockText)}")
                        }
                    }
                }
            }
            appendLine()
            appendLine("Open tasks:")
            if (tasks.isEmpty()) {
                appendLine("- none")
            } else {
                tasks.take(MaxActionContextTasks).forEach { task ->
                    appendLine("- ${task.title}")
                }
            }
        }

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
            Do not answer with markdown tables when the user wants data created in the app. Convert the idea into table/page actions.
            Do not expose internal ids. Use page/table/block/row/column names from context.

            Supported action types:
            CREATE_PAGE, RENAME_PAGE, UPDATE_PAGE,
            APPEND_BLOCK, ADD_BLOCK, UPDATE_BLOCK, FORMAT_BLOCK_TEXT, DELETE_BLOCK, DELETE_ALL_BLOCKS,
            ADD_PROPERTY, UPDATE_PROPERTY, DELETE_PROPERTY,
            CREATE_DATABASE, CREATE_TABLE, RENAME_TABLE, ADD_TABLE_COLUMN, DELETE_TABLE_COLUMN,
            RENAME_TABLE_COLUMN, UPDATE_TABLE_COLUMN_TYPE, UPDATE_TABLE_COLUMN_CONFIG,
            ADD_TABLE_ROW, UPDATE_TABLE_ROW, DELETE_TABLE_ROW, UPDATE_TABLE_CELL,
            ADD_ROW_PAGE_BLOCK, UPDATE_ROW_PAGE_BLOCK, DELETE_ROW_PAGE_BLOCK,
            CHANGE_TABLE_VIEW, SET_TABLE_VIEW_CONFIG, SORT_TABLE, FILTER_TABLE, GROUP_TABLE.

            Main fields you may use:
            targetTitle, title, content, blockType, blockText, propertyName, propertyType, value,
            tableTitle, tableView, columnName, newColumnName, columnType, rowTitle, newRowTitle,
            cellValues, tableColumns, tableRows, options, formula, sortDirection, filterQuery, groupByColumnName,
            textToFormat, format, linkUrl, color, highlight, rangeStart, rangeEnd.

            Table column types:
            Text, Number, Select, MultiSelect, Status, Date, Person, Files, Checkbox, URL, Email, Phone,
            Formula, Relation, Rollup, CreatedTime, CreatedBy, LastEditedTime, LastEditedBy, Button, Place, ID.

            Decision rules:
            - Home request to create a new tracker/jadual/table/page: use CREATE_PAGE with tableTitle, tableColumns, and tableRows when useful.
            - Request inside or mentioning an existing page to create a table: use CREATE_DATABASE with targetTitle.
            - Request to add spending/expense/record to an existing table: use ADD_TABLE_ROW. Do not create a new table unless user asks for a new table/page.
            - If a page is mentioned in CYL_MENTION_CONTEXT, treat that as the exact target page.
            - If one current/mentioned page is clearly in context and user does not mention a page, use that page.
            - For date words like harini/today, use the client date.
            - For money like "29 ringgit", put the numeric value in an amount/price/cost column if such column exists or create a Number column.
            - For table creation, infer sensible columns and rows from the user's intent instead of using fixed templates.
            - For Select, MultiSelect, or Status dropdown values, include options as a string array on the column or action.
            - If the user asks for a category dropdown, use columnType "Select" and include category options.
            - For multi-step requests, return multiple actions in order.

            Examples:
            User: buatkan page baru untuk bulan 7 punya monthly expenses,dengan gaji 1488
            JSON: {"reply":"Siap - saya buat page monthly expenses bulan 7.","actions":[{"type":"CREATE_PAGE","title":"Monthly Expenses July","tableTitle":"Monthly Expenses","tableColumns":[{"name":"Category","type":"Select","options":["Income","Rent","Food","Transport","Makeup","Other"]},{"name":"Budget","type":"Number"},{"name":"Actual","type":"Number"},{"name":"Status","type":"Status","options":["Planned","Paid","Over budget"]},{"name":"Notes","type":"Text"}],"tableRows":[{"Category":"Income","Budget":"1488","Actual":"1488","Status":"Paid"}]}]}

            User: tambah property Category dropdown dengan Food, Fuel, Makeup
            JSON: {"reply":"Siap - saya tambah dropdown Category.","actions":[{"type":"ADD_TABLE_COLUMN","columnName":"Category","columnType":"Select","options":["Food","Fuel","Makeup"]}]}

            User: saya guna 29 ringgit harini beli makeup
            JSON: {"reply":"Siap - saya tambah rekod belanja itu.","actions":[{"type":"ADD_TABLE_ROW","rowTitle":"makeup","cellValues":{"Item":"makeup","Amount":"29","Date":"${clientDate.ifBlank { "today" }}"}}]}

            User: @Budget Tracker ubah nama table jadi Expenses
            JSON: {"reply":"Siap - saya rename table itu.","actions":[{"type":"RENAME_TABLE","targetTitle":"Budget Tracker","title":"Expenses"}]}

            User: bold perkataan ayam dalam block jadual penjagaan
            JSON: {"reply":"Siap - saya format teks itu.","actions":[{"type":"FORMAT_BLOCK_TEXT","blockText":"jadual penjagaan","textToFormat":"ayam","format":"Bold"}]}

            Context:
            $context
        """.trimIndent()

        return listOf(ChatMessage(role = "system", content = systemPrompt)) + messages
    }

    internal fun selectActionResultForPrompt(
        prompt: String,
        modelResult: AiActionResult?,
        promptResult: AiActionResult?,
    ): AiActionResult? = actionPlanner.selectActionResult(
        prompt = prompt,
        modelResult = modelResult,
        promptResult = promptResult,
    )

    internal fun recoverActionFromModelReply(
        reply: String,
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? = modelActionNormalizer.recoverActionFromModelReply(
        reply = reply,
        prompt = prompt,
        pages = pages,
    )

    internal fun recoverActionFromPrompt(
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? = promptActionRecovery.recoverActionFromPrompt(
        prompt = prompt,
        pages = pages,
    )

    private fun List<ChatMessage>.withImageContext(images: List<AiImageInput>): List<ChatMessage> {
        val context = buildImageContext(images)
        if (context.isBlank()) return this
        val lastUserIndex = indexOfLast { message -> message.role.equals("user", ignoreCase = true) }
        if (lastUserIndex < 0) {
            return this + ChatMessage(role = "user", content = context)
        }
        return mapIndexed { index, message ->
            if (index == lastUserIndex) {
                message.copy(
                    content = """
                        ${message.content}

                        $context
                    """.trimIndent(),
                )
            } else {
                message
            }
        }
    }

    private fun buildImageContext(images: List<AiImageInput>): String {
        val validImages = images
            .asSequence()
            .filter { image ->
                image.dataUrl.startsWith("data:image/", ignoreCase = true) &&
                    image.dataUrl.contains(";base64,", ignoreCase = true)
            }
            .take(MaxVisionImages)
            .toList()
        val textFiles = images
            .asSequence()
            .filter { file -> file.textContent.isNotBlank() }
            .take(MaxTextContextFiles)
            .toList()
        if (validImages.isEmpty() && textFiles.isEmpty()) return ""

        val imageSummary = when {
            validImages.isEmpty() -> ""
            else -> analyzeImagesWithVisionFallback(validImages)
        }
        val textSummary = textFiles.joinToString(separator = "\n\n") { file ->
            """
                File: ${file.name.ifBlank { "attached file" }}
                MIME: ${file.mimeType.ifBlank { "text/plain" }}
                Bytes: ${file.sizeBytes}
                Content:
                ${file.textContent.take(MaxTextContextChars)}
            """.trimIndent()
        }

        return """
            CYL_FILE_CONTEXT:
            The user attached ${validImages.size} image(s) and ${textFiles.size} readable text file(s).
            Use this extracted attachment context as user-provided evidence.
            If Image context says image reading failed or unavailable, tell the user the attachment reached CYL but CYL could not extract readable image context. Ask the user to retry or check the LM Studio vision model/tunnel. Do not mention provider status codes unless the user asks.
            Do not mention internal attachment pipeline details unless the user asks.
            ${if (imageSummary.isNotBlank()) "Image context:\n$imageSummary" else ""}
            ${if (textSummary.isNotBlank()) "Text file context:\n$textSummary" else ""}
        """.trimIndent()
    }

    private fun analyzeImagesWithVisionFallback(images: List<AiImageInput>): String {
        val failures = mutableListOf<String>()

        if (!lmStudioBaseUrl.isNullOrBlank()) {
            val lmStudioResult = analyzeImagesWithLmStudioFallback(images)
            if (!lmStudioResult.isVisionFailure()) return lmStudioResult
            failures += lmStudioResult
            return failures.joinToString(separator = "\n\n")
        }

        if (!openRouterApiKey.isNullOrBlank()) {
            val openRouterResult = analyzeImagesWithOpenRouterFallback(images)
            if (!openRouterResult.isVisionFailure()) {
                return openRouterResult
            }
            failures += openRouterResult
        }

        if (failures.isEmpty()) {
            return "Image reading unavailable because LMSTUDIO_BASE_URL or OPENROUTER_API_KEY is not configured."
        }
        return failures.joinToString(separator = "\n\n")
    }

    private fun analyzeImagesWithLmStudioFallback(images: List<AiImageInput>): String {
        val baseUrl = lmStudioBaseUrl?.trim().orEmpty()
        val completionsUrl = baseUrl.toChatCompletionsUrl()
        val models = lmStudioVisionModels
            .ifEmpty { DefaultLmStudioVisionModels }
            .map { model -> model.trim() }
            .filter { model -> model.isNotBlank() }
            .distinct()
            .take(MaxVisionFallbackModels)
        val failures = mutableListOf<String>()

        models.forEach { model ->
            runCatching {
                analyzeImagesWithVisionProvider(
                    images = images,
                    model = model,
                    defaultModel = DefaultLmStudioVisionModels.first(),
                    completionsUrl = completionsUrl,
                    apiKey = lmStudioApiKey.orEmpty(),
                )
            }.onSuccess { content ->
                if (content.isNotBlank()) {
                    return """
                        Vision model used: $model
                        $content
                    """.trimIndent()
                }
                failures += "$model: empty response"
            }.onFailure { error ->
                failures += "$model: ${error.compactVisionError()}"
            }
        }

        return """
            Image reading failed after trying ${models.size} LM Studio vision model(s).
            CYL is configured to use LM Studio for image reading, so OpenRouter vision fallback was skipped.
            Endpoint: $completionsUrl
            Tried:
            ${failures.joinToString(separator = "\n") { failure -> "- $failure" }}
        """.trimIndent()
    }

    private fun analyzeImagesWithOpenRouterFallback(images: List<AiImageInput>): String {
        val models = openRouterVisionModels
            .ifEmpty { DefaultVisionModels }
            .map { model -> model.trim() }
            .filter { model -> model.isNotBlank() }
            .distinct()
            .take(MaxVisionFallbackModels)
        val failures = mutableListOf<String>()

        models.forEach { model ->
            runCatching {
                analyzeImagesWithOpenRouter(
                    images = images,
                    model = model,
                )
            }.onSuccess { content ->
                if (content.isNotBlank()) {
                    return """
                        Vision model used: $model
                        $content
                    """.trimIndent()
                }
                failures += "$model: empty response"
            }.onFailure { error ->
                failures += "$model: ${error.compactVisionError()}"
            }
        }

        return """
            Image reading failed after trying ${models.size} vision model(s).
            Tried:
            ${failures.joinToString(separator = "\n") { failure -> "- $failure" }}
        """.trimIndent()
    }

    private fun analyzeImagesWithOpenRouter(
        images: List<AiImageInput>,
        model: String,
    ): String = analyzeImagesWithVisionProvider(
        images = images,
        model = model,
        defaultModel = DefaultVisionModels.first(),
        completionsUrl = OpenRouterCompletionsUrl,
        apiKey = openRouterApiKey.orEmpty(),
    )

    private fun analyzeImagesWithVisionProvider(
        images: List<AiImageInput>,
        model: String,
        defaultModel: String,
        completionsUrl: String,
        apiKey: String,
    ): String {
        val prompt = buildString {
            appendLine("Read the attached image(s) for the CYL app.")
            appendLine("Extract only useful facts for a Notion-like personal productivity app.")
            appendLine("Return compact JSON with keys: summary, detectedText, tables, amounts, dates, people, places, suggestedPageTitle, suggestedProperties, confidence.")
            appendLine("If the image is a receipt, bill, spreadsheet, screenshot, table, calendar, or note, preserve important rows and values.")
            appendLine("Do not invent data that is not visible.")
            images.forEachIndexed { index, image ->
                appendLine("Image ${index + 1}: name=${image.name.ifBlank { "image" }}, mime=${image.mimeType.ifBlank { "image/*" }}, bytes=${image.sizeBytes}")
            }
        }

        val body = buildJsonObject {
            put("model", model.ifBlank { defaultModel })
            put("temperature", 0.0)
            put("max_tokens", 900)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", prompt)
                                        },
                                    )
                                    images.forEach { image ->
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", image.dataUrl)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(completionsUrl))
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://changeyourlife.local")
            .header("X-Title", "ChangeYourLife")
            .timeout(AiRequestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        val request = requestBuilder.build()

        var lastError: Exception? = null
        repeat(VisionRequestMaxAttempts) { attempt ->
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val apiResponse = json.decodeFromString<ApiResponse>(response.body())
                return apiResponse.choices.firstOrNull()
                    ?.message
                    ?.content
                    ?.ifBlank { null }
                    ?: throw Exception("Vision model returned an empty response.")
            }

            val error = Exception("Vision HTTP ${response.statusCode()} - ${response.body()}")
            lastError = error
            if (!response.statusCode().isRetryableVisionStatus() || attempt == VisionRequestMaxAttempts - 1) {
                throw error
            }
            Thread.sleep(VisionRetryDelayMillis * (attempt + 1))
        }
        throw lastError ?: Exception("Vision request failed.")
    }

    private fun Int.isRetryableVisionStatus(): Boolean =
        this == 408 || this == 409 || this == 425 || this == 429 || this >= 500

    private fun Throwable.compactVisionError(): String {
        return (localizedMessage ?: message ?: this::class.simpleName.orEmpty())
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .take(320)
    }

    private fun String.isVisionFailure(): Boolean =
        trim().startsWith("Image reading failed", ignoreCase = true)

    private fun String.toChatCompletionsUrl(): String {
        val normalized = trim().trimEnd('/')
        return when {
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }
    }

    private fun chatCompletionsJson(
        messages: List<ChatMessage>,
        temperature: Double = 0.2,
    ): String = chatCompletions(
        messages = messages,
        temperature = temperature,
        responseFormat = ApiResponseFormat("json_object"),
    )

    private fun chatCompletions(
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        responseFormat: ApiResponseFormat? = null,
    ): String {
        val failures = mutableListOf<String>()
        completionEndpoints.forEach { endpoint ->
            runCatching {
                sendChatCompletion(
                    endpoint = endpoint,
                    messages = messages,
                    temperature = temperature,
                    responseFormat = responseFormat,
                )
            }.onSuccess { content ->
                if (content.isNotBlank()) return content
                failures += "${endpoint.provider}/${endpoint.model}: empty response"
            }.onFailure { error ->
                failures += "${endpoint.provider}/${endpoint.model}: ${error.compactVisionError()}"
            }
        }
        throw Exception(
            "All AI providers failed. Tried: ${failures.joinToString(separator = " | ")}",
        )
    }

    private fun sendChatCompletion(
        endpoint: CompletionEndpoint,
        messages: List<ChatMessage>,
        temperature: Double,
        responseFormat: ApiResponseFormat?,
    ): String {
        fun requestBody(format: ApiResponseFormat?): String =
            json.encodeToString(
                ApiRequest(
                    model = endpoint.model,
                    messages = messages.map { ApiMessage(it.role, it.content) },
                    temperature = temperature,
                    response_format = format,
                )
            )

        fun send(format: ApiResponseFormat?): HttpResponse<String> {
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.url))
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://changeyourlife.local")
                .header("X-Title", "ChangeYourLife")
                .timeout(AiRequestTimeout) // read + response timeout
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(format)))
            if (!endpoint.apiKey.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${endpoint.apiKey}")
            }
            return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        }

        val response = send(responseFormat).let { initialResponse ->
            if (
                initialResponse.statusCode() != 200 &&
                endpoint.provider == "lmstudio" &&
                responseFormat != null
            ) {
                send(null)
            } else {
                initialResponse
            }
        }
        return if (response.statusCode() == 200) {
            val apiResponse = json.decodeFromString<ApiResponse>(response.body())
            apiResponse.choices.firstOrNull()
                ?.message
                ?.content
                ?.ifBlank { null }
                ?: throw Exception("AI provider returned an empty response.")
        } else {
            throw Exception("${endpoint.provider} HTTP ${response.statusCode()} - ${response.body()}")
        }
    }

    private data class CompletionEndpoint(
        val provider: String,
        val model: String,
        val url: String,
        val apiKey: String?,
    )

    // Shared chat-completions request/response models.
    @Serializable
    private data class ApiRequest(
        val model: String,
        val messages: List<ApiMessage>,
        val temperature: Double = 0.7,
        val response_format: ApiResponseFormat? = null
    )

    @Serializable
    private data class ApiMessage(
        val role: String = "",
        val content: String? = "",
    )

    @Serializable
    private data class ApiResponseFormat(
        val type: String
    )

    @Serializable
    private data class ApiResponse(
        val choices: List<ApiChoice>
    )

    @Serializable
    private data class ApiChoice(
        val message: ApiMessage
    )

    private companion object {
        const val MaxActionContextPages = 25
        const val MaxActionContextTablesPerPage = 5
        const val MaxActionContextBlocksPerPage = 6
        const val MaxActionContextTasks = 20
        const val MaxActionContextBlockText = 260
        const val MaxVisionImages = 4
        const val MaxVisionFallbackModels = 5
        const val VisionRequestMaxAttempts = 2
        const val VisionRetryDelayMillis = 900L
        const val MaxTextContextFiles = 4
        const val MaxTextContextChars = 16_000
        val AiRequestTimeout: Duration = Duration.ofMinutes(5)
        const val DefaultLmStudioModel = "qwen/qwen3.5-9b"
        val DefaultLmStudioVisionModels = listOf("qwen/qwen3.5-9b")
        const val OpenRouterCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions"
        val DefaultVisionModels = listOf(
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-3-4b-it:free",
            "google/gemini-2.0-flash-exp:free",
        )
    }
}

internal fun String.cleanAiJson(): String {
    var cleaned = trim()
    if (cleaned.startsWith("```json", ignoreCase = true)) {
        cleaned = cleaned.substringAfter('\n', missingDelimiterValue = cleaned.removePrefix("```json"))
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substringAfter('\n', missingDelimiterValue = cleaned.removePrefix("```"))
    }
    if (cleaned.endsWith("```")) cleaned = cleaned.substringBeforeLast("```")
    cleaned = cleaned.trim()

    val objectStart = cleaned.indexOf('{')
    val arrayStart = cleaned.indexOf('[')
    val start = when {
        objectStart < 0 -> arrayStart
        arrayStart < 0 -> objectStart
        else -> minOf(objectStart, arrayStart)
    }
    if (start < 0) return cleaned

    val open = cleaned[start]
    val close = if (open == '{') '}' else ']'
    return cleaned.extractBalancedJson(start, open, close) ?: cleaned
}

private fun String.canUsePromptActionRecovery(): Boolean {
    val normalized = trim().lowercase()
    if (normalized.isBlank()) return false
    return !(
        normalized.startsWith("error:") ||
            normalized.contains("backend ai request failed") ||
            normalized.contains("error contacting ai") ||
            normalized.contains("ai provider returned an empty response") ||
            normalized.contains("[ai sandbox mode")
        )
}

private fun String.extractBalancedJson(
    start: Int,
    open: Char,
    close: Char,
): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until length) {
        val char = this[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }

        when (char) {
            '"' -> inString = true
            open -> depth++
            close -> {
                depth--
                if (depth == 0) {
                    return substring(start, index + 1).trim()
                }
            }
        }
    }
    return null
}
