package com.changeyourlife.cyl.backend.service

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
    private val openRouterModel: String = "openai/gpt-oss-120b:free",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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
        "openrouter" -> openRouterModel.ifBlank { "openai/gpt-oss-120b:free" }
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
                apiResponse.choices.firstOrNull()?.message?.content
                    ?: "Sorry, I generated an empty response."
            } else {
                "Error: Backend AI request failed with status code ${response.statusCode()}.\nResponse: ${response.body()}"
            }
        } catch (e: Exception) {
            "Error contacting AI completions endpoint: ${e.localizedMessage}"
        }
    }

    @Serializable
    private data class AiActionJsonResponse(
        val reply: String = "",
        val actions: List<AiActionItem> = emptyList()
    )

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

    data class AiActionResult(val reply: String, val actions: List<AiActionItem>)

    fun chatWithActions(
        messages: List<ChatMessage>,
        pages: List<AiPageContext> = emptyList(),
        tasks: List<AiTaskContext> = emptyList()
    ): AiActionResult {
        if (isMockMode) {
            return AiActionResult(
                reply = "[AI Sandbox Mode] This is a simulated chat-with-actions response. Add OPENROUTER_API_KEY to enable live AI.",
                actions = emptyList()
            )
        }

        return try {
            val pageContext = pages.joinToString(separator = "\n") { page ->
                val blockContext = page.blocks
                    .joinToString(separator = "\n") { block ->
                        val normalizedText = block.text
                            .replace("\n", " ")
                            .ifBlank { "empty" }
                        val textLimit = when {
                            block.type.equals("DatabaseTable", ignoreCase = true) -> 1_800
                            block.rowId.isNotBlank() -> 600
                            else -> 320
                        }
                        val text = normalizedText.take(textLimit)
                        val tableTitle = block.tableTitle
                            .replace("\n", " ")
                            .ifBlank { "" }
                        val tableSuffix = if (tableTitle.isBlank()) "" else "; tableTitle=$tableTitle"
                        val tableBlockSuffix = block.tableBlockId.ifBlank { "" }.let { tableBlockId ->
                            if (tableBlockId.isBlank()) "" else "; tableBlockId=$tableBlockId"
                        }
                        val rowSuffix = block.rowId.ifBlank { "" }.let { rowId ->
                            if (rowId.isBlank()) {
                                ""
                            } else {
                                val rowTitle = block.rowTitle.replace("\n", " ").ifBlank { "empty" }
                                val rowBlockId = block.rowBlockId.ifBlank { block.id }
                                "; rowId=$rowId; rowTitle=$rowTitle; rowBlockId=$rowBlockId"
                            }
                        }
                        val checkedSuffix = block.isChecked?.let { checked -> "; checked=$checked" }.orEmpty()
                        "  - blockId=${block.id}; path=${block.path}; type=${block.type}; text=$text$tableSuffix$tableBlockSuffix$rowSuffix$checkedSuffix"
                    }
                    .ifBlank { "  - No block outline supplied." }
                "- ${page.title}\n$blockContext"
            }
                .ifBlank { "No existing pages." }
                .take(32_000)
            val taskContext = tasks.joinToString(separator = "\n") { task -> "- ${task.title}" }
                .ifBlank { "No open tasks." }
            val systemPrompt = """
                You are CYL (ChangeYourLife), a helpful personal productivity AI assistant.
                You MUST respond with a JSON object containing:
                1. "reply": A friendly conversational response to the user.
                2. "actions": An array of actions to execute.

                Supported action types:
                - "CREATE_PAGE": use "title" for the new page title and optional "content" for page content.
                - "UPDATE_PAGE": use "targetTitle" for the existing page title to find, "title" for a new title if renaming, and optional "content" to replace page content.
                - "DELETE_PAGE": use "targetTitle" for the existing page title to delete.
                - "APPEND_PAGE_BLOCK": use "targetTitle" for the page title, "blockType" as Text|Heading|Todo|Bullet|Quote|Divider, and "content" as the block text.
                - "CREATE_SUBPAGE": use "targetTitle" for parent page title, "title" for subpage title, and optional "content".
                - "CREATE_MODULE": use "moduleType" as Goal|Habit|Travel|Budget and optional "title". This creates a structured CYL module page with typed database fields for that domain.
                - "RENAME_CURRENT_PAGE": for the attached/current page only, use "title" for the new page title.
                - "APPEND_BLOCK": for the attached/current page only, use "blockType" as Text|Heading|Todo|Bullet|Quote|Divider|DatabaseTable and "content" as the block text.
                - "UPDATE_BLOCK": for the attached/current page only. Prefer exact "blockId" from the current page block outline. Use "content" for new block text, optional "blockType" to change type, and optional "isChecked" for Todo checked state.
                - "ADD_PROPERTY": for the attached/current page only, use "propertyName", "propertyType" as Text|Number|Select|MultiSelect|Status|Date|Person|FilesMedia|Checkbox|Url|Email|Phone|Formula|Relation|Rollup|Button|Place|Id, and optional "value".
                - "UPDATE_PROPERTY": for the attached/current page only, use "propertyName" and "value". Include "propertyType" if creating it when missing.
                - "DELETE_PROPERTY": for the attached/current page only, use "propertyName".
                - "DELETE_BLOCK": for the attached/current page only. Prefer exact "blockId" from the current page block outline. Also include "blockType" and "blockText" when useful. For database tables, prefer "blockId"; if no blockId is available, use "blockType":"DatabaseTable" and "tableTitle".
                - "CREATE_DATABASE": for the attached/current page only, use "tableTitle" or "title", optional "tableView" as Table|List|Board|Calendar|Gallery|Timeline|Dashboard, "tableColumns" as [{"name":"Name","type":"Text|Number|Status|Date|Checkbox|Formula|Relation|Rollup","dateFormat":"DayMonthYear|MonthDayYear|YearMonthDay","timeFormat":"Hidden|TwelveHour|TwentyFourHour","dateReminder":"None|AtTimeOfEvent|OnDayOfEvent|OneDayBefore","timezoneLabel":"Local","formula":"{Price} * {Qty}","relationTargetTableId":"target-table-block-id","rollupRelationColumnName":"Orders","rollupTargetColumnName":"Amount","rollupAggregation":"Count|Sum|Average|Min|Max"}], and "tableRows" as objects keyed by column name.
                - "ADD_TABLE_COLUMN": for the attached/current page only, use optional "blockId" or "tableTitle", "columnName", and "columnType" as Text|Number|Status|Date|Checkbox|Formula|Relation|Rollup. For Formula include "formula" using {Column Name}. For Relation include "relationTargetTableId" (preferred) or "relationTargetTableTitle". For Rollup include "rollupRelationColumnId" and "rollupTargetColumnId" when they are visible in the outline; use "rollupRelationColumnName" or "rollupTargetColumnName" only as fallback, plus "rollupAggregation" as Count|Sum|Average|Min|Max.
                - "ADD_TABLE_ROW": for the attached/current page only, use optional "blockId" or "tableTitle", optional "rowTitle", and "cellValues" as an object keyed by column name.
                - "UPDATE_TABLE_CELL": for the attached/current page only, use optional "blockId" or "tableTitle", "rowId" or "rowTitle", "columnId" or "columnName", and "value".
                - "DELETE_TABLE_COLUMN": for the attached/current page only, use optional "blockId" or "tableTitle", and "columnId" or "columnName".
                - "RENAME_TABLE_COLUMN": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and "newColumnName".
                - "UPDATE_TABLE_COLUMN_TYPE": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and "columnType" as Text|Number|Status|Date|Checkbox|Formula|Relation|Rollup. Include Formula/Relation/Rollup config fields when needed.
                - "UPDATE_TABLE_COLUMN_CONFIG": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and config fields: "formula", "relationTargetTableId" or "relationTargetTableTitle", "rollupRelationColumnId" or "rollupRelationColumnName", "rollupTargetColumnId" or "rollupTargetColumnName", "rollupAggregation". Prefer exact ids from the outline; names are only fallback.
                - "REORDER_TABLE_COLUMN": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and "targetIndex" as a 1-based destination position.
                - "DELETE_TABLE_ROW": for the attached/current page only, use optional "blockId" or "tableTitle", and "rowId" or "rowTitle".
                - "UPDATE_TABLE_ROW": for the attached/current page only, use optional "blockId" or "tableTitle", "rowId" or "rowTitle", optional "newRowTitle", and optional "cellValues".
                - "REORDER_TABLE_ROW": for the attached/current page only, use optional "blockId" or "tableTitle", "rowId" or "rowTitle", and "targetIndex" as a 1-based destination position.
                - "ADD_ROW_PAGE_BLOCK": for content inside a table row's Open page, use "blockId" as the tableBlockId, "rowId" or "rowTitle", "blockType" as Text|Heading|Todo|Bullet|Quote|Divider, and "content".
                - "UPDATE_ROW_PAGE_BLOCK": for content inside a table row's Open page, use "blockId" as the tableBlockId, "rowId" or "rowTitle", "rowBlockId", optional "blockType", optional "content", and optional "isChecked" for Todo blocks.
                - "DELETE_ROW_PAGE_BLOCK": for content inside a table row's Open page, use "blockId" as the tableBlockId, "rowId" or "rowTitle", and "rowBlockId".
                - "CHANGE_TABLE_VIEW": for the attached/current page only, use optional "blockId" or "tableTitle" and "tableView" as Table|List|Board|Calendar|Gallery|Timeline|Dashboard.
                - "SET_TABLE_VIEW_CONFIG": for the attached/current page only, use optional "blockId" or "tableTitle", and configure view-specific columns. For Calendar use "calendarDateColumnId" or "calendarDateColumnName". For Timeline use "timelineStartColumnId"/"timelineStartColumnName" and optional "timelineEndColumnId"/"timelineEndColumnName". For Dashboard/Chart use "dashboardMetricColumnId"/"dashboardMetricColumnName" and optional "dashboardGroupColumnId"/"dashboardGroupColumnName". Prefer exact ids from the outline.
                - "SORT_TABLE": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and "sortDirection" as Ascending|Descending.
                - "CLEAR_TABLE_SORT": for the attached/current page only, use optional "blockId" or "tableTitle".
                - "FILTER_TABLE": for the attached/current page only, use optional "blockId" or "tableTitle", "columnId" or "columnName", and "filterQuery" as the text/value to match.
                - "CLEAR_TABLE_FILTER": for the attached/current page only, use optional "blockId" or "tableTitle".
                - "GROUP_TABLE": for the attached/current page only, use optional "blockId" or "tableTitle", and "groupByColumnId" or "groupByColumnName".
                - "CLEAR_TABLE_GROUP": for the attached/current page only, use optional "blockId" or "tableTitle".
                Do not use standalone task/reminder actions. Task, reminder, due-date, deadline, schedule, and todo-like workflows must be represented as database tables and table rows.

                Existing pages and table data available for answering questions and page actions:
                $pageContext

                Legacy open tasks are read-only context; do not create new standalone tasks:
                $taskContext

                Search/read behavior:
                - If the user asks to search, find, list, compare, summarize, count, or answer a question about existing pages, properties, blocks, table rows, or row page content, answer directly from the Existing pages and table data above and return "actions": [].
                - Treat DatabaseTable text as searchable row/column data. Use row ids, row titles, column names, and cell values shown in the context.
                - Do not say you cannot search tables, cannot access table data, or need the user to tell you what to do with rows when matching table data is present in the supplied context.
                - If there is no matching data in the supplied context, say no matching data was found in the available CYL context and ask one concise follow-up.

                Page mention behavior:
                - If the user mentions a page with @Page Title in any chat, copy the exact page title into "targetTitle" for actions that modify that page.
                - Home/global chat can modify a mentioned page using page-level actions such as ADD_PROPERTY, UPDATE_PROPERTY, DELETE_PROPERTY, APPEND_BLOCK, CREATE_DATABASE, ADD_TABLE_ROW, UPDATE_TABLE_CELL, and DELETE_TABLE_ROW.
                - Page/current chat may omit "targetTitle" because the attached page is already the target.

                Database view behavior:
                - Default every new database to "tableView":"Table".
                - Do not create or switch to List, Board, Calendar, Gallery, Timeline, or Dashboard unless the user explicitly asks for that view.
                - If the user asks for a view using existing data, use "CHANGE_TABLE_VIEW" on the matching existing table instead of creating another table.
                - If the user asks to assign a calendar date field, timeline start/end range, chart metric, dashboard widget metric, or dashboard grouping, use "SET_TABLE_VIEW_CONFIG" on the matching existing table.

                Module behavior:
                - If the user asks for a goal module, habit module/tracker, travel planner/itinerary module, or budget/expense tracker module, use "CREATE_MODULE" with moduleType Goal|Habit|Travel|Budget.
                - Use "CREATE_DATABASE" only when the user specifically asks for a table/database inside the current page rather than a full module page.

                Task/reminder table behavior:
                - If the user asks to create a task, todo, reminder, due date, deadline, appointment, schedule, or anything with time, use a table, not a Todo block and not standalone task/reminder actions.
                - If a suitable task-like table already exists in the current/mentioned page, use "ADD_TABLE_ROW" with that table.
                - If no suitable task-like table exists in the current/mentioned page, use "CREATE_DATABASE" with tableTitle "Tasks", tableView "Table", and columns:
                  [{"name":"Task","type":"Text"},{"name":"Status","type":"Status"},{"name":"Date","type":"Date","dateFormat":"DayMonthYear","timeFormat":"TwelveHour","dateReminder":"AtTimeOfEvent","timezoneLabel":"Local"},{"name":"Notes","type":"Text"}]
                - For Date cell values with time, use this exact string format: {"startDate":"YYYY-MM-DD","startTime":"HH:mm:ss","includeTime":true,"timezoneLabel":"Local"}.
                - For Date cell values without time, use "YYYY-MM-DD". If the user gives only a relative time that cannot be converted confidently, leave Date blank and put the relative time in Notes.
                - For completion, set the Status cell to "Done". For new work, set Status to "Not started" unless the user specifies another status.

                If the user is not asking to create, update, or delete app data, return an empty actions array.
                If the user says "this page", "current page", "page ini", "sini", or mentions an @page supplied in the latest message, use the current-page action types above.
                If the user asks to update/delete/append to a page/table row but the target is unclear or not listed, do not return an action; ask a concise clarification in "reply".
                For block update/delete and table edit requests, use ids from the page outline when visible:
                - table target: use table blockId
                - column target: use columnId when visible
                - row target: use rowId when visible
                - row page block target: use blockId=tableBlockId, rowId, and rowBlockId exactly as shown in the outline
                If multiple visible blocks, columns, or rows could match and the user did not specify enough context, ask which one instead of guessing.

                Examples:
                - User: "create a page for my trip" → {"reply": "Done! I've created a page for your trip.", "actions": [{"type": "CREATE_PAGE", "title": "My Trip"}]}
                - User: "buat habit tracker module" → {"reply": "Done — I created a Habit module.", "actions": [{"type": "CREATE_MODULE", "moduleType": "Habit", "title": "Habit Tracker"}]}
                - User: "create a travel planner module for Japan" → {"reply": "Done — I created a Travel module.", "actions": [{"type": "CREATE_MODULE", "moduleType": "Travel", "title": "Japan Travel Planner"}]}
                - User: "rename Trip Plan to Japan Trip" → {"reply": "Done — I renamed the page.", "actions": [{"type": "UPDATE_PAGE", "targetTitle": "Trip Plan", "title": "Japan Trip"}]}
                - User: "add a task to Trip Plan to book hotels tomorrow 10am" → {"reply": "Done — I added that task to the table.", "actions": [{"type": "ADD_TABLE_ROW", "targetTitle": "Trip Plan", "tableTitle": "Tasks", "rowTitle": "Book hotels", "cellValues": {"Task": "Book hotels", "Status": "Not started", "Date": "{\"startDate\":\"2026-06-15\",\"startTime\":\"10:00:00\",\"includeTime\":true,\"timezoneLabel\":\"Local\"}", "Notes": ""}}]}
                - User: "create a packing subpage under Trip Plan" → {"reply": "Done — I created the subpage.", "actions": [{"type": "CREATE_SUBPAGE", "targetTitle": "Trip Plan", "title": "Packing"}]}
                - User: "in @Trip Plan add status property Planning" → {"reply": "Done — I added that property.", "actions": [{"type": "ADD_PROPERTY", "propertyName": "Status", "propertyType": "Status", "value": "Planning"}]}
                - User: "add a checklist item here to buy tickets" → {"reply": "Done — I added that item to the task table.", "actions": [{"type": "ADD_TABLE_ROW", "rowTitle": "Buy tickets", "cellValues": {"Task": "Buy tickets", "Status": "Not started", "Date": "", "Notes": ""}}]}
                - User: "rename this page to Japan Trip" → {"reply": "Done — I renamed this page.", "actions": [{"type": "RENAME_CURRENT_PAGE", "title": "Japan Trip"}]}
                - User: "delete status property" → {"reply": "Done — I deleted that property.", "actions": [{"type": "DELETE_PROPERTY", "propertyName": "Status"}]}
                - User: "change task buy tickets to buy train tickets" → {"reply": "Done — I updated that task row.", "actions": [{"type": "UPDATE_TABLE_ROW", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Buy tickets", "newRowTitle": "Buy train tickets", "cellValues": {"Task": "Buy train tickets"}}]}
                - User: "mark buy tickets as done" → {"reply": "Done — I marked that row as done.", "actions": [{"type": "UPDATE_TABLE_CELL", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Buy tickets", "columnName": "Status", "value": "Done"}]}
                - User: "delete todo buy tickets" → {"reply": "Done — I deleted that row.", "actions": [{"type": "DELETE_TABLE_ROW", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Buy tickets"}]}
                - User: "delete habit tracker table" → {"reply": "Done — I deleted that database.", "actions": [{"type": "DELETE_BLOCK", "blockId": "exact-table-block-id-from-outline", "blockType": "DatabaseTable", "tableTitle": "Habit Tracker"}]}
                - User: "buat table habit tracker dekat sini" → {"reply": "Done — I created a habit tracker database.", "actions": [{"type": "CREATE_DATABASE", "tableTitle": "Habit Tracker", "tableView": "Table", "tableColumns": [{"name":"Habit","type":"Text"},{"name":"Status","type":"Status"},{"name":"Date","type":"Date","dateFormat":"DayMonthYear","timeFormat":"Hidden","dateReminder":"OnDayOfEvent","timezoneLabel":"Local"}], "tableRows": [{"Habit":"Drink water","Status":"Not started","Date":""},{"Habit":"Exercise","Status":"Not started","Date":""}]}]}
                - User: "ubah table ini kepada board" → {"reply": "Done — I changed the database view.", "actions": [{"type": "CHANGE_TABLE_VIEW", "tableView": "Board"}]}
                - User: "tambah row Buy tickets status pending" → {"reply": "Done — I added the row.", "actions": [{"type": "ADD_TABLE_ROW", "rowTitle": "Buy tickets", "cellValues": {"Name": "Buy tickets", "Status": "Pending"}}]}
                - User: "rename status column to progress" → {"reply": "Done — I renamed that column.", "actions": [{"type": "RENAME_TABLE_COLUMN", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Status", "newColumnName": "Progress"}]}
                - User: "change done column to checkbox" → {"reply": "Done — I changed that column type.", "actions": [{"type": "UPDATE_TABLE_COLUMN_TYPE", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Done", "columnType": "Checkbox"}]}
                - User: "add total formula price times quantity" → {"reply": "Done — I added the formula column.", "actions": [{"type": "ADD_TABLE_COLUMN", "blockId": "exact-table-block-id-from-outline", "columnName": "Total", "columnType": "Formula", "formula": "{Price} * {Quantity}"}]}
                - User: "make customer column relation to contacts table" → {"reply": "Done — I configured the relation.", "actions": [{"type": "UPDATE_TABLE_COLUMN_TYPE", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Customer", "columnType": "Relation", "relationTargetTableId": "exact-target-table-block-id-from-outline"}]}
                - User: "add rollup total order amount from orders relation" → {"reply": "Done — I added the rollup.", "actions": [{"type": "ADD_TABLE_COLUMN", "blockId": "exact-table-block-id-from-outline", "columnName": "Total amount", "columnType": "Rollup", "rollupRelationColumnId": "exact-relation-column-id-from-outline", "rollupTargetColumnId": "exact-target-column-id-from-outline", "rollupAggregation": "Sum"}]}
                - User: "sort this table by due date descending" → {"reply": "Done — I sorted the table.", "actions": [{"type": "SORT_TABLE", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Due date", "sortDirection": "Descending"}]}
                - User: "filter status pending" → {"reply": "Done — I filtered the table.", "actions": [{"type": "FILTER_TABLE", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Status", "filterQuery": "Pending"}]}
                - User: "group by status" → {"reply": "Done — I grouped the table.", "actions": [{"type": "GROUP_TABLE", "blockId": "exact-table-block-id-from-outline", "groupByColumnId": "exact-column-id-from-outline", "groupByColumnName": "Status"}]}
                - User: "use due date as the calendar date field" → {"reply": "Done — I set the calendar date field.", "actions": [{"type": "SET_TABLE_VIEW_CONFIG", "blockId": "exact-table-block-id-from-outline", "calendarDateColumnId": "exact-date-column-id", "calendarDateColumnName": "Due date"}]}
                - User: "make timeline use start date and end date" → {"reply": "Done — I set the timeline range.", "actions": [{"type": "SET_TABLE_VIEW_CONFIG", "blockId": "exact-table-block-id-from-outline", "timelineStartColumnId": "exact-start-column-id", "timelineStartColumnName": "Start date", "timelineEndColumnId": "exact-end-column-id", "timelineEndColumnName": "End date"}]}
                - User: "dashboard metric should be amount grouped by status" → {"reply": "Done — I configured the dashboard.", "actions": [{"type": "SET_TABLE_VIEW_CONFIG", "blockId": "exact-table-block-id-from-outline", "dashboardMetricColumnId": "exact-amount-column-id", "dashboardMetricColumnName": "Amount", "dashboardGroupColumnId": "exact-status-column-id", "dashboardGroupColumnName": "Status"}]}
                - User: "inside exercise row add a note warm up first" → {"reply": "Done — I added content inside that row.", "actions": [{"type": "ADD_ROW_PAGE_BLOCK", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Exercise", "blockType": "Text", "content": "Warm up first"}]}
                - User: "change the note in exercise row to warm up 10 minutes" → {"reply": "Done — I updated that row content.", "actions": [{"type": "UPDATE_ROW_PAGE_BLOCK", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowBlockId": "exact-row-block-id-from-outline", "blockType": "Text", "content": "Warm up 10 minutes"}]}
                - User: "delete the note inside exercise row" → {"reply": "Done — I deleted that row content.", "actions": [{"type": "DELETE_ROW_PAGE_BLOCK", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowBlockId": "exact-row-block-id-from-outline"}]}
                - User: "delete row exercise" → {"reply": "Done — I deleted that row.", "actions": [{"type": "DELETE_TABLE_ROW", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Exercise"}]}
                - User: "move priority column to first" → {"reply": "Done — I moved that column.", "actions": [{"type": "REORDER_TABLE_COLUMN", "blockId": "exact-table-block-id-from-outline", "columnId": "exact-column-id-from-outline", "columnName": "Priority", "targetIndex": 1}]}
                - User: "complete call mom" → {"reply": "Done — I marked that row as done.", "actions": [{"type": "UPDATE_TABLE_CELL", "blockId": "exact-table-block-id-from-outline", "rowId": "exact-row-id-from-outline", "rowTitle": "Call mom", "columnName": "Status", "value": "Done"}]}
                - User: "delete the old ideas page" → {"reply": "Done — I deleted the page.", "actions": [{"type": "DELETE_PAGE", "targetTitle": "Old Ideas"}]}
                - User: "hello" → {"reply": "Hi! How can I help you today?", "actions": []}
                - User: "remind me to call mom in 30 minutes" → {"reply": "Done — I added that reminder to the task table.", "actions": [{"type": "ADD_TABLE_ROW", "rowTitle": "Call mom", "cellValues": {"Task": "Call mom", "Status": "Not started", "Date": "", "Notes": "Remind in 30 minutes"}}]}
            """.trimIndent()

            val allMessages = listOf(ChatMessage(role = "system", content = systemPrompt)) + messages
            val responseText = chatCompletionsJson(allMessages)
            val parsed = json.decodeFromString<AiActionJsonResponse>(responseText.cleanJson())
            val reply = parsed.reply.ifBlank {
                if (parsed.actions.isNotEmpty()) {
                    "Done — I handled that for you."
                } else {
                    chat(messages).ifBlank { "I received your message, but the AI returned an empty reply." }
                }
            }
            AiActionResult(reply = reply, actions = parsed.actions)
        } catch (e: Exception) {
            val fallback = chat(messages)
            AiActionResult(reply = fallback, actions = emptyList())
        }
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
            json.decodeFromString<List<String>>(response.cleanJson())
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
            chatCompletionsJson(messages).cleanJson()
        } catch (e: Exception) {
            createMockPlanJson("Error generating plan: ${e.localizedMessage}")
        }
    }

    private fun chatCompletionsJson(messages: List<ChatMessage>): String {
        val body = json.encodeToString(
            ApiRequest(
                model = activeModel,
                messages = messages.map { ApiMessage(it.role, it.content) },
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
            apiResponse.choices.firstOrNull()?.message?.content ?: throw Exception("No response choice")
        } else {
            throw Exception("HTTP Error: ${response.statusCode()} - ${response.body()}")
        }
    }

    private fun String.cleanJson(): String {
        var cleaned = this.trim()
        if (cleaned.startsWith("```json")) cleaned = cleaned.substringAfter("```json")
        else if (cleaned.startsWith("```")) cleaned = cleaned.substringAfter("```")
        if (cleaned.endsWith("```")) cleaned = cleaned.substringBeforeLast("```")
        return cleaned.trim()
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
        val role: String,
        val content: String
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
