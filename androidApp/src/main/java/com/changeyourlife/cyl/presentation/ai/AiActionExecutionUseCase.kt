package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import com.changeyourlife.cyl.presentation.ai.toPageTableColumnFromAi
import javax.inject.Inject

class AiActionExecutionUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val aiPageActionExecutor: AiPageActionExecutor,
) {
    suspend fun execute(
        workspaceId: String,
        scopedTargetPage: Page?,
        actions: List<ChatAction>,
    ): AiActionExecutionResult {
        if (actions.isEmpty()) return AiActionExecutionResult()
        val globalActions = actions.filter { action -> action.isHomeScopedAction() }
        val pageActions = actions.filterNot { action -> action.isHomeScopedAction() }
        val globalResult = executeHomeScopedActions(workspaceId, globalActions)
        val pageResult = when {
            pageActions.isEmpty() -> AiActionExecutionResult()
            scopedTargetPage != null -> executePageScopedActions(scopedTargetPage, pageActions)
            else -> AiActionExecutionResult(
                validationIssues = pageActions.mapIndexed { index, _ ->
                    ChatActionValidationMetadata(
                        actionIndex = index,
                        field = "targetTitle",
                        code = "target_page_required",
                        message = "This action needs a page target. Mention a page with @ or open the page before asking AI to edit it.",
                    )
                },
            )
        }
        return globalResult + pageResult
    }

    private suspend fun executeHomeScopedActions(
        workspaceId: String,
        actions: List<ChatAction>,
    ): AiActionExecutionResult {
        if (actions.isEmpty()) return AiActionExecutionResult()
        return runCatching {
            val messages = mutableListOf<String>()
            val pageLinks = mutableListOf<AiChatPageLink>()
            actions.forEach { action ->
                when (action.type.normalizedActionType()) {
                    "CREATE_PAGE",
                    "CREATE_DATABASE",
                    "CREATE_TABLE",
                    -> {
                        val pageTitle = action.homePageTitle()
                        val created = pageRepository.createPage(
                            workspaceId = workspaceId,
                            title = pageTitle,
                            content = action.toCreatedPageContent(),
                        )
                        messages += "Done: Created page ${created.title.ifBlank { "Untitled page" }}"
                        pageLinks += created.toChatPageLink()
                    }
                }
            }
            AiActionExecutionResult(
                messages = messages,
                pageLinks = pageLinks,
            )
        }.getOrElse { error ->
            AiActionExecutionResult(
                messages = listOf(error.toAiExecutionErrorMessage()),
            )
        }
    }

    private suspend fun executePageScopedActions(
        page: Page,
        actions: List<ChatAction>,
    ): AiActionExecutionResult {
        return runCatching {
            val supportedActions = actions
                .map { action ->
                    action.copy(
                        targetTitle = action.targetTitle.ifBlank { page.title },
                    )
                }
                .filter { action -> aiPageActionExecutor.supports(action) }
            if (supportedActions.isEmpty()) {
                AiActionExecutionResult()
            } else {
                val execution = aiPageActionExecutor.executeOnPage(
                    page = page,
                    title = page.title,
                    document = PageBlockCodec.decodeDocument(page.content),
                    actions = supportedActions,
                )
                val didUpdatePage = execution.updatedTitle != null || execution.updatedDocument != null
                val updatedPage = if (didUpdatePage) {
                    page.copy(
                        title = execution.updatedTitle ?: page.title,
                        content = execution.updatedDocument?.let(PageBlockCodec::encodeDocument) ?: page.content,
                        updatedAt = System.currentTimeMillis(),
                    ).also { updatedPage -> pageRepository.upsertPage(updatedPage) }
                } else {
                    page
                }

                val pageLinks = buildList {
                    if (didUpdatePage) add(updatedPage.toChatPageLink())
                    addAll(execution.pageLinks)
                    addAll(execution.createdPages.map { createdPage -> createdPage.toChatPageLink() })
                }.distinctBy { link -> "${link.pageId}:${link.targetType}:${link.targetId}" }

                AiActionExecutionResult(
                    messages = execution.messages.ifEmpty {
                        if (didUpdatePage) {
                            listOf("Done: Updated ${updatedPage.title.ifBlank { "Untitled page" }}")
                        } else {
                            emptyList()
                        }
                    },
                    pageLinks = pageLinks,
                    validationIssues = execution.validationIssues.map { issue ->
                        ChatActionValidationMetadata(
                            actionIndex = issue.actionIndex,
                            field = issue.field,
                            code = issue.code,
                            message = issue.message,
                        )
                    },
                    undoCommands = execution.undoCommands,
                )
            }
        }.getOrElse { error ->
            AiActionExecutionResult(
                messages = listOf(error.toAiExecutionErrorMessage()),
            )
        }
    }
}

data class AiActionExecutionResult(
    val messages: List<String> = emptyList(),
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val validationIssues: List<ChatActionValidationMetadata> = emptyList(),
    val undoCommands: List<AiUndoCommandSummary> = emptyList(),
)

operator fun AiActionExecutionResult.plus(other: AiActionExecutionResult): AiActionExecutionResult {
    return AiActionExecutionResult(
        messages = messages + other.messages,
        pageLinks = (pageLinks + other.pageLinks).distinctBy { link ->
            "${link.pageId}:${link.targetType}:${link.targetId}"
        },
        validationIssues = validationIssues + other.validationIssues,
        undoCommands = undoCommands + other.undoCommands,
    )
}

private fun ChatAction.isHomeScopedAction(): Boolean {
    val actionType = type.normalizedActionType()
    return actionType == "CREATE_PAGE" ||
        (actionType in setOf("CREATE_DATABASE", "CREATE_TABLE") && targetTitle.isBlank())
}

private fun ChatAction.toCreatedPageContent(): String {
    val actionType = type.normalizedActionType()
    val moduleType = requestedModuleType(actionType)
    if (moduleType != null) return PageModuleTemplates.contentFor(moduleType)
    val blocks = buildList {
        if (tableTitle.isNotBlank() || tableColumns.isNotEmpty() || tableRows.isNotEmpty() || cellValues.isNotEmpty()) {
            add(toDatabaseBlock())
        }
        if (content.isNotBlank()) {
            add(PageBlockCodec.newBlock(PageBlockType.Text).copy(text = content.trim()))
        }
    }
    return PageBlockCodec.encodeDocument(PageBlockDocument(blocks = blocks))
}

private fun ChatAction.homePageTitle(): String {
    return title
        .ifBlank { tableTitle }
        .ifBlank { content }
        .ifBlank { "Untitled page" }
}

private fun ChatAction.requestedModuleType(actionType: String): PageModuleType? {
    val normalizedActionType = actionType.replace("_", "")
    val isModuleAction = normalizedActionType.startsWith("CREATE") &&
        (
            normalizedActionType.contains("MODULE") ||
                normalizedActionType.contains("TRACKER") ||
                normalizedActionType.contains("PLANNER")
            )
    if (isModuleAction) {
        return PageModuleTemplates.fromActionFields(
            moduleType,
            type,
            title,
            tableTitle,
            content,
            blockType,
        ) ?: error("Missing module type. Use Goal, Habit, Travel, or Budget.")
    }

    if (actionType != "CREATE_PAGE") return null
    if (moduleType.isNotBlank()) {
        return PageModuleType.from(moduleType)
    }
    val looksLikeModulePage = title.looksLikeModuleTitle() ||
        tableTitle.looksLikeModuleTitle() ||
        content.looksLikeModuleTitle()
    if (!looksLikeModulePage) return null
    return PageModuleTemplates.fromActionFields(title, tableTitle, content)
}

private fun String.looksLikeModuleTitle(): Boolean {
    val value = trim().lowercase()
    if (value.isBlank()) return false
    return value.contains("module") ||
        value.contains("tracker") ||
        value.contains("planner") ||
        value.contains("itinerary")
}

private fun ChatAction.toDatabaseBlock(): PageBlock {
    val tableName = tableTitle
        .ifBlank { title }
        .ifBlank { content }
        .ifBlank { "AI database" }
    val columns = buildTableColumns()
    val rows = buildTableRows(columns)

    return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
        table = PageTable(
            title = tableName,
            view = tableView.toPageTableView(),
            columns = columns,
            rows = rows,
        ),
    )
}

private fun ChatAction.buildTableColumns(): List<PageTableColumn> {
    val fromAction = tableColumns.mapNotNull { column ->
        val name = column.name.trim()
        if (name.isBlank()) {
            null
        } else {
            column.toPageTableColumnFromAi()
        }
    }
    if (fromAction.isNotEmpty()) return fromAction

    val keys = (tableRows.flatMap { row -> row.keys } + cellValues.keys)
        .map { key -> key.trim() }
        .filter { key -> key.isNotBlank() }
        .distinctBy { key -> key.normalizedAiKey() }
    if (keys.isNotEmpty()) {
        return keys.map { key -> PageBlockCodec.newTableColumn(key, key.inferTableColumnType()) }
    }

    return listOf(
        PageBlockCodec.newTableColumn("Name"),
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status),
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date),
    )
}

private fun ChatAction.buildTableRows(columns: List<PageTableColumn>): List<PageTableRow> {
    val rowMaps = when {
        tableRows.isNotEmpty() -> tableRows
        cellValues.isNotEmpty() -> listOf(cellValues)
        rowTitle.isNotBlank() || content.isNotBlank() -> listOf(mapOf(columns.first().name to rowTitle.ifBlank { content }))
        else -> listOf(emptyMap())
    }
    return rowMaps.map { values -> columns.newRow(values) }
}

private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
    val valuesByNormalizedName = valuesByColumnName.entries.associate { entry ->
        entry.key.normalizedAiKey() to entry.value
    }
    return PageBlockCodec.newTableRow(this).copy(
        cells = associate { column ->
            column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty()
        },
    )
}

private fun String.toPageTableColumnType(): PageTableColumnType {
    return when (normalizedAiKey()) {
        "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
        "status", "stage", "state", "phase" -> PageTableColumnType.Status
        "date", "day", "deadline", "due", "time", "calendar" -> PageTableColumnType.Date
        "file", "files", "media", "attachment", "attachments", "image", "photo", "video", "filesmedia", "filemedia" -> PageTableColumnType.FilesMedia
        "checkbox", "check", "done", "complete", "completed", "boolean" -> PageTableColumnType.Checkbox
        "formula", "calculation", "calculate", "computed" -> PageTableColumnType.Formula
        "relation", "related", "link", "linkedrow", "linkedrows" -> PageTableColumnType.Relation
        "rollup", "aggregate", "aggregation" -> PageTableColumnType.Rollup
        else -> PageTableColumnType.Text
    }
}

private fun String.inferTableColumnType(): PageTableColumnType {
    return toPageTableColumnType()
}

private fun String.toPageTableView(): PageTableView {
    return when (normalizedAiKey()) {
        "list" -> PageTableView.List
        "board", "kanban" -> PageTableView.Board
        "calendar" -> PageTableView.Calendar
        "gallery" -> PageTableView.Gallery
        "timeline" -> PageTableView.Timeline
        "dashboard", "chart", "charts" -> PageTableView.Dashboard
        else -> PageTableView.Table
    }
}

private fun String.normalizedActionType(): String =
    trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

private fun String.normalizedAiKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")
}

private fun Page.toChatPageLink(
    targetType: String = "",
    targetId: String = "",
): AiChatPageLink {
    return AiChatPageLink(
        pageId = id,
        title = title.ifBlank { "Untitled page" },
        targetType = targetType,
        targetId = targetId,
    )
}

private fun Throwable.toAiExecutionErrorMessage(): String {
    val root = generateSequence(this) { error -> error.cause }.last()
    val detail = root.localizedMessage?.takeIf { message -> message.isNotBlank() }
        ?: "AI edit failed before it could update the page. (${root.javaClass.simpleName})"
    return "Failed: $detail"
}
