package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
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
) {
    private val logger = LoggerFactory.getLogger(AiService::class.java)

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
        val newPropertyName: String = "",
        val propertyType: String = "Text",
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
        val rowIds: List<String> = emptyList(),
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
        val diagnostics: AiDiagnostics = AiDiagnostics(),
    )

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
        )

        val promptResult = if (modelResult != null && reply.canUsePromptActionRecovery()) {
            recoverActionFromPrompt(prompt = userMessage, pages = pages)
        } else {
            null
        }
        selectActionResultForPrompt(
            prompt = userMessage,
            modelResult = modelResult,
            promptResult = promptResult,
        )?.let { result -> return result.copy(diagnostics = preparedMessages.diagnostics) }

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

            $actionContractPrompt

            Decision rules:
            - Home request to create a new tracker/jadual/table/page: use CREATE_PAGE with tableTitle, tableColumns, and tableRows when useful.
            - Request inside or mentioning an existing page to create a table: use CREATE_DATABASE with targetTitle.
            - Request to add spending/expense/record to an existing budget/monthly expense page: use ADD_TABLE_ROW with tableTitle "Transactions", Category, Type, Amount, Status, Month (YYYY-MM) when known, and Date when known. Do not create a new table unless user asks for a new table/page.
            - CYL_MENTION_CONTEXT may contain either explicit page mentions or the currently open default page; follow its targeting instructions exactly.
            - A page explicitly selected with @ overrides the currently open default page.
            - If the visible request clearly names another page, use that exact page in targetTitle instead of forcing the action onto the current page.
            - If several pages are explicitly selected, include the exact targetTitle on every mutation action so each action can be routed independently.
            - If one current/mentioned page is clearly in context and the user does not name another page, use that page.
            - For date words like harini/today, use the client date.
            - For money like "29 ringgit", put the numeric value in an amount/price/cost column if such column exists or create a Number column.
            - To change one existing cell, use UPDATE_TABLE_CELL with the exact table, row, column, and value. Text and Number cells are editable data: never convert them to Select/Status or modify dropdown options unless the user explicitly asks to change the property type/options.
            - To intentionally clear/delete one cell value, use CLEAR_TABLE_CELL with its exact rowId/rowTitle and columnId/columnName. Do not delete the row or column.
            - To clear every cell in one column whose current value matches a query, use CLEAR_TABLE_CELLS with tableTitle, columnId/columnName, and filterQuery. This is a bulk cell operation: do not ask for one row when the user explicitly says all/semua.
            - CLEAR_TABLE_CELLS must only clear matching cells. It must never delete rows, delete the column, or clear values from other columns.
            - To delete an entire database/table block, use DELETE_BLOCK with blockType "DatabaseTable" and the exact blockId or table title in blockText. Do not claim table deletion is unsupported.
            - If a value such as "bulan 4" uniquely identifies one existing cell, use its hidden rowId and columnId. If several cells match, ask which table/row instead of guessing.
            - To change several cells in one existing row, use UPDATE_TABLE_ROW with cellValues.
            - If the user explicitly asks to update or delete all matching rows, use UPDATE_TABLE_ROWS or DELETE_TABLE_ROWS. Prefer exact rowIds from context; otherwise provide columnName/columnId plus filterQuery. Never turn a bulk request into repeated ambiguous single-row actions.
            - For update/delete/move row actions, prefer the exact rowId from CYL_SEARCH_CONTEXT when available. If the user identifies a row by another property such as Month, resolve that search result to its rowId instead of inventing a row title.
            - Use DUPLICATE_TABLE_ROW, DUPLICATE_TABLE_COLUMN, or DUPLICATE_DATABASE when the user asks to copy an existing database item. Use newRowTitle/newColumnName/title only when a new name is requested.
            - Use MOVE_BLOCK with targetIndex or moveDirection "up"/"down"; use INDENT_BLOCK and OUTDENT_BLOCK for nesting; use DUPLICATE_BLOCK for a copied block.
            - Use RENAME_PROPERTY, MOVE_PROPERTY, and DUPLICATE_PROPERTY for page properties. Property actions are not table-column actions.
            - For page lifecycle requests use MOVE_PAGE, DUPLICATE_PAGE, TRASH_PAGE, RESTORE_PAGE, or DELETE_PAGE_PERMANENTLY. Permanent delete is only valid for a page already in trash.
            - For MOVE_PAGE, parentPageTitle/parentPageId identifies the new parent; omit both to move to workspace root.
            - To connect a database to another database page, use ATTACH_TABLE_DATA_SOURCE with sourcePageId/sourcePageTitle and sourceTableBlockId/sourceTableTitle. Use CLEAR_TABLE_DATA_SOURCE to disconnect it.
            - For table creation, infer sensible columns and rows from the user's intent instead of using fixed templates.
            - Do not set table sort, filter, group, hidden columns, or view rules when creating a page/table unless the user explicitly asks for those controls. A normal monthly expenses request should create data/schema only; user can filter/sort/group manually later.
            - For monthly expenses/budget with salary and spending data, prefer a transaction ledger plus summary:
              first CREATE_PAGE with tableTitle "Transactions" and columns Name, Date, Month Select, Category Select, Type Select, Amount Number, Status, Notes;
              then CREATE_DATABASE on that page with tableTitle "Monthly Summary" and columns Month Select, Status, Notes only. The app will wire monthly Income/Known Expenses/Debt rollups and Balance formula.
            - If a category has multiple amounts like "Makan: 3+8.9+4+5+", create separate transaction rows and mark Status "Incomplete" when the expression ends with +.
            - Use Type "Income" for gaji/salary/income, "Debt" for hutang/debt, otherwise "Expense".
            - For Select, MultiSelect, or Status dropdown values, include options as a string array on the column or action.
            - If the user asks for a category dropdown, use columnType "Select" and include category options.
            - If the user asks to be reminded, use CREATE_REMINDER. Include a clear title and either positive delayMinutes for a relative time or cellValues with Date for an absolute date. Do not use CREATE_TASK as a substitute for a reminder.
            - Use CANCEL_REMINDER, RESCHEDULE_REMINDER, or COMPLETE_REMINDER for an existing reminder row. Include the exact rowId when context provides it and the Date column when a table has several Date columns.
            - If the user asks for a task without a reminder, use CREATE_TASK. A task date is optional and does not schedule a notification by itself.
            - For multi-step requests, return multiple actions in order.

            Examples:
            User: buatkan page baru untuk bulan 7 punya monthly expenses,dengan gaji 1488
            JSON: {"reply":"Siap - saya buat page monthly expenses bulan 7.","actions":[{"type":"CREATE_PAGE","title":"July Monthly Expenses","tableTitle":"Transactions","tableColumns":[{"name":"Name","type":"Text"},{"name":"Date","type":"Date"},{"name":"Month","type":"Select","options":["2026-07"]},{"name":"Category","type":"Select","options":["Salary","Food","Fuel","Makeup","Transport","Other"]},{"name":"Type","type":"Select","options":["Expense","Income","Debt"]},{"name":"Amount","type":"Number"},{"name":"Status","type":"Status","options":["Confirmed","Incomplete","Empty"]},{"name":"Notes","type":"Text"}],"tableRows":[{"Name":"Salary","Month":"2026-07","Category":"Salary","Type":"Income","Amount":"1488","Status":"Confirmed"}]},{"type":"CREATE_DATABASE","targetTitle":"July Monthly Expenses","tableTitle":"Monthly Summary","tableColumns":[{"name":"Month","type":"Select","options":["2026-07"]},{"name":"Status","type":"Status","options":["Confirmed","Incomplete","Empty"]},{"name":"Notes","type":"Text"}],"tableRows":[{"Month":"2026-07","Status":"Confirmed","Notes":"Balance = Income - Known Expenses - Debt"}]}]}

            User: tambah property Category dropdown dengan Food, Fuel, Makeup
            JSON: {"reply":"Siap - saya tambah dropdown Category.","actions":[{"type":"ADD_TABLE_COLUMN","columnName":"Category","columnType":"Select","options":["Food","Fuel","Makeup"]}]}

            User: saya guna 29 ringgit harini beli makeup
            JSON: {"reply":"Siap - saya tambah rekod belanja itu.","actions":[{"type":"ADD_TABLE_ROW","tableTitle":"Transactions","rowTitle":"makeup","cellValues":{"Name":"makeup","Category":"Makeup","Type":"Expense","Amount":"29","Status":"Confirmed","Date":"${clientDate.ifBlank { "today" }}"}}]}

            User: dalam row makeup ubah Notes jadi beli di kedai
            JSON: {"reply":"Siap - saya kemas kini catatan itu.","actions":[{"type":"UPDATE_TABLE_CELL","tableTitle":"Transactions","rowTitle":"makeup","columnName":"Notes","value":"beli di kedai"}]}

            User: padam cell Month untuk row April
            JSON: {"reply":"Siap - saya kosongkan cell itu.","actions":[{"type":"CLEAR_TABLE_CELL","tableTitle":"Monthly Summary","rowTitle":"April","columnName":"Month"}]}

            User: delete cell yang ada bulan 4 semua dalam column Month
            JSON: {"reply":"Siap - saya kosongkan semua cell Month yang sepadan dengan bulan 4.","actions":[{"type":"CLEAR_TABLE_CELLS","tableTitle":"Transactions","columnName":"Month","filterQuery":"bulan 4"}]}

            User: @Budget Tracker ubah nama table jadi Expenses
            JSON: {"reply":"Siap - saya rename table itu.","actions":[{"type":"RENAME_TABLE","targetTitle":"Budget Tracker","title":"Expenses"}]}

            User: bold perkataan ayam dalam block jadual penjagaan
            JSON: {"reply":"Siap - saya format teks itu.","actions":[{"type":"FORMAT_BLOCK_TEXT","blockText":"jadual penjagaan","textToFormat":"ayam","format":"Bold"}]}

            User: ingatkan saya bayar bil dalam 30 minit
            JSON: {"reply":"Baik, saya tetapkan peringatan bayar bil dalam 30 minit.","actions":[{"type":"CREATE_REMINDER","title":"Bayar bil","delayMinutes":30}]}

            User: padam semua transaksi untuk Month 2026-04
            JSON: {"reply":"Baik, saya padam semua transaksi April 2026.","actions":[{"type":"DELETE_TABLE_ROWS","tableTitle":"Transactions","columnName":"Month","filterQuery":"2026-04"}]}

            User: pindahkan block catatan ke atas
            JSON: {"reply":"Baik, saya pindahkan block itu.","actions":[{"type":"MOVE_BLOCK","blockText":"catatan","moveDirection":"up"}]}

            User: sambungkan table ini dengan database Orders dalam page Sales
            JSON: {"reply":"Baik, saya sambungkan data source itu.","actions":[{"type":"ATTACH_TABLE_DATA_SOURCE","sourcePageTitle":"Sales","sourceTableTitle":"Orders"}]}

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

    private data class PreparedAttachmentContext(
        val messages: List<ChatMessage>,
        val diagnostics: AiDiagnostics,
    )

    private data class AttachmentContext(
        val content: String,
        val diagnostics: AiDiagnostics,
    )

    private data class VisionAnalysisResult(
        val content: String = "",
        val provider: String = "",
        val model: String = "",
        val status: String = "",
        val warning: String = "",
    )

    private fun List<ChatMessage>.withImageContext(images: List<AiImageInput>): List<ChatMessage> =
        withAttachmentContext(images).messages

    private fun List<ChatMessage>.withWebSearchContext(webContext: WebSearchContext): List<ChatMessage> {
        val promptContext = webContext.toPromptContext()
        if (promptContext.isBlank()) return this
        val lastUserIndex = indexOfLast { message -> message.role.equals("user", ignoreCase = true) }
        return if (lastUserIndex < 0) {
            this + ChatMessage(role = "user", content = promptContext)
        } else {
            mapIndexed { index, message ->
                if (index == lastUserIndex) {
                    message.copy(
                        content = """
                            ${message.content}

                            $promptContext
                        """.trimIndent(),
                    )
                } else {
                    message
                }
            }
        }
    }

    private fun AiDiagnostics.withWebSearchContext(webContext: WebSearchContext): AiDiagnostics {
        val mergedWarning = listOf(warning, webContext.warning)
            .filter { message -> message.isNotBlank() }
            .joinToString(separator = " | ")
            .take(MaxDiagnosticsWarningChars)
        return copy(
            webSearchAttempted = true,
            webSearchProvider = webContext.provider,
            webSearchStatus = webContext.status,
            webSearchResultCount = webContext.results.size,
            warning = mergedWarning,
        )
    }

    private fun String.shouldAutoUseWebSearch(): Boolean {
        val lower = lowercase()
        return WebSearchTriggerPhrases.any { trigger -> lower.contains(trigger) }
    }

    private fun List<ChatMessage>.withAttachmentContext(images: List<AiImageInput>): PreparedAttachmentContext {
        val context = buildAttachmentContext(images)
        if (context.content.isBlank()) {
            return PreparedAttachmentContext(
                messages = this,
                diagnostics = context.diagnostics,
            )
        }
        val lastUserIndex = indexOfLast { message -> message.role.equals("user", ignoreCase = true) }
        val prepared = if (lastUserIndex < 0) {
            this + ChatMessage(role = "user", content = context.content)
        } else {
            mapIndexed { index, message ->
                if (index == lastUserIndex) {
                    message.copy(
                        content = """
                            ${message.content}

                            ${context.content}
                        """.trimIndent(),
                    )
                } else {
                    message
                }
            }
        }
        return PreparedAttachmentContext(
            messages = prepared,
            diagnostics = context.diagnostics,
        )
    }

    private fun buildAttachmentContext(images: List<AiImageInput>): AttachmentContext {
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
        if (validImages.isEmpty() && textFiles.isEmpty()) {
            return AttachmentContext(
                content = "",
                diagnostics = images.toAttachmentDiagnostics(phase = "no_attachments"),
            )
        }

        val visionResult = when {
            validImages.isEmpty() -> VisionAnalysisResult(status = "not_attempted")
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

        val content = """
            CYL_FILE_CONTEXT:
            The user attached ${validImages.size} image(s) and ${textFiles.size} readable text file(s).
            Use this extracted attachment context as user-provided evidence.
            If Image context says image reading failed or unavailable, tell the user the attachment reached CYL but CYL could not extract readable image context. Ask the user to retry or check the LM Studio vision model/tunnel. Do not mention provider status codes unless the user asks.
            Do not mention internal attachment pipeline details unless the user asks.
            ${if (visionResult.content.isNotBlank()) "Image context:\n${visionResult.content}" else ""}
            ${if (textSummary.isNotBlank()) "Text file context:\n$textSummary" else ""}
        """.trimIndent()
        return AttachmentContext(
            content = content,
            diagnostics = AiDiagnostics(
                phase = "attachments_prepared",
                imageCount = validImages.size,
                textFileCount = textFiles.size,
                visionAttempted = validImages.isNotEmpty(),
                visionProvider = visionResult.provider,
                visionModel = visionResult.model,
                visionStatus = visionResult.status,
                visionPipelineVersion = VisionPipelineVersion,
                warning = visionResult.warning,
            ),
        )
    }

    private fun List<AiImageInput>.toAttachmentDiagnostics(phase: String): AiDiagnostics {
        val imageCount = count { image ->
            image.dataUrl.startsWith("data:image/", ignoreCase = true) ||
                image.kind.equals("image", ignoreCase = true)
        }
        val textFileCount = count { file -> file.textContent.isNotBlank() }
        return AiDiagnostics(
            phase = phase,
            imageCount = imageCount.coerceAtMost(MaxVisionImages),
            textFileCount = textFileCount.coerceAtMost(MaxTextContextFiles),
            visionAttempted = imageCount > 0,
            visionProvider = when {
                imageCount == 0 -> ""
                !lmStudioBaseUrl.isNullOrBlank() -> "lmstudio"
                !openRouterApiKey.isNullOrBlank() -> "openrouter"
                else -> ""
            },
            visionModel = when {
                imageCount == 0 -> ""
                !lmStudioBaseUrl.isNullOrBlank() -> lmStudioVisionModels.firstOrNull().orEmpty()
                !openRouterApiKey.isNullOrBlank() -> openRouterVisionModels.firstOrNull().orEmpty()
                else -> ""
            },
            visionStatus = if (imageCount > 0) "queued" else "",
            visionPipelineVersion = VisionPipelineVersion,
        )
    }

    private fun analyzeImagesWithVisionFallback(images: List<AiImageInput>): VisionAnalysisResult {
        val failures = mutableListOf<String>()

        if (!lmStudioBaseUrl.isNullOrBlank()) {
            val lmStudioResult = analyzeImagesWithLmStudioFallback(images)
            if (lmStudioResult.status != "failed") return lmStudioResult
            failures += lmStudioResult.content
            return lmStudioResult
        }

        if (!openRouterApiKey.isNullOrBlank()) {
            val openRouterResult = analyzeImagesWithOpenRouterFallback(images)
            if (openRouterResult.status != "failed") {
                return openRouterResult
            }
            failures += openRouterResult.content
        }

        if (failures.isEmpty()) {
            val warning = "Image reading unavailable because LMSTUDIO_BASE_URL or OPENROUTER_API_KEY is not configured."
            return VisionAnalysisResult(
                content = warning,
                status = "unavailable",
                warning = warning,
            )
        }
        val warning = failures.joinToString(separator = "\n\n")
        return VisionAnalysisResult(
            content = warning,
            status = "failed",
            warning = warning,
        )
    }

    private fun analyzeImagesWithLmStudioFallback(images: List<AiImageInput>): VisionAnalysisResult {
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
                    stream = true,
                )
            }.onSuccess { content ->
                if (content.isNotBlank()) {
                    return VisionAnalysisResult(
                        content = """
                        Vision model used: $model
                        $content
                        """.trimIndent(),
                        provider = "lmstudio",
                        model = model,
                        status = "succeeded",
                    )
                }
                failures += "$model: empty response"
            }.onFailure { error ->
                failures += "$model: ${error.compactVisionError()}"
            }
        }

        val warning = """
            Image reading failed after trying ${models.size} LM Studio vision model(s).
            CYL is configured to use LM Studio for image reading, so OpenRouter vision fallback was skipped.
            Endpoint: $completionsUrl
            Tried:
            ${failures.joinToString(separator = "\n") { failure -> "- $failure" }}
        """.trimIndent()
        return VisionAnalysisResult(
            content = warning,
            provider = "lmstudio",
            model = models.joinToString(","),
            status = "failed",
            warning = warning,
        )
    }

    private fun analyzeImagesWithOpenRouterFallback(images: List<AiImageInput>): VisionAnalysisResult {
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
                    return VisionAnalysisResult(
                        content = """
                        Vision model used: $model
                        $content
                        """.trimIndent(),
                        provider = "openrouter",
                        model = model,
                        status = "succeeded",
                    )
                }
                failures += "$model: empty response"
            }.onFailure { error ->
                failures += "$model: ${error.compactVisionError()}"
            }
        }

        val warning = """
            Image reading failed after trying ${models.size} vision model(s).
            Tried:
            ${failures.joinToString(separator = "\n") { failure -> "- $failure" }}
        """.trimIndent()
        return VisionAnalysisResult(
            content = warning,
            provider = "openrouter",
            model = models.joinToString(","),
            status = "failed",
            warning = warning,
        )
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
        stream: Boolean = false,
    ): String {
        val visionImages = images.map { image -> image.optimizedForVision() }
        val originalBytes = images.sumOf { image -> image.sizeBytes.coerceAtLeast(0) }
        val optimizedBytes = visionImages.sumOf { image -> image.sizeBytes.coerceAtLeast(0) }
        logger.info(
            "AI vision request prepared: pipeline={}, providerUrl={}, model={}, stream={}, images={}, originalBytes={}, optimizedBytes={}, maxDimension={}",
            VisionPipelineVersion,
            completionsUrl.withoutQuery(),
            model.ifBlank { defaultModel },
            stream,
            visionImages.size,
            originalBytes,
            optimizedBytes,
            VisionMaxImageDimension,
        )
        val prompt = buildString {
            appendLine("Read the attached image(s) for the CYL app.")
            appendLine("Return a concise plain-text OCR/context summary.")
            appendLine("If there is visible text, copy the visible text as accurately as possible.")
            appendLine("If the image is a receipt, bill, spreadsheet, screenshot, table, calendar, or note, preserve important rows, values, dates, and amounts.")
            appendLine("Use Malay/Indonesian if the image or user uses Malay/Indonesian; otherwise use English.")
            appendLine("Do not invent data that is not visible.")
            appendLine("Do not include hidden chain-of-thought. Only give the final extracted image context.")
            visionImages.forEachIndexed { index, image ->
                appendLine("Image ${index + 1}: name=${image.name.ifBlank { "image" }}, mime=${image.mimeType.ifBlank { "image/*" }}, bytes=${image.sizeBytes}")
            }
        }

        val body = buildJsonObject {
            put("model", model.ifBlank { defaultModel })
            put("temperature", 0.0)
            put("max_tokens", VisionMaxTokens)
            if (stream) {
                put("stream", true)
            }
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
                                    visionImages.forEach { image ->
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", image.dataUrl)
                                                        put("detail", "low")
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
            val startedAt = System.nanoTime()
            if (stream) {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
                if (response.statusCode() == 200) {
                    val streamContent = response.body().readVisionStreamContent()
                    logger.info(
                        "AI vision stream completed: model={}, attempt={}, durationMs={}, contentChars={}, reasoningChars={}",
                        model.ifBlank { defaultModel },
                        attempt + 1,
                        startedAt.elapsedMillis(),
                        streamContent.content.length,
                        streamContent.reasoning.length,
                    )
                    if (streamContent.content.isNotBlank()) return streamContent.content
                    if (streamContent.reasoning.containsImageBlindnessHint()) {
                        throw Exception(
                            "Vision model responded as if it did not receive image pixels. " +
                                "In LM Studio, confirm the server model is the same vision-capable model used in chat and that OpenAI-compatible image input is enabled.",
                        )
                    }
                    if (streamContent.reasoning.isNotBlank()) return streamContent.reasoning
                    throw Exception("Vision model returned an empty response.")
                }

                val error = Exception("Vision HTTP ${response.statusCode()} - ${response.body().readLinesForError()}")
                logger.warn(
                    "AI vision stream failed: model={}, attempt={}, status={}, durationMs={}, error={}",
                    model.ifBlank { defaultModel },
                    attempt + 1,
                    response.statusCode(),
                    startedAt.elapsedMillis(),
                    error.compactVisionError(),
                )
                lastError = error
                if (!response.statusCode().isRetryableVisionStatus() || attempt == VisionRequestMaxAttempts - 1) {
                    throw error
                }
            } else {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val apiResponse = json.decodeFromString<ApiResponse>(response.body())
                    val message = apiResponse.choices.firstOrNull()?.message
                        ?: throw Exception("Vision model returned no choices.")
                    val content = message.content.orEmpty().trim()
                    val reasoning = message.allReasoningText().trim()
                    logger.info(
                        "AI vision completed: model={}, attempt={}, durationMs={}, contentChars={}, reasoningChars={}",
                        model.ifBlank { defaultModel },
                        attempt + 1,
                        startedAt.elapsedMillis(),
                        content.length,
                        reasoning.length,
                    )
                    if (content.isNotBlank()) return content
                    if (reasoning.containsImageBlindnessHint()) {
                        throw Exception(
                            "Vision model responded as if it did not receive image pixels. " +
                            "In LM Studio, confirm the server model is the same vision-capable model used in chat and that OpenAI-compatible image input is enabled.",
                        )
                    }
                    if (reasoning.isNotBlank()) return reasoning
                    throw Exception("Vision model returned an empty response.")
                }

                val error = Exception("Vision HTTP ${response.statusCode()} - ${response.body()}")
                logger.warn(
                    "AI vision failed: model={}, attempt={}, status={}, durationMs={}, error={}",
                    model.ifBlank { defaultModel },
                    attempt + 1,
                    response.statusCode(),
                    startedAt.elapsedMillis(),
                    error.compactVisionError(),
                )
                lastError = error
                if (!response.statusCode().isRetryableVisionStatus() || attempt == VisionRequestMaxAttempts - 1) {
                    throw error
                }
            }
            Thread.sleep(VisionRetryDelayMillis * (attempt + 1))
        }
        throw lastError ?: Exception("Vision request failed.")
    }

    private data class VisionStreamContent(
        val content: String,
        val reasoning: String,
    )

    private fun java.util.stream.Stream<String>.readVisionStreamContent(): VisionStreamContent {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        use { lines ->
            val iterator = lines.iterator()
            while (iterator.hasNext()) {
                val line = iterator.next().trim()
                val payload = when {
                    line.startsWith("data:", ignoreCase = true) -> line.removePrefix("data:").trim()
                    line.startsWith("{") -> line
                    else -> continue
                }
                if (payload == "[DONE]") break
                appendVisionStreamPayload(
                    payload = payload,
                    content = content,
                    reasoning = reasoning,
                )
            }
        }
        return VisionStreamContent(
            content = content.toString().trim(),
            reasoning = reasoning.toString().trim(),
        )
    }

    private fun appendVisionStreamPayload(
        payload: String,
        content: StringBuilder,
        reasoning: StringBuilder,
    ) {
        runCatching {
            json.decodeFromString<ApiStreamResponse>(payload)
        }.getOrNull()?.choices.orEmpty().forEach { choice ->
            content.append(choice.delta.content.orEmpty())
            reasoning.append(choice.delta.allReasoningText())
            content.append(choice.message?.content.orEmpty())
            reasoning.append(choice.message?.allReasoningText().orEmpty())
        }

        if (content.isNotBlank() || reasoning.isNotBlank()) return

        runCatching {
            json.decodeFromString<ApiResponse>(payload)
        }.getOrNull()?.choices.orEmpty().forEach { choice ->
            content.append(choice.message.content.orEmpty())
            reasoning.append(choice.message.allReasoningText())
        }
    }

    private fun java.util.stream.Stream<String>.readLinesForError(): String =
        use { lines ->
            lines
                .limit(20)
                .toList()
                .joinToString(separator = " ")
                .replace(Regex("\\s+"), " ")
                .take(480)
        }

    private fun AiImageInput.optimizedForVision(): AiImageInput {
        if (!dataUrl.startsWith("data:image/", ignoreCase = true)) return this
        val marker = ";base64,"
        val markerIndex = dataUrl.indexOf(marker, ignoreCase = true)
        if (markerIndex < 0) return this

        return runCatching {
            val header = dataUrl.substringBefore(marker)
            val originalBytes = Base64.getDecoder().decode(dataUrl.substring(markerIndex + marker.length))
            val source = ImageIO.read(ByteArrayInputStream(originalBytes)) ?: return this
            val longestSide = maxOf(source.width, source.height).coerceAtLeast(1)
            val scale = minOf(1.0, VisionMaxImageDimension.toDouble() / longestSide.toDouble())
            val targetWidth = maxOf(1, (source.width * scale).toInt())
            val targetHeight = maxOf(1, (source.height * scale).toInt())

            if (
                scale >= 0.999 &&
                originalBytes.size <= VisionMaxImageBytes &&
                header.contains("image/jpeg", ignoreCase = true)
            ) {
                return this
            }

            val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = resized.createGraphics()
            try {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, targetWidth, targetHeight)
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
            } finally {
                graphics.dispose()
            }

            val output = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("jpg").asSequence().firstOrNull()
            if (writer == null) {
                ImageIO.write(resized, "jpg", output)
            } else {
                val imageOutput = ImageIO.createImageOutputStream(output)
                try {
                    writer.output = imageOutput
                    val params = writer.defaultWriteParam
                    if (params.canWriteCompressed()) {
                        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        params.compressionQuality = VisionJpegQuality
                    }
                    writer.write(null, IIOImage(resized, null, null), params)
                } finally {
                    imageOutput.close()
                    writer.dispose()
                }
            }

            val optimizedBytes = output.toByteArray()
            copy(
                dataUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(optimizedBytes)}",
                mimeType = "image/jpeg",
                name = name.ifBlank { "image" }.replaceAfterLast('.', "jpg", missingDelimiterValue = "${name.ifBlank { "image" }}.jpg"),
                sizeBytes = optimizedBytes.size.toLong(),
                kind = "image",
            )
        }.getOrElse {
            this
        }
    }

    private fun Int.isRetryableVisionStatus(): Boolean =
        this == 408 || this == 409 || this == 425 || this == 429 || this >= 500

    private fun String.containsImageBlindnessHint(): Boolean {
        val lower = lowercase()
        return lower.contains("cannot see") ||
            lower.contains("can't see") ||
            lower.contains("cannot actually see") ||
            lower.contains("cannot process") && lower.contains("image") ||
            lower.contains("no actual image") ||
            lower.contains("image data accessible") ||
            lower.contains("text-only")
    }

    private fun Throwable.compactVisionError(): String {
        return (localizedMessage ?: message ?: this::class.simpleName.orEmpty())
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .take(320)
    }

    private fun String.withoutQuery(): String =
        substringBefore('?').take(180)

    private fun Long.elapsedMillis(): Long =
        Duration.ofNanos(System.nanoTime() - this).toMillis()

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
        val reasoning_content: String? = null,
        val reasoning: String? = null,
    )

    private fun ApiMessage.allReasoningText(): String =
        listOf(reasoning_content, reasoning)
            .filterNot { value -> value.isNullOrBlank() }
            .joinToString(separator = "\n")

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

    @Serializable
    private data class ApiStreamResponse(
        val choices: List<ApiStreamChoice> = emptyList(),
    )

    @Serializable
    private data class ApiStreamChoice(
        val delta: ApiStreamDelta = ApiStreamDelta(),
        val message: ApiMessage? = null,
    )

    @Serializable
    private data class ApiStreamDelta(
        val content: String? = null,
        val reasoning_content: String? = null,
        val reasoning: String? = null,
    )

    private fun ApiStreamDelta.allReasoningText(): String =
        listOf(reasoning_content, reasoning)
            .filterNot { value -> value.isNullOrBlank() }
            .joinToString(separator = "\n")

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
        const val VisionMaxTokens = 2000
        const val VisionPipelineVersion = "lmstudio-stream-resize-v5"
        const val VisionMaxImageDimension = 640
        const val VisionMaxImageBytes = 350 * 1024
        const val VisionJpegQuality = 0.76f
        const val MaxTextContextFiles = 4
        const val MaxTextContextChars = 16_000
        const val MaxDiagnosticsWarningChars = 500
        val AiRequestTimeout: Duration = Duration.ofMinutes(5)
        const val DefaultLmStudioModel = "qwen/qwen3.5-9b"
        val DefaultLmStudioVisionModels = listOf("qwen/qwen3.5-9b")
        const val OpenRouterCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions"
        val DefaultVisionModels = listOf(
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-3-4b-it:free",
            "google/gemini-2.0-flash-exp:free",
        )
        val WebSearchTriggerPhrases = listOf(
            "search web",
            "web search",
            "search internet",
            "cari web",
            "cari internet",
            "carikan di web",
            "carikan internet",
            "google",
            "browse",
            "browsing",
            "latest",
            "terkini",
            "terbaru",
            "paling baru",
            "sekarang",
            "hari ini",
            "today",
            "current",
            "recent",
            "news",
            "berita",
            "harga",
            "price",
            "model terbaru",
            "latest model",
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
