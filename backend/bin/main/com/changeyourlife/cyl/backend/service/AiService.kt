package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiTaskContext
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

class AiService(
    private val glmApiKey: String? = null,
    private val geminiApiKey: String? = null,
    private val openRouterApiKey: String? = null,
    private val openRouterModel: String = "openai/gpt-oss-20b:free",
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

    @Serializable
    private data class AiActionEnvelope(
        val reply: String = "",
        val actions: List<AiActionItem> = emptyList(),
    )

    private val tableRowActionTypes = setOf(
        "ADD_TABLE_ROW",
        "UPDATE_TABLE_ROW",
        "DELETE_TABLE_ROW",
        "UPDATE_TABLE_CELL",
    )
    private val legacyEnvelopeKeys = setOf("page", "targetpage", "targettitle", "action", "data", "rows", "row", "table", "tabletitle")
    private val ignoredLegacyDataKeys = setOf("id", "rowid", "row_id", "uuid")
    private val supportedActionTypes = setOf(
        "RENAME_CURRENT_PAGE",
        "RENAME_PAGE",
        "UPDATE_PAGE",
        "APPEND_BLOCK",
        "APPEND_PAGE_BLOCK",
        "ADD_BLOCK",
        "ADD_PROPERTY",
        "UPDATE_PROPERTY",
        "DELETE_PROPERTY",
        "DELETE_ALL_BLOCKS",
        "DELETE_BLOCK",
        "UPDATE_BLOCK",
        "EDIT_BLOCK",
        "UPDATE_TODO",
        "CHECK_BLOCK",
        "UNCHECK_BLOCK",
        "CREATE_DATABASE",
        "CREATE_TABLE",
        "RENAME_TABLE",
        "RENAME_DATABASE",
        "UPDATE_TABLE_TITLE",
        "ADD_TABLE_COLUMN",
        "DELETE_TABLE_COLUMN",
        "RENAME_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN",
        "UPDATE_TABLE_COLUMN_TYPE",
        "CHANGE_TABLE_COLUMN_TYPE",
        "SET_TABLE_COLUMN_TYPE",
        "UPDATE_TABLE_COLUMN_CONFIG",
        "SET_TABLE_COLUMN_CONFIG",
        "UPDATE_FORMULA_COLUMN",
        "UPDATE_RELATION_COLUMN",
        "UPDATE_ROLLUP_COLUMN",
        "REORDER_TABLE_COLUMN",
        "MOVE_TABLE_COLUMN",
        "ADD_TABLE_ROW",
        "DELETE_TABLE_ROW",
        "UPDATE_TABLE_ROW",
        "RENAME_TABLE_ROW",
        "REORDER_TABLE_ROW",
        "MOVE_TABLE_ROW",
        "ADD_ROW_PAGE_BLOCK",
        "APPEND_ROW_PAGE_BLOCK",
        "ADD_TABLE_ROW_BLOCK",
        "UPDATE_ROW_PAGE_BLOCK",
        "EDIT_ROW_PAGE_BLOCK",
        "UPDATE_TABLE_ROW_BLOCK",
        "CHECK_ROW_PAGE_BLOCK",
        "UNCHECK_ROW_PAGE_BLOCK",
        "DELETE_ROW_PAGE_BLOCK",
        "DELETE_TABLE_ROW_BLOCK",
        "UPDATE_TABLE_CELL",
        "CHANGE_TABLE_VIEW",
        "SET_TABLE_VIEW",
        "SET_TABLE_VIEW_CONFIG",
        "CONFIGURE_TABLE_VIEW",
        "UPDATE_TABLE_VIEW_CONFIG",
        "SORT_TABLE",
        "SET_TABLE_SORT",
        "CLEAR_TABLE_SORT",
        "FILTER_TABLE",
        "SET_TABLE_FILTER",
        "CLEAR_TABLE_FILTER",
        "GROUP_TABLE",
        "SET_TABLE_GROUP",
        "CLEAR_TABLE_GROUP",
        "CREATE_SUBPAGE",
        "CREATE_PAGE",
        "CREATE_TASK",
        "CREATE_REMINDER",
    )

    private data class AiActionSchemaValidation(
        val actions: List<AiActionItem>,
        val issues: List<AiActionValidationIssue>,
    )

    private fun List<AiActionItem>.validatedForActionSchema(): AiActionSchemaValidation {
        val validActions = mutableListOf<AiActionItem>()
        val issues = mutableListOf<AiActionValidationIssue>()
        forEachIndexed { index, action ->
            val normalizedType = action.type.normalizedActionType()
            when {
                normalizedType.isBlank() -> {
                    issues += AiActionValidationIssue(
                        actionIndex = index,
                        field = "type",
                        code = "missing_action_type",
                        message = "Action type is required.",
                    )
                }
                normalizedType !in supportedActionTypes -> {
                    issues += AiActionValidationIssue(
                        actionIndex = index,
                        field = "type",
                        code = "unsupported_action_type",
                        message = "Unsupported CYL action type: $normalizedType.",
                    )
                }
                else -> {
                    val normalizedAction = action.copy(type = normalizedType)
                    val requirementIssues = normalizedAction.requiredFieldIssues(actionIndex = index)
                    if (requirementIssues.isEmpty()) {
                        validActions += normalizedAction
                    } else {
                        issues += requirementIssues
                    }
                }
            }
        }
        return AiActionSchemaValidation(actions = validActions, issues = issues)
    }

    private fun AiActionItem.requiredFieldIssues(actionIndex: Int): List<AiActionValidationIssue> {
        fun issue(field: String, message: String): AiActionValidationIssue =
            AiActionValidationIssue(
                actionIndex = actionIndex,
                field = field,
                code = "missing_required_field",
                message = message,
            )

        fun hasAny(vararg values: String?): Boolean = values.any { value -> !value.isNullOrBlank() }

        return when (type) {
            "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> {
                if (blockType.normalizedActionType() == "DIVIDER" || hasAny(content, title)) emptyList()
                else listOf(issue("content", "Block content is required unless the block is a divider."))
            }
            "DELETE_BLOCK" -> {
                if (hasAny(blockId, blockText, content, title)) emptyList()
                else listOf(issue("blockId", "Delete block action needs blockId, blockText, content, or title."))
            }
            "UPDATE_BLOCK", "EDIT_BLOCK", "UPDATE_TODO", "CHECK_BLOCK", "UNCHECK_BLOCK" -> {
                buildList {
                    if (!hasAny(blockId, blockText, title)) {
                        add(issue("blockId", "Update block action needs blockId, blockText, or title."))
                    }
                    if (type in setOf("UPDATE_BLOCK", "EDIT_BLOCK") && !hasAny(content, value)) {
                        add(issue("content", "Update block action needs replacement content."))
                    }
                }
            }
            "ADD_PROPERTY", "UPDATE_PROPERTY", "DELETE_PROPERTY" -> {
                if (hasAny(propertyName, title)) emptyList()
                else listOf(issue("propertyName", "Property action needs propertyName or title."))
            }
            "CREATE_DATABASE", "CREATE_TABLE" -> {
                if (hasAny(tableTitle, title, content)) emptyList()
                else listOf(issue("tableTitle", "Create table action needs tableTitle, title, or content."))
            }
            "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> {
                if (hasAny(title, value, content, newColumnName)) emptyList()
                else listOf(issue("title", "Rename table action needs a new title."))
            }
            "ADD_TABLE_COLUMN" -> {
                if (hasAny(columnName, propertyName, title)) emptyList()
                else listOf(issue("columnName", "Add table column action needs columnName, propertyName, or title."))
            }
            "DELETE_TABLE_COLUMN", "UPDATE_TABLE_COLUMN_TYPE", "CHANGE_TABLE_COLUMN_TYPE", "SET_TABLE_COLUMN_TYPE",
            "UPDATE_TABLE_COLUMN_CONFIG", "SET_TABLE_COLUMN_CONFIG", "UPDATE_FORMULA_COLUMN", "UPDATE_RELATION_COLUMN",
            "UPDATE_ROLLUP_COLUMN" -> {
                if (hasAny(columnId, columnName, propertyName, title)) emptyList()
                else listOf(issue("columnName", "Column action needs columnId, columnName, propertyName, or title."))
            }
            "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> {
                buildList {
                    if (!hasAny(columnId, columnName, propertyName, title)) {
                        add(issue("columnName", "Rename column action needs columnId, columnName, propertyName, or title."))
                    }
                    if (!hasAny(newColumnName, value, content)) {
                        add(issue("newColumnName", "Rename column action needs newColumnName, value, or content."))
                    }
                }
            }
            "REORDER_TABLE_COLUMN", "MOVE_TABLE_COLUMN" -> {
                buildList {
                    if (!hasAny(columnId, columnName, propertyName, title)) {
                        add(issue("columnName", "Move column action needs columnId, columnName, propertyName, or title."))
                    }
                    if (targetIndex == null) add(issue("targetIndex", "Move column action needs targetIndex."))
                }
            }
            "ADD_TABLE_ROW" -> {
                if (hasAny(rowTitle, title, content) || cellValues.isNotEmpty() || tableRows.isNotEmpty()) emptyList()
                else listOf(issue("rowTitle", "Add row action needs rowTitle, title, content, cellValues, or tableRows."))
            }
            "DELETE_TABLE_ROW" -> {
                if (hasAny(rowId, rowTitle, title)) emptyList()
                else listOf(issue("rowTitle", "Delete row action needs rowId, rowTitle, or title."))
            }
            "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) {
                        add(issue("rowTitle", "Update row action needs rowId, rowTitle, or title."))
                    }
                    if (!hasAny(newRowTitle, value, content) && cellValues.isEmpty()) {
                        add(issue("value", "Update row action needs newRowTitle, value, content, or cellValues."))
                    }
                }
            }
            "REORDER_TABLE_ROW", "MOVE_TABLE_ROW" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) {
                        add(issue("rowTitle", "Move row action needs rowId, rowTitle, or title."))
                    }
                    if (targetIndex == null) add(issue("targetIndex", "Move row action needs targetIndex."))
                }
            }
            "ADD_ROW_PAGE_BLOCK", "APPEND_ROW_PAGE_BLOCK", "ADD_TABLE_ROW_BLOCK",
            "UPDATE_ROW_PAGE_BLOCK", "EDIT_ROW_PAGE_BLOCK", "UPDATE_TABLE_ROW_BLOCK",
            "CHECK_ROW_PAGE_BLOCK", "UNCHECK_ROW_PAGE_BLOCK", "DELETE_ROW_PAGE_BLOCK", "DELETE_TABLE_ROW_BLOCK" -> {
                if (hasAny(rowId, rowTitle, targetTitle, title)) emptyList()
                else listOf(issue("rowTitle", "Row page block action needs rowId, rowTitle, targetTitle, or title."))
            }
            "UPDATE_TABLE_CELL" -> {
                buildList {
                    if (!hasAny(rowId, rowTitle, title)) add(issue("rowTitle", "Cell update needs rowId, rowTitle, or title."))
                    if (!hasAny(columnId, columnName, propertyName)) {
                        add(issue("columnName", "Cell update needs columnId, columnName, or propertyName."))
                    }
                    if (!hasAny(value, content)) add(issue("value", "Cell update needs value or content."))
                }
            }
            "SORT_TABLE", "SET_TABLE_SORT", "FILTER_TABLE", "SET_TABLE_FILTER", "GROUP_TABLE", "SET_TABLE_GROUP" -> {
                if (hasAny(columnId, columnName, propertyName, title, groupByColumnId, groupByColumnName)) emptyList()
                else listOf(issue("columnName", "Table rule action needs a target column."))
            }
            else -> emptyList()
        }
    }

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
        if (modelResult != null && modelResult.actions.isNotEmpty()) return modelResult

        val promptResult = recoverActionFromPrompt(
            prompt = userMessage,
            pages = pages,
        )
        if (promptResult != null) {
            return promptResult.copy(
                validationIssues = modelResult?.validationIssues.orEmpty() + promptResult.validationIssues,
            )
        }
        if (modelResult != null) return modelResult

        return AiActionResult(reply = reply, actions = emptyList())
    }

    internal fun recoverActionFromModelReply(
        reply: String,
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? {
        if (reply.isBlank()) return null
        val cleaned = reply.cleanAiJson()
        val jsonElement = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() ?: return null
        val jsonObject = jsonElement as? JsonObject ?: return null

        runCatching { json.decodeFromString<AiActionEnvelope>(cleaned) }
            .getOrNull()
            ?.takeIf { envelope -> envelope.actions.isNotEmpty() }
            ?.let { envelope ->
                val normalizedActions = envelope.actions.map { action -> action.normalizedJsonAction(pages, prompt) }
                val validation = normalizedActions.validatedForActionSchema()
                return AiActionResult(
                    reply = envelope.reply.ifBlank {
                        validation.actions.ifEmpty { normalizedActions }.recoveredReplyForPrompt(prompt)
                    },
                    actions = validation.actions,
                    validationIssues = validation.issues,
                )
            }

        runCatching { json.decodeFromString<AiActionItem>(cleaned) }
            .getOrNull()
            ?.takeIf { action -> action.type.isNotBlank() }
            ?.let { action ->
                val normalized = action.normalizedJsonAction(pages, prompt)
                val validation = listOf(normalized).validatedForActionSchema()
                return AiActionResult(
                    reply = listOf(normalized).recoveredReplyForPrompt(prompt),
                    actions = validation.actions,
                    validationIssues = validation.issues,
                )
            }

        return jsonObject.toLegacyActionResult(prompt = prompt, pages = pages)
    }

    private fun AiActionItem.normalizedJsonAction(
        pages: List<AiPageContext>,
        prompt: String,
    ): AiActionItem {
        val normalizedType = type.normalizedActionType()
        val explicitTarget = targetTitle.cleanAiPageTitle()
            .ifBlank { title.cleanAiPageTitle().takeIf { normalizedType != "CREATE_DATABASE" }.orEmpty() }
        val targetPage = pages.findPageByAiTitle(explicitTarget)
            ?: pages.findTargetPage(prompt)
        return copy(
            type = normalizedType,
            targetTitle = targetPage?.title ?: explicitTarget,
            tableTitle = tableTitle.ifBlank {
                if (normalizedType in tableRowActionTypes) targetPage?.defaultTableTitle().orEmpty() else ""
            },
        )
    }

    private fun List<AiActionItem>.recoveredReplyForPrompt(prompt: String): String =
        prompt.withoutMentionContext().recoveryReply(
            malay = recoveredMalayReply(),
            english = recoveredEnglishReply(),
        )

    private fun JsonObject.toLegacyActionResult(
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? {
        val action = stringValue("action").lowercase()
        if (action.isBlank()) return null
        val targetPage = pages.findPageByAiTitle(stringValue("page"))
            ?: pages.findPageByAiTitle(stringValue("targetPage"))
            ?: pages.findPageByAiTitle(stringValue("targetTitle"))
            ?: pages.findTargetPage(prompt)
            ?: return null
        val dataRows = legacyDataRows()
        if (dataRows.isEmpty()) return null

        val tableTitle = stringValue("table")
            .ifBlank { stringValue("tableTitle") }
            .ifBlank { targetPage.defaultTableTitle().orEmpty() }

        val actions = when (action) {
            "add", "append", "create", "insert", "update", "upsert", "set" -> {
                dataRows.mapNotNull { row ->
                    val rowTitle = row.legacyRowTitle()
                    val cellValues = row
                        .filterKeys { key -> key.normalizedLegacyKey() !in ignoredLegacyDataKeys }
                        .mapKeys { (key, _) -> key.toAiColumnName() }
                    if (rowTitle.isBlank() && cellValues.isEmpty()) {
                        null
                    } else {
                        AiActionItem(
                            type = "ADD_TABLE_ROW",
                            targetTitle = targetPage.title,
                            tableTitle = tableTitle,
                            rowTitle = rowTitle,
                            cellValues = cellValues,
                        )
                    }
                }
            }
            "delete", "remove", "padam", "buang" -> {
                dataRows.mapNotNull { row ->
                    row.legacyRowTitle().takeIf { rowTitle -> rowTitle.isNotBlank() }?.let { rowTitle ->
                        AiActionItem(
                            type = "DELETE_TABLE_ROW",
                            targetTitle = targetPage.title,
                            tableTitle = tableTitle,
                            rowTitle = rowTitle,
                        )
                    }
                }
            }
            else -> emptyList()
        }

        if (actions.isEmpty()) return null
        val validation = actions.validatedForActionSchema()
        return AiActionResult(
            reply = actions.recoveredReplyForPrompt(prompt),
            actions = validation.actions,
            validationIssues = validation.issues,
        )
    }

    private fun JsonObject.legacyDataRows(): List<Map<String, String>> {
        val data = this["data"] ?: this["rows"] ?: this["row"]
        val explicitRows = when (data) {
            is JsonArray -> data.mapNotNull { item -> (item as? JsonObject)?.toPlainStringMap() }
            is JsonObject -> listOf(data.toPlainStringMap())
            else -> emptyList()
        }
        if (explicitRows.isNotEmpty()) return explicitRows

        val inlineRow = toPlainStringMap()
            .filterKeys { key -> key.normalizedLegacyKey() !in legacyEnvelopeKeys }
        return listOf(inlineRow).filter { row -> row.isNotEmpty() }
    }

    private fun JsonObject.toPlainStringMap(): Map<String, String> =
        entries.mapNotNull { (key, value) ->
            key.takeIf { it.isNotBlank() }?.let {
                it to value.plainString().trim()
            }
        }
            .filter { (_, value) -> value.isNotBlank() }
            .toMap()

    private fun JsonElement.plainString(): String =
        when (this) {
            is JsonPrimitive -> contentOrNull ?: toString()
            is JsonArray, is JsonObject -> toString()
        }

    private fun JsonObject.stringValue(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun Map<String, String>.legacyRowTitle(): String {
        val preferredKeys = listOf("name", "item", "title", "task", "category", "description", "note", "notes")
        preferredKeys.firstNotNullOfOrNull { preferred ->
            entries.firstOrNull { (key, value) ->
                key.normalizedLegacyKey() == preferred && value.isNotBlank()
            }?.value
        }?.let { return it }
        return entries.firstOrNull { (key, value) ->
            key.normalizedLegacyKey() !in ignoredLegacyDataKeys && value.isNotBlank()
        }?.value.orEmpty()
    }

    private fun List<AiPageContext>.findPageByAiTitle(rawTitle: String): AiPageContext? {
        val normalized = rawTitle.cleanAiPageTitle().normalizeForAiMatch()
        if (normalized.isBlank()) return null
        return firstOrNull { page -> page.id == rawTitle.trim() }
            ?: firstOrNull { page -> page.title.normalizeForAiMatch() == normalized }
            ?: firstOrNull { page ->
                val title = page.title.normalizeForAiMatch()
                title.isNotBlank() && (title.contains(normalized) || normalized.contains(title))
            }
    }

    private fun String.cleanAiPageTitle(): String =
        trim().removePrefix("@").trim()

    private fun String.toAiColumnName(): String =
        trim()
            .replace("_", " ")
            .replace("-", " ")
            .split(Regex("\\s+"))
            .filter { part -> part.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char -> char.uppercaseChar() }
            }

    private fun String.normalizedLegacyKey(): String =
        trim()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .lowercase()

    internal fun recoverActionFromPrompt(
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? {
        if (prompt.isBlank()) return null
        val visiblePrompt = prompt.withoutMentionContext()
        val targetPage = pages.findTargetPage(prompt)
            ?: pages.findTargetPage(visiblePrompt)
            ?: return null

        val recoveredActions = visiblePrompt.recoverStructuredActions(targetPage)
        if (recoveredActions.isNotEmpty()) {
            val validation = recoveredActions.validatedForActionSchema()
            return AiActionResult(
                reply = visiblePrompt.recoveryReply(
                    malay = validation.actions.ifEmpty { recoveredActions }.recoveredMalayReply(),
                    english = validation.actions.ifEmpty { recoveredActions }.recoveredEnglishReply(),
                ),
                actions = validation.actions,
                validationIssues = validation.issues,
            )
        }

        val fallbackActions = listOfNotNull(
            visiblePrompt.recoverBlockAction(targetPage)
                ?: visiblePrompt.recoverTableRowAction(targetPage)
                ?: visiblePrompt.recoverPropertyAction(targetPage)
                ?: visiblePrompt.recoverTableRenameAction(targetPage)
                ?: visiblePrompt.recoverDatabaseAction(targetPage)
                ?: visiblePrompt.takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage),
        )
        if (fallbackActions.isEmpty()) return null
        val validation = fallbackActions.validatedForActionSchema()
        return AiActionResult(
            reply = visiblePrompt.recoveryReply(
                malay = validation.actions.ifEmpty { fallbackActions }.recoveredMalayReply(),
                english = validation.actions.ifEmpty { fallbackActions }.recoveredEnglishReply(),
            ),
            actions = validation.actions,
            validationIssues = validation.issues,
        )
    }

    private fun String.recoverStructuredActions(targetPage: AiPageContext): List<AiActionItem> {
        val actions = mutableListOf<AiActionItem>()
        var createdTableTitle: String? = null
        actionSegments().forEach { segment ->
            val rowAction = segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
            if (rowAction == null &&
                segment.looksLikeTableRowRequest() &&
                createdTableTitle.isNullOrBlank() &&
                !targetPage.hasAnyTable()
            ) {
                val tableAction = segment.recoverImplicitDatabaseAction(targetPage)
                createdTableTitle = tableAction.tableTitle
                actions += tableAction
                segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
                    ?.let { action -> actions += action }
                return@forEach
            }

            val action = segment.recoverBlockAction(targetPage)
                ?: rowAction
                ?: segment.recoverPropertyAction(targetPage)
                ?: segment.recoverTableRenameAction(targetPage)
                ?: segment.recoverDatabaseAction(targetPage)
                ?: segment.takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage)
            if (action != null) {
                actions += action
                if (action.type.equals("CREATE_DATABASE", ignoreCase = true)) {
                    createdTableTitle = action.tableTitle.takeIf { title -> title.isNotBlank() }
                }
            }
        }
        return if (actions.isNotEmpty()) {
            actions
        } else {
            listOfNotNull(
                recoverBlockAction(targetPage)
                    ?: recoverTableRowAction(targetPage)
                    ?: recoverPropertyAction(targetPage)
                    ?: recoverTableRenameAction(targetPage)
                    ?: recoverDatabaseAction(targetPage)
                    ?: takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage),
            )
        }
    }

    private fun List<AiActionItem>.recoveredMalayReply(): String {
        if (size > 1) return "Siap - saya buat perubahan itu."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Siap - saya padam semua block dalam page itu."
            "DELETE_BLOCK" -> "Siap - saya buang block itu."
            "UPDATE_BLOCK" -> "Siap - saya ubah block itu."
            "ADD_TABLE_ROW" -> "Siap - saya tambah row itu."
            "CREATE_DATABASE" -> "Siap - saya buat table itu."
            "DELETE_PROPERTY" -> "Siap - saya padam property itu."
            "UPDATE_PROPERTY" -> "Siap - saya ubah property itu."
            "ADD_PROPERTY" -> "Siap - saya tambah property itu."
            else -> "Siap - saya buat perubahan itu."
        }
    }

    private fun List<AiActionItem>.recoveredEnglishReply(): String {
        if (size > 1) return "Done - I made those changes."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Done - I deleted all blocks in that page."
            "DELETE_BLOCK" -> "Done - I deleted that block."
            "UPDATE_BLOCK" -> "Done - I updated that block."
            "ADD_TABLE_ROW" -> "Done - I added that row."
            "CREATE_DATABASE" -> "Done - I created that table."
            "DELETE_PROPERTY" -> "Done - I deleted that property."
            "UPDATE_PROPERTY" -> "Done - I updated that property."
            "ADD_PROPERTY" -> "Done - I added that property."
            else -> "Done - I made that change."
        }
    }

    private fun List<AiPageContext>.findTargetPage(prompt: String): AiPageContext? {
        prompt.extractMentionContextPageIds().firstNotNullOfOrNull { pageId ->
            firstOrNull { page -> page.id == pageId }
        }?.let { return it }

        val normalizedPrompt = prompt.lowercase()
        val pagesWithTitle = filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }

        pagesWithTitle.firstOrNull { page ->
            normalizedPrompt.contains("@${page.title.lowercase()}")
        }?.let { return it }

        val mention = Regex("@([^\\n,.;:]+)")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mention.isNotBlank()) {
            pagesWithTitle.firstOrNull { page ->
                val title = page.title.lowercase()
                title == mention ||
                    title.startsWith(mention) ||
                    mention.startsWith(title) ||
                    title.contains(mention)
            }?.let { return it }
        }

        return singleOrNull()?.takeIf { prompt.looksLikePageMutationRequest() }
    }

    private fun String.extractMentionContextPageIds(): List<String> {
        val context = substringAfter("CYL_MENTION_CONTEXT:", missingDelimiterValue = "")
        if (context.isBlank()) return emptyList()
        return Regex("\\bid=([^\\s]+)")
            .findAll(context)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { pageId -> pageId.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun String.withoutMentionContext(): String =
        substringBefore("CYL_MENTION_CONTEXT:").trim()

    private fun String.recoverWriteAction(targetPage: AiPageContext): AiActionItem? {
        if (!looksLikePageWriteRequest()) return null
        val content = extractWriteContent(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "APPEND_BLOCK",
            targetTitle = targetPage.title,
            blockType = inferBlockType(),
            content = content,
        )
    }

    private fun String.recoverPropertyAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val mentionsProperty = listOf("property", "properties", "prop").any { token -> value.contains(token) }
        if (!mentionsProperty) return null

        val actionType = when {
            listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) } -> "DELETE_PROPERTY"
            listOf("ubah", "tukar", "edit", "update", "set", "jadikan", "change").any { token -> value.contains(token) } -> "UPDATE_PROPERTY"
            listOf("tambah", "add", "create", "buat", "masukkan", "letak").any { token -> value.contains(token) } -> "ADD_PROPERTY"
            else -> return null
        }
        val propertyName = extractPropertyName(targetPage.title)
        if (propertyName.isBlank()) return null

        return AiActionItem(
            type = actionType,
            targetTitle = targetPage.title,
            propertyName = propertyName,
            propertyType = inferPropertyType(),
            value = extractPropertyValue(targetPage.title),
        )
    }

    private fun String.recoverDatabaseAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        if (looksLikeTableRowRequest()) return null
        val isTableContext = looksLikeTableContextOnlyRequest()
        val hasDatabaseIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasDatabaseIntent && !isTableContext) return null
        if (isTableContext && !hasDatabaseIntent && targetPage.hasAnyTable()) return null
        val isCreate = listOf("buat", "create", "cipta", "tambah", "add", "masukkan", "letak").any { token ->
            value.contains(token)
        } || isTableContext
        if (!isCreate) return null

        val tableTitle = extractTableTitle(targetPage.title).ifBlank { "Table" }
        return AiActionItem(
            type = "CREATE_DATABASE",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(value),
        )
    }

    private fun String.recoverImplicitDatabaseAction(targetPage: AiPageContext): AiActionItem {
        val tableTitle = when {
            looksLikeExpenseText() -> targetPage.title.ifBlank { "Belanja" }
            else -> targetPage.title.ifBlank { "Table" }
        }
        return AiActionItem(
            type = "CREATE_DATABASE",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(lowercase()),
        )
    }

    private fun String.recoverTableRenameAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val hasTableIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasTableIntent || !targetPage.hasAnyTable()) return null
        val hasRenameIntent = listOf("rename", "ubah nama", "tukar nama", "ganti nama", "change name", "jadikan nama")
            .any { token -> value.contains(token) } ||
            (
                listOf("ubah", "tukar", "ganti", "jadikan", "change", "set").any { token -> value.contains(token) } &&
                    listOf("nama", "name", "title").any { token -> value.contains(token) }
                )
        if (!hasRenameIntent) return null
        val newTitle = extractNewTableTitle(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "RENAME_TABLE",
            title = newTitle,
            targetTitle = targetPage.title,
        )
    }

    private fun String.recoverTableRowAction(
        targetPage: AiPageContext,
        fallbackTableTitle: String? = null,
    ): AiActionItem? {
        if (!looksLikeTableRowRequest()) return null
        val tableTitle = targetPage.defaultTableTitle()
            ?: fallbackTableTitle?.takeIf { title -> title.isNotBlank() }
        if (tableTitle == null && !targetPage.hasAnyTable()) return null

        val rowPrompt = bestTableRowSegment()
        val rowText = rowPrompt.extractTableRowText(targetPage.title)
        if (rowText.isBlank()) return null
        val amount = rowText.extractMoneyAmount()
        val rowTitle = rowText.removeMoneyAmount().removeMetricRequestWords().removeDateRequestWords().ifBlank { rowText }
        val dateValue = rowPrompt.inferredDateValue()
        val cellValues = buildMap {
            put("Name", rowTitle)
            put("Item", rowTitle)
            amount?.let { value ->
                put("Amount", value)
                put("Jumlah", value)
                put("Harga", value)
                put("Price", value)
                put("Cost", value)
                put("Total", value)
            }
            dateValue?.let { value ->
                put("Date", value)
                put("Tarikh", value)
            }
            if (amount != null && !rowText.equals(rowTitle, ignoreCase = true)) {
                put("Notes", rowText)
            }
        }
        return AiActionItem(
            type = "ADD_TABLE_ROW",
            targetTitle = targetPage.title,
            tableTitle = tableTitle.orEmpty(),
            rowTitle = rowTitle,
            cellValues = cellValues,
        )
    }

    private fun String.recoverBlockAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val hasBlockIntent = listOf(
            "block",
            "blok",
            "heading",
            "tajuk",
            "todo",
            "checklist",
            "quote",
            "petikan",
            "divider",
            "media",
            "file",
            "gambar",
        ).any { token -> value.contains(token) }
        if (!hasBlockIntent) return null

        val isDelete = listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) }
        if (isDelete) {
            if (requestsAllBlocksDeletion()) {
                return AiActionItem(
                    type = "DELETE_ALL_BLOCKS",
                    targetTitle = targetPage.title,
                )
            }
            val reference = extractBlockReference(targetPage.title)
            val block = targetPage.findMatchingBlock(inferBlockType(), reference)
            return AiActionItem(
                type = "DELETE_BLOCK",
                targetTitle = targetPage.title,
                blockId = block?.id.orEmpty(),
                blockType = block?.type ?: inferBlockType(),
                blockText = reference.ifBlank { block?.text.orEmpty() },
                content = reference.ifBlank { block?.text.orEmpty() },
                tableTitle = block?.tableTitle.orEmpty(),
            )
        }

        val isUpdate = listOf("ubah", "tukar", "edit", "update", "ganti", "jadikan", "change").any { token ->
            value.contains(token)
        }
        if (isUpdate) {
            val replacement = splitBlockReplacement(targetPage.title) ?: return null
            val block = targetPage.findMatchingBlock(inferBlockType(), replacement.first)
            return AiActionItem(
                type = "UPDATE_BLOCK",
                targetTitle = targetPage.title,
                blockId = block?.id.orEmpty(),
                blockType = block?.type ?: inferBlockType(),
                blockText = replacement.first.ifBlank { block?.text.orEmpty() },
                content = replacement.second,
            )
        }

        val isAppend = listOf("tambah", "add", "buat", "masukkan", "letak", "tulis", "catat", "append").any { token ->
            value.contains(token)
        }
        if (!isAppend) return null
        val content = extractBlockReference(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "APPEND_BLOCK",
            targetTitle = targetPage.title,
            blockType = inferBlockType(),
            content = content,
        )
    }

    private fun AiPageContext.findMatchingBlock(
        requestedType: String,
        reference: String,
    ): AiBlockContext? {
        val normalizedReference = reference.normalizeForAiMatch()
        val candidates = blocks.filterNot { block -> block.type.equals("Property", ignoreCase = true) }
        if (normalizedReference.isNotBlank()) {
            candidates.firstOrNull { block ->
                val blockText = block.text.normalizeForAiMatch()
                blockText.isNotBlank() &&
                    (blockText.contains(normalizedReference) || normalizedReference.contains(blockText))
            }?.let { return it }

            candidates.firstOrNull { block ->
                val tableTitle = block.tableTitle.normalizeForAiMatch()
                tableTitle.isNotBlank() && tableTitle.contains(normalizedReference)
            }?.let { return it }
        }

        val normalizedType = requestedType.normalizeForAiMatch()
        return candidates.firstOrNull { block ->
            block.type.normalizeForAiMatch() == normalizedType ||
                block.type.equals(requestedType, ignoreCase = true)
        }
    }

    private fun String.looksLikePageMutationRequest(): Boolean {
        val value = lowercase()
        val mutationIntent = listOf(
            "write",
            "tulis",
            "catat",
            "masukkan",
            "insert",
            "append",
            "tambah",
            "add",
            "buat",
            "draft",
            "ubah",
            "tukar",
            "edit",
            "update",
            "ganti",
            "padam",
            "buang",
            "hapus",
            "delete",
            "remove",
        ).any { token -> value.contains(token) }
        val targetHint = listOf(
            "@",
            "page ini",
            "current page",
            "this page",
            "sini",
            "dalam page",
            "dekat page",
            "property",
            "block",
            "blok",
        ).any { token -> value.contains(token) }
        return mutationIntent && targetHint
    }

    private fun String.extractTableTitle(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(buat|create|cipta|tambah|add|masukkan|letak|untuk|catat|rekod|record|table|database|jadual|dalam|dekat|di|page|ini|sini|this|current)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractNewTableTitle(pageTitle: String): String {
        val cleaned = removeTargetMention(pageTitle)
            .replace(Regex("(?i)\\b(ubah|tukar|ganti|jadikan|change|set|rename|nama|name|title|table|database|jadual)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val afterMarker = listOf(
            "dengan nama ",
            "kepada ",
            "menjadi ",
            "jadikan ",
            "dengan ",
            "jadi ",
            "to ",
            "into ",
            "as ",
            " dengan nama ",
            " kepada ",
            " menjadi ",
            " jadikan ",
            " dengan ",
            " jadi ",
            " to ",
            " into ",
            " as ",
        ).firstNotNullOfOrNull { marker ->
            cleaned.substringAfter(marker, missingDelimiterValue = "")
                .takeIf { value -> value.isNotBlank() }
        } ?: cleaned

        val title = afterMarker
            .replace(Regex("(?i)\\b(yang|baru|new)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\s+(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\b\\s*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '"', '\'')
        return title.takeUnless { candidate -> candidate.isQualitativeTableTitleRequest() }.orEmpty()
    }

    private fun String.isQualitativeTableTitleRequest(): Boolean {
        val normalized = lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isBlank()) return true
        val descriptorWords = setOf(
            "sesuai",
            "sensuai",
            "sesual",
            "sensual",
            "appropriate",
            "suitable",
            "better",
            "nice",
            "good",
            "bagus",
            "kemas",
            "cantik",
            "proper",
            "short",
            "simple",
            "ringkas",
            "pendek",
        )
        return normalized.split(Regex("\\s+")).all { word -> word in descriptorWords }
    }

    private fun String.defaultRecoveredTableColumns(promptValue: String): List<AiTableColumnItem> =
        if (looksLikeExpenseText() || promptValue.looksLikeExpenseText()) {
            listOf(
                AiTableColumnItem(name = "Name", type = "Text"),
                AiTableColumnItem(name = "Amount", type = "Number"),
                AiTableColumnItem(name = "Date", type = "Date"),
                AiTableColumnItem(name = "Notes", type = "Text"),
            )
        } else {
            listOf(
                AiTableColumnItem(name = "Name", type = "Text"),
                AiTableColumnItem(name = "Status", type = "Status"),
                AiTableColumnItem(name = "Notes", type = "Text"),
            )
        }

    private fun String.looksLikeExpenseText(): Boolean {
        val value = lowercase()
        return extractMoneyAmount() != null ||
            listOf("duit", "belanja", "expense", "expenses", "spend", "makan", "ringgit", "rm", "harga", "jumlah")
                .any { token -> value.contains(token) }
    }

    private fun AiPageContext.hasAnyTable(): Boolean =
        blocks.any { block ->
            block.type.equals("DatabaseTable", ignoreCase = true) || block.tableTitle.isNotBlank()
        }

    private fun AiPageContext.defaultTableTitle(): String? {
        blocks.firstOrNull { block ->
            block.type.equals("DatabaseTable", ignoreCase = true) && block.tableTitle.isNotBlank()
        }?.tableTitle?.let { return it }
        blocks.firstOrNull { block ->
            block.type.equals("DatabaseTable", ignoreCase = true)
        }?.text?.let { text ->
            Regex("(?i)title=([^;]+)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return blocks.firstOrNull { block -> block.tableTitle.isNotBlank() }?.tableTitle
    }

    private fun String.looksLikeTableRowRequest(): Boolean {
        val value = lowercase()
        val hasRowIntent = listOf("row", "baris", "rekod", "record")
            .any { token -> value.contains(token) }
        val hasAddIntent = listOf("tambah", "add", "masukkan", "letak", "insert", "create", "catat")
            .any { token -> value.contains(token) }
        val hasCreateTableIntent = listOf("table", "database", "jadual")
            .any { token -> value.contains(token) } &&
            listOf("buat", "create", "cipta")
                .any { token -> value.contains(token) }
        val isExplicitNoteWrite = listOf("nota", "note", "memo", "isi", "content").any { token ->
            value.contains(token)
        } && looksLikePageWriteRequest()
        val hasExpenseDataHint = extractMoneyAmount() != null ||
            listOf("makan", "ringgit", "rm", "harga", "jumlah", "tarikh", "hari ini", "harini", "today")
            .any { token -> value.contains(token) }
        if (isExplicitNoteWrite) return false
        if (hasCreateTableIntent && !hasRowIntent) return false
        return (hasRowIntent && hasAddIntent) || (hasExpenseDataHint && !hasCreateTableIntent)
    }

    private fun String.looksLikeTaskRowRequest(): Boolean {
        val value = lowercase()
        return listOf("task", "todo", "reminder", "ingatkan", "deadline", "due", "appointment", "jadualkan")
            .any { token -> value.contains(token) }
    }

    private fun String.looksLikeTableContextOnlyRequest(): Boolean {
        val value = lowercase()
        val hasContextIntent = listOf("catat", "rekod", "record", "track", "tracking").any { token ->
            value.contains(token)
        } &&
            listOf("duit", "belanja", "expense", "expenses", "spending").any { token ->
                value.contains(token)
            } &&
            listOf("bulanan", "monthly", "bulan", "harian", "daily").any { token ->
                value.contains(token)
            }
        return hasContextIntent && !looksLikeTableRowRequest()
    }

    private fun String.extractTableRowText(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(tambah|add|masukkan|letak|insert|create|buat|catat|satu|1|row|baris|rekod|record|dalam|dekat|di|ke|to|into|table|database|jadual|page|ini|tersebut|yang|saya|guna|pakai|use|used|untuk|tu|dah|sekali)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractMoneyAmount(): String? {
        val match = Regex("(?i)(?:rm\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:ringgit|rm)?")
            .find(this)
            ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.takeIf { value -> value.isNotBlank() }
    }

    private fun String.removeMoneyAmount(): String =
        replace(Regex("(?i)\\b(?:rm\\s*)?\\d+(?:[.,]\\d+)?\\s*(?:ringgit|rm)?\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.removeMetricRequestWords(): String =
        replace(Regex("(?i)\\b(amount|jumlah|harga|price|cost|total|nilai|value)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.removeDateRequestWords(): String =
        replace(Regex("(?i)\\b(hari\\s*ini|harini|today|tarikh|date|sekali)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.inferredDateValue(): String? {
        val value = lowercase()
        val wantsToday = listOf("hari ini", "harini", "today").any { token -> value.contains(token) }
        val wantsDate = wantsToday || listOf("tarikh", "date").any { token -> value.contains(token) }
        return if (wantsDate) LocalDate.now().toString() else null
    }

    private fun String.actionSegments(): List<String> {
        val splitText = replace(Regex("(?i)\\b(lepas tu|selepas tu|pastu|astu|then|next)\\b"), "\n")
            .replace(
                Regex("(?i)\\s*,\\s*(?=(dan\\s+)?(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
            .replace(
                Regex("(?i)\\b(dan|and)\\s+(?=(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
        return splitText
            .lineSequence()
            .map { segment -> segment.trim(' ', ',', '.', ';', ':', '-') }
            .filter { segment -> segment.isNotBlank() }
            .toList()
            .ifEmpty { listOf(trim()) }
    }

    private fun String.bestTableRowSegment(): String =
        actionSegments()
            .lastOrNull { segment -> segment.looksLikeTableRowRequest() }
            ?: this

    private fun String.requestsAllBlocksDeletion(): Boolean {
        val value = lowercase()
        val hasDeleteIntent = listOf("padam", "buang", "hapus", "delete", "remove", "clear", "kosongkan")
            .any { token -> value.contains(token) }
        val hasAllIntent = listOf("semua", "all", "every", "keseluruhan", "seluruh")
            .any { token -> value.contains(token) }
        val hasBlockIntent = listOf("block", "blok", "blocks")
            .any { token -> value.contains(token) }
        return hasDeleteIntent && hasAllIntent && hasBlockIntent
    }

    private fun String.looksLikePageWriteRequest(): Boolean {
        val value = lowercase()
        val hasWriteIntent = listOf(
            "write",
            "tulis",
            "catat",
            "masukkan",
            "insert",
            "append",
            "add note",
            "tambah nota",
            "nota",
            "note",
            "isi",
            "content",
            "memo",
            "buat isi",
            "draft",
            "karangan",
        ).any { token -> value.contains(token) }
        if (!hasWriteIntent) return false

        val nonWriteIntent = listOf(
            "delete",
            "hapus",
            "buang",
            "rename",
            "table",
            "database",
            "row",
            "column",
            "property",
            "block",
            "blok",
        ).any { token -> value.contains(token) }
        return !nonWriteIntent
    }

    private fun String.extractWriteContent(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(Regex("(?i)\\b(tolong|please|buat|create|write|tulis|catat|masukkan|insert|append|draft|nota|note|memo|isi|content|buat isi|tambah nota|add note)\\b"), " ")
            .replace(Regex("(?i)\\b(dalam|dekat|di|ke|to|into|page|ini|sini|this page|current page)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractPropertyName(pageTitle: String): String {
        val typeWords = setOf(
            "text",
            "number",
            "nombor",
            "select",
            "multi",
            "multiselect",
            "status",
            "date",
            "tarikh",
            "person",
            "files",
            "media",
            "checkbox",
            "url",
            "email",
            "phone",
            "telefon",
            "formula",
            "relation",
            "rollup",
            "button",
            "place",
            "tempat",
            "id",
        )
        val cleaned = removeTargetMention(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(tambah|add|create|buat|masukkan|letak|ubah|tukar|edit|update|set|jadikan|change|padam|buang|hapus|delete|remove|property|properties|prop|type|jenis|dalam|dekat|di|page|ini|sini|this|current)\\b",
                ),
                " ",
            )
            .replace(Regex("(?i)\\b(kepada|ke|as|sebagai|value|nilai|with|dengan)\\b.*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

        return cleaned
            .split(" ")
            .filter { token -> token.isNotBlank() && token.lowercase() !in typeWords }
            .joinToString(" ")
            .trim()
    }

    private fun String.extractPropertyValue(pageTitle: String): String {
        val withoutMention = removeTargetMention(pageTitle)
        val match = Regex("(?i)\\b(value|nilai|kepada|ke|as|sebagai|dengan|with)\\b\\s+(.+)$")
            .find(withoutMention)
            ?: return ""
        return match.groupValues.getOrNull(2)
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.inferPropertyType(): String {
        val value = lowercase()
        return when {
            value.contains("number") || value.contains("nombor") || value.contains("jumlah") || value.contains("harga") -> "Number"
            value.contains("multi-select") || value.contains("multiselect") || value.contains("multi select") -> "MultiSelect"
            value.contains("select") -> "Select"
            value.contains("status") -> "Status"
            value.contains("date") || value.contains("tarikh") || value.contains("deadline") || value.contains("due") -> "Date"
            value.contains("person") || value.contains("orang") -> "Person"
            value.contains("file") || value.contains("media") || value.contains("gambar") -> "FilesMedia"
            value.contains("checkbox") || value.contains("check") || value.contains("tick") || value.contains("siap") -> "Checkbox"
            value.contains("url") || value.contains("link") -> "Url"
            value.contains("email") -> "Email"
            value.contains("phone") || value.contains("telefon") -> "Phone"
            value.contains("formula") || value.contains("kira") -> "Formula"
            value.contains("relation") || value.contains("hubungan") -> "Relation"
            value.contains("rollup") -> "Rollup"
            value.contains("button") -> "Button"
            value.contains("place") || value.contains("tempat") || value.contains("lokasi") -> "Place"
            value.contains("id") -> "Id"
            else -> "Text"
        }
    }

    private fun String.extractBlockReference(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(tambah|add|buat|masukkan|letak|tulis|catat|padam|buang|hapus|delete|remove|block|blok|text|heading|tajuk|todo|checklist|quote|petikan|divider|garisan|media|file|gambar|dalam|dekat|di|page|ini|sini|this|current)\\b",
                ),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.splitBlockReplacement(pageTitle: String): Pair<String, String>? {
        val cleaned = removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(ubah|tukar|edit|update|ganti|jadikan|change|block|blok|text|heading|tajuk|todo|checklist|quote|petikan|divider|garisan|media|file|gambar|dalam|dekat|di|page|ini|sini|this|current)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val parts = Regex("(?i)\\s+\\b(kepada|ke|jadi|menjadi|dengan|to|into|with)\\b\\s+")
            .split(cleaned, limit = 2)
        if (parts.size != 2) return null
        val from = parts[0].trim(' ', '-', ':')
        val to = parts[1].trim(' ', '-', ':')
        if (from.isBlank() || to.isBlank()) return null
        return from to to
    }

    private fun String.inferBlockType(): String {
        val value = lowercase()
        return when {
            value.contains("database") || value.contains("table") -> "DatabaseTable"
            value.contains("heading") || value.contains("tajuk") -> "Heading"
            value.contains("bullet") || value.contains("list") || value.contains("senarai") -> "Bullet"
            value.contains("quote") || value.contains("petikan") -> "Quote"
            value.contains("todo") || value.contains("checklist") -> "Todo"
            value.contains("divider") || value.contains("garisan") || value.contains("line") -> "Divider"
            value.contains("media") || value.contains("file") || value.contains("gambar") -> "MediaFile"
            else -> "Text"
        }
    }

    private fun String.removeTargetMention(pageTitle: String): String =
        replace("@$pageTitle", "", ignoreCase = true)
            .replace(Regex("@[^\\s,.;:]+"), " ")

    private fun String.normalizeForAiMatch(): String =
        lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.normalizedActionType(): String =
        trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

    private fun String.recoveryReply(
        malay: String,
        english: String,
    ): String = if (prefersMalayReply()) malay else english

    private fun String.prefersMalayReply(): Boolean {
        val value = lowercase()
        return listOf(
            "saya",
            "awak",
            "tolong",
            "boleh",
            "nak",
            "tulis",
            "catat",
            "tambah",
            "masukkan",
            "ubah",
            "tukar",
            "padam",
            "buang",
            "hapus",
            "dekat",
            "dalam",
            "sini",
            "page ini",
            "tarikh",
        ).any { token -> value.contains(token) }
    }

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
