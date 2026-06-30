package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AiService(
    private val glmApiKey: String? = null,
    private val geminiApiKey: String? = null,
    private val openRouterApiKey: String? = null,
    private val openRouterModel: String = "openai/gpt-oss-20b:free",
    private val actionPlanner: AiActionPlanner = AiActionPlanner(),
    private val actionSchemaValidator: AiActionSchemaValidator = AiActionSchemaValidator(),
    private val modelActionNormalizer: AiModelActionNormalizer = AiModelActionNormalizer(actionSchemaValidator),
    private val promptActionRecovery: AiPromptActionRecovery = AiPromptActionRecovery(actionSchemaValidator),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    // OpenRouter takes priority, then Gemini, then GLM, then sandbox.
    val activeProvider: String = when {
        !openRouterApiKey.isNullOrBlank() -> "openrouter"
        !geminiApiKey.isNullOrBlank() -> "gemini"
        !glmApiKey.isNullOrBlank() -> "glm"
        else -> "sandbox"
    }

    val activeModel: String = when (activeProvider) {
        "openrouter" -> openRouterModel.ifBlank { "openai/gpt-oss-20b:free" }
        "gemini" -> "gemini-3.5-flash"
        "glm" -> "glm-4-flash"
        else -> "mock"
    }

    val isMockMode: Boolean = activeProvider == "sandbox"

    val apiKeyLength: Int = when (activeProvider) {
        "openrouter" -> openRouterApiKey?.length ?: 0
        "gemini" -> geminiApiKey?.length ?: 0
        "glm" -> glmApiKey?.length ?: 0
        else -> 0
    }

    val apiKeyInspect: String
        get() {
            val key = when (activeProvider) {
                "openrouter" -> openRouterApiKey
                "gemini" -> geminiApiKey
                "glm" -> glmApiKey
                else -> null
            } ?: return "null"
            if (key.isBlank()) return "blank"
            val len = key.length
            val first = key.substring(0, minOf(5, len))
            val last = key.substring(maxOf(0, len - 5))
            return "provider=$activeProvider, len=$len, first='$first', last='$last'"
        }

    private val completionsUrl: String = when (activeProvider) {
        "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
        "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        else -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    }

    private val activeApiKey: String?
        get() = when (activeProvider) {
            "openrouter" -> openRouterApiKey
            "gemini" -> geminiApiKey
            "glm" -> glmApiKey
            else -> null
        }

    fun chat(messages: List<ChatMessage>): String {
        if (isMockMode) {
            val userMsg = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
            return "[AI Sandbox Mode - No API Key]\nHere is a simulated response to your question: \"$userMsg\". Add OPENROUTER_API_KEY to enable live AI answers."
        }

        return try {
            val body = json.encodeToString(
                ApiRequest(
                    model = activeModel,
                    messages = messages.map { ApiMessage(it.role, it.content) }
                )
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create(completionsUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $activeApiKey")
                .header("HTTP-Referer", "https://changeyourlife.local")
                .header("X-Title", "ChangeYourLife")
                .timeout(Duration.ofSeconds(90)) // read + response timeout
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val apiResponse = json.decodeFromString<ApiResponse>(response.body())
                apiResponse.choices.firstOrNull()
                    ?.message
                    ?.content
                    ?.ifBlank { null }
                    ?: "Sorry, the AI provider returned an empty response. Please try again or switch model."
            } else {
                "Error: Backend AI request failed with status code ${response.statusCode()}.\nResponse: ${response.body()}"
            }
        } catch (e: Exception) {
            "Error contacting AI completions endpoint: ${e.localizedMessage}"
        }
    }

    @Serializable
    data class AiTableColumnItem(
        val name: String = "",
        val type: String = "Text",
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
    ): AiActionResult {
        val userMessage = messages.lastOrNull { message -> message.role.equals("user", ignoreCase = true) }
            ?.content
            .orEmpty()
        recoverActionFromPrompt(
            prompt = userMessage,
            pages = pages,
        )?.takeIf { result -> result.actions.isNotEmpty() }?.let { result ->
            return result
        }

        val reply = if (isMockMode) {
            "[AI Sandbox Mode - No API Key]\nHere is a simulated response to your question: \"$userMessage\". Add OPENROUTER_API_KEY to enable live AI answers."
        } else {
            chat(messages).ifBlank { "I received your message, but the AI returned an empty reply." }
        }

        val modelResult = recoverActionFromModelReply(
            reply = reply,
            prompt = userMessage,
            pages = pages,
        )

        val promptResult = recoverActionFromPrompt(prompt = userMessage, pages = pages)
        selectActionResultForPrompt(
            prompt = userMessage,
            modelResult = modelResult,
            promptResult = promptResult,
        )?.let { result -> return result }

        return AiActionResult(reply = reply, actions = emptyList())
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
    fun summarize(text: String): String {
        if (isMockMode) {
            return "Mock Summary: The page contents detail a personal productivity framework covering habits, workspaces, and notes. This is a local mock summary for testing. [Sandbox Mode]"
        }

        val prompt = "Please summarize the following document content concisely in one brief paragraph:\n\n$text"
        val messages = listOf(
            ChatMessage(role = "system", content = "You are a helpful notes assistant. Summarize text provided by the user."),
            ChatMessage(role = "user", content = prompt)
        )
        return chat(messages)
    }

    fun generateTasks(text: String): List<String> {
        if (isMockMode) {
            return listOf(
                "Task 1: Set up AI-assisted templates (Mock)",
                "Task 2: Detail weekly workspace dashboard (Mock)",
                "Task 3: Schedule sync engine tests (Mock)"
            )
        }

        val systemPrompt = """
            You are a task management extraction assistant.
            Your job is to read the text provided by the user and extract a list of actionable task items.
            Return ONLY a raw JSON array of strings. Do not explain anything.
            Example output format: ["Buy flight tickets", "Pack suitcases", "Check-in online"]
        """.trimIndent()

        val prompt = "Read the following document and extract a list of todo tasks:\n\n$text"

        return try {
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = prompt)
            )
            val response = chatCompletionsJson(messages)
            json.decodeFromString<List<String>>(response.cleanAiJson())
        } catch (e: Exception) {
            listOf("Failed to extract tasks: ${e.localizedMessage}")
        }
    }

    fun generatePlan(prompt: String): String {
        if (isMockMode) {
            return createMockPlanJson(prompt)
        }

        val systemPrompt = """
            You are an expert personal productivity AI assistant. Generate a structured page plan.
            Output ONLY a valid JSON object — no markdown fences, no extra text.

            Structure:
            {
              "version": 1,
              "properties": [
                { "id": "uuid-string", "name": "Property Name", "type": "Text|Number|Select|Status|Date|Checkbox|Formula|Relation|Rollup", "value": "value-string" }
              ],
              "blocks": [
                {
                  "id": "uuid-string",
                  "type": "Text|Heading|Todo|Bullet|Quote|Divider|DatabaseTable",
                  "text": "text-content",
                  "isChecked": false,
                  "table": {
                    "title": "Table Title",
                    "view": "Table",
                    "columns": [{ "id": "col-uuid", "name": "Column Name", "type": "Text|Number|Status|Date|Checkbox|Formula|Relation|Rollup", "formula": "", "relationTargetTableId": "", "rollupRelationColumnId": "", "rollupTargetColumnId": "", "rollupAggregation": "Count" }],
                    "rows": [{ "id": "row-uuid", "cells": { "col-uuid": "cell-value" } }]
                  },
                  "children": []
                }
              ]
            }

            Rules:
            - Generate unique UUID strings for all id fields.
            - Block types: Text, Heading, Todo, Bullet, Quote, Divider, DatabaseTable.
            - Property types: Text, Checkbox, Date, Status.
            - If DatabaseTable: include at least 2 columns, 2 rows, and cells map to column IDs.
            - Output MUST be valid JSON only.
        """.trimIndent()

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = prompt)
        )

        return try {
            chatCompletionsJson(messages).cleanAiJson()
        } catch (e: Exception) {
            createMockPlanJson("Error generating plan: ${e.localizedMessage}")
        }
    }

    private fun chatCompletionsJson(
        messages: List<ChatMessage>,
        temperature: Double = 0.2,
    ): String {
        val body = json.encodeToString(
            ApiRequest(
                model = activeModel,
                messages = messages.map { ApiMessage(it.role, it.content) },
                temperature = temperature,
                response_format = ApiResponseFormat("json_object")
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(completionsUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $activeApiKey")
            .header("HTTP-Referer", "https://changeyourlife.local")
            .header("X-Title", "ChangeYourLife")
            .timeout(Duration.ofSeconds(90)) // read + response timeout
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return if (response.statusCode() == 200) {
            val apiResponse = json.decodeFromString<ApiResponse>(response.body())
            apiResponse.choices.firstOrNull()
                ?.message
                ?.content
                ?.ifBlank { null }
                ?: throw Exception("AI provider returned an empty response.")
        } else {
            throw Exception("HTTP Error: ${response.statusCode()} - ${response.body()}")
        }
    }

    private fun createMockPlanJson(prompt: String): String {
        val planId = UUID.randomUUID().toString()
        val quoteId = UUID.randomUUID().toString()
        val todo1Id = UUID.randomUUID().toString()
        val todo2Id = UUID.randomUUID().toString()
        val divId1 = UUID.randomUUID().toString()
        val subHeadingId = UUID.randomUUID().toString()
        val bullet1Id = UUID.randomUUID().toString()
        val bullet2Id = UUID.randomUUID().toString()
        val divId2 = UUID.randomUUID().toString()
        val tableId = UUID.randomUUID().toString()
        val col1 = UUID.randomUUID().toString()
        val col2 = UUID.randomUUID().toString()
        val col3 = UUID.randomUUID().toString()
        val r1 = UUID.randomUUID().toString()
        val r2 = UUID.randomUUID().toString()

        return """
        {
          "version": 1,
          "properties": [
            { "id": "prop-cyl-mock", "name": "AI Generated", "type": "Checkbox", "value": "true" }
          ],
          "blocks": [
            { "id": "$planId", "type": "Heading", "text": "Plan: $prompt (Sandbox Mode)" },
            { "id": "$quoteId", "type": "Quote", "text": "This is a sandbox response. Add OPENROUTER_API_KEY to enable live AI generation." },
            { "id": "$todo1Id", "type": "Todo", "text": "Task A: Research details on $prompt", "isChecked": false },
            { "id": "$todo2Id", "type": "Todo", "text": "Task B: Outline requirements for $prompt", "isChecked": false },
            { "id": "$divId1", "type": "Divider" },
            { "id": "$subHeadingId", "type": "Heading", "text": "Itinerary & Stages" },
            { "id": "$bullet1Id", "type": "Bullet", "text": "Stage 1: Prep resources and initial setup" },
            { "id": "$bullet2Id", "type": "Bullet", "text": "Stage 2: Execute checklist and finalize milestones" },
            { "id": "$divId2", "type": "Divider" },
            {
              "id": "$tableId",
              "type": "DatabaseTable",
              "table": {
                "title": "Expense Estimation",
                "view": "Table",
                "columns": [
                  { "id": "$col1", "name": "Item Description", "type": "Text" },
                  { "id": "$col2", "name": "Estimate Cost", "type": "Number" },
                  { "id": "$col3", "name": "Status", "type": "Status" }
                ],
                "rows": [
                  { "id": "$r1", "cells": { "$col1": "Initial Setup", "$col2": "150", "$col3": "Paid" } },
                  { "id": "$r2", "cells": { "$col1": "Travel & Logistics", "$col2": "500", "$col3": "Pending" } }
                ]
              }
            }
          ]
        }
        """.trimIndent()
    }

    // Shared OpenAI-compatible request/response models (work for both Gemini and GLM)
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
