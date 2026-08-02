package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.aicontract.AiActionWire
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPendingActionMetadata
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.toAiMonthReferenceOrNull
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatActionResult
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun List<ChatMessage>.latestPendingAiActions(): List<ChatPendingActionMetadata> {
    val lastMessage = lastOrNull() ?: return emptyList()
    if (!lastMessage.role.equals("assistant", ignoreCase = true)) return emptyList()
    return lastMessage.actionMetadata?.pendingActions.orEmpty()
}

internal enum class AiPendingDestructiveDecision {
    None,
    Confirm,
    Cancel,
}

internal fun List<ChatPendingActionMetadata>.destructiveDecision(
    userPrompt: String,
): AiPendingDestructiveDecision {
    if (none(ChatPendingActionMetadata::requiresDestructiveConfirmation)) {
        return AiPendingDestructiveDecision.None
    }
    return when {
        userPrompt.explicitlyConfirmsDestructiveActions() -> AiPendingDestructiveDecision.Confirm
        userPrompt.cancelsPendingAction() -> AiPendingDestructiveDecision.Cancel
        else -> AiPendingDestructiveDecision.None
    }
}

internal fun List<ChatPendingActionMetadata>.toConfirmedDestructiveActionResult(): ChatActionResult {
    return ChatActionResult(
        reply = "Confirmed. Applying the saved action plan.",
        actions = map { pending -> pending.action.toChatAction() },
    )
}

internal fun cancelledDestructiveActionResult(): ChatActionResult {
    return ChatActionResult(
        reply = "Cancelled. No changes were made.",
        actions = emptyList(),
    )
}

internal fun ChatPendingActionMetadata.requiresDestructiveConfirmation(): Boolean {
    return issueCodes.any { code ->
        code.equals(DestructiveConfirmationRequiredCode, ignoreCase = true)
    }
}

internal fun List<ChatPendingActionMetadata>.toPendingClarificationContext(): String {
    if (isEmpty()) return ""
    if (all(ChatPendingActionMetadata::requiresDestructiveConfirmation)) {
        return buildString {
            appendLine("CYL_PENDING_DESTRUCTIVE_CONFIRMATION:")
            appendLine("The exact action plan below is suspended and must not execute until the user explicitly confirms it.")
            appendLine("Do not alter, regenerate, or broaden this saved plan.")
            this@toPendingClarificationContext.forEachIndexed { index, pending ->
                appendLine("Pending action ${index + 1}: ${PendingActionJson.encodeToString(pending.action)}")
            }
        }.trim()
    }
    return buildString {
        appendLine("CYL_PENDING_CLARIFICATION:")
        appendLine("The previous edit is suspended, not discarded.")
        appendLine("Use the user's latest message to repair the exact pending action below.")
        appendLine("Preserve its action type and hidden IDs unless the user explicitly changes the request.")
        appendLine("Do not repeat a clarification question when the latest message resolves the listed field.")
        this@toPendingClarificationContext.forEachIndexed { index, pending ->
            appendLine("Pending ${index + 1}:")
            appendLine("issueFields=${pending.issueFields.joinToString(",")}")
            appendLine("issueCodes=${pending.issueCodes.joinToString(",")}")
            appendLine("action=${PendingActionJson.encodeToString(pending.action)}")
        }
    }.trim()
}

internal fun ChatActionResult.resolvePendingClarification(
    pendingActions: List<ChatPendingActionMetadata>,
    userPrompt: String,
    pages: List<Page>,
    scopedTargetPage: Page?,
): ChatActionResult {
    if (pendingActions.isEmpty() || userPrompt.isBlank() || userPrompt.cancelsPendingAction()) {
        return this
    }

    if (actions.isNotEmpty()) {
        return copy(
            actions = actions.map { action ->
                val pending = pendingActions.singleOrNull { candidate ->
                    candidate.action.type.equals(action.type, ignoreCase = true)
                } ?: return@map action
                action.withPendingIdentity(pending.action.toChatAction())
            },
        )
    }

    val pending = pendingActions.singleOrNull() ?: return this
    val resolved = pending.resolveLocally(
        userPrompt = userPrompt,
        pages = pages,
        scopedTargetPage = scopedTargetPage,
    ) ?: return this
    return copy(
        reply = "Baik, saya teruskan perubahan itu.",
        actions = listOf(resolved),
        validationIssues = emptyList(),
    )
}

private fun ChatPendingActionMetadata.resolveLocally(
    userPrompt: String,
    pages: List<Page>,
    scopedTargetPage: Page?,
): ChatAction? {
    var payload = action
    var changed = false
    val issueFieldKeys = issueFields.map(String::clarificationKey).toSet()
    val clarifiedPage = pages.uniquePageMatching(userPrompt) ?: scopedTargetPage

    if ("targettitle" in issueFieldKeys) {
        clarifiedPage?.let { page ->
            payload = payload.copy(targetTitle = page.title)
            changed = true
        }
    }
    if (issueFieldKeys.any { field -> field in SourcePageClarificationFields }) {
        clarifiedPage?.let { page ->
            payload = payload.copy(
                sourcePageId = page.id,
                sourcePageTitle = page.title,
            )
            changed = true
        }
    }
    if (issueFieldKeys.any { field -> field in ParentPageClarificationFields }) {
        clarifiedPage?.let { page ->
            payload = payload.copy(
                parentPageId = page.id,
                parentPageTitle = page.title,
            )
            changed = true
        }
    }

    val targetPage = pages.exactPage(payload.targetTitle) ?: scopedTargetPage
    val document = targetPage?.let { page -> PageBlockCodec.decodeDocument(page.content) }
    val tables = document?.blocks.orEmpty().collectClarificationTables()
    val table = tables.resolveClarificationTable(payload)

    if (table != null && issueFieldKeys.any { field -> field in ColumnClarificationFields }) {
        table.table.uniqueColumnMatching(userPrompt)?.let { column ->
            payload = payload.copy(columnId = column.id, columnName = column.name)
            changed = true
        }
    }

    if (table != null && issueFieldKeys.any { field -> field in RowClarificationFields }) {
        val rowQuery = if (userPrompt.requestsAllMatches()) {
            payload.rowTitle.ifBlank { payload.filterQuery }.ifBlank { userPrompt }
        } else {
            userPrompt
        }
        val matchingRows = table.table.rowsMatchingClarification(rowQuery)
        when {
            matchingRows.size == 1 -> {
                val row = matchingRows.single()
                payload = payload.copy(
                    rowId = row.id,
                    rowTitle = row.primaryTitle(table.table),
                )
                changed = true
            }

            matchingRows.size > 1 && userPrompt.requestsAllMatches() -> {
                payload.toBulkActionForAll(table.table)?.let { bulkPayload ->
                    payload = bulkPayload
                    changed = true
                }
            }
        }
    }

    if ("filterquery" in issueFieldKeys && !userPrompt.requestsAllMatches()) {
        payload = payload.copy(filterQuery = userPrompt.trim())
        changed = true
    }

    if (!changed) return null
    val parsed = AiActionContractSchema.parse(actionIndex = null, payload = payload)
    if (!parsed.isValid) return null
    return parsed.normalizedPayload.toChatAction()
}

private fun AiActionWire.toBulkActionForAll(table: PageTable): AiActionWire? {
    val query = rowTitle.ifBlank { filterQuery }.ifBlank { value }.ifBlank { content }.trim()
    if (query.isBlank()) return null
    val filterColumns = table.columns.filter { column ->
        table.rows.count { row -> row.cells[column.id].orEmpty().matchesClarificationValue(query) } > 1
    }
    val filterColumn = when {
        columnId.isNotBlank() -> filterColumns.singleOrNull { column -> column.id == columnId }
        columnName.isNotBlank() -> filterColumns.singleOrNull { column ->
            column.name.equals(columnName, ignoreCase = true)
        }
        else -> filterColumns.singleOrNull()
    } ?: return null

    return when (type.trim().uppercase(Locale.ROOT)) {
        "CLEAR_TABLE_CELL" -> copy(
            type = "CLEAR_TABLE_CELLS",
            rowId = "",
            rowIds = emptyList(),
            rowTitle = "",
            columnId = filterColumn.id,
            columnName = filterColumn.name,
            filterQuery = query,
            value = "",
            content = "",
            cellValues = emptyMap(),
        )

        "DELETE_TABLE_ROW" -> copy(
            type = "DELETE_TABLE_ROWS",
            rowId = "",
            rowIds = emptyList(),
            rowTitle = "",
            columnId = filterColumn.id,
            columnName = filterColumn.name,
            filterQuery = query,
            value = "",
            content = "",
        )

        "UPDATE_TABLE_ROW" -> copy(
            type = "UPDATE_TABLE_ROWS",
            rowId = "",
            rowIds = emptyList(),
            rowTitle = "",
            columnId = filterColumn.id,
            columnName = filterColumn.name,
            filterQuery = query,
        )

        "UPDATE_TABLE_CELL" -> {
            val targetColumn = table.findClarificationColumn(columnId, columnName) ?: return null
            val updateValue = value.ifBlank { content }
            if (updateValue.isBlank()) return null
            copy(
                type = "UPDATE_TABLE_ROWS",
                rowId = "",
                rowIds = emptyList(),
                rowTitle = "",
                columnId = filterColumn.id,
                columnName = filterColumn.name,
                filterQuery = query,
                value = "",
                content = "",
                cellValues = mapOf(targetColumn.id to updateValue),
            )
        }

        else -> null
    }
}

private fun ChatAction.withPendingIdentity(pending: ChatAction): ChatAction {
    return copy(
        targetTitle = targetTitle.ifBlank { pending.targetTitle },
        blockId = blockId.ifBlank { pending.blockId },
        blockText = blockText.ifBlank { pending.blockText },
        propertyName = propertyName.ifBlank { pending.propertyName },
        parentPageId = parentPageId.ifBlank { pending.parentPageId },
        parentPageTitle = parentPageTitle.ifBlank { pending.parentPageTitle },
        sourcePageId = sourcePageId.ifBlank { pending.sourcePageId },
        sourcePageTitle = sourcePageTitle.ifBlank { pending.sourcePageTitle },
        sourceTableBlockId = sourceTableBlockId.ifBlank { pending.sourceTableBlockId },
        sourceTableTitle = sourceTableTitle.ifBlank { pending.sourceTableTitle },
        tableTitle = tableTitle.ifBlank { pending.tableTitle },
        columnId = columnId.ifBlank { pending.columnId },
        columnName = columnName.ifBlank { pending.columnName },
        rowId = rowId.ifBlank { pending.rowId },
        rowIds = rowIds.ifEmpty { pending.rowIds },
        rowTitle = rowTitle.ifBlank { pending.rowTitle },
        rowBlockId = rowBlockId.ifBlank { pending.rowBlockId },
        filterQuery = filterQuery.ifBlank { pending.filterQuery },
        cellValues = cellValues.ifEmpty { pending.cellValues },
        targetIndex = targetIndex ?: pending.targetIndex,
    )
}

private fun List<Page>.exactPage(title: String): Page? {
    val target = title.trim().removePrefix("@").trim()
    if (target.isBlank()) return null
    return filter { page -> page.title.equals(target, ignoreCase = true) }.singleOrNull()
}

private fun List<Page>.uniquePageMatching(reply: String): Page? {
    val target = reply.cleanClarificationReply()
    return filter { page -> page.title.clarificationKey() == target.clarificationKey() }.singleOrNull()
}

private fun List<PageBlock>.collectClarificationTables(): List<PageBlock> = buildList {
    fun collect(blocks: List<PageBlock>) {
        blocks.forEach { block ->
            if (block.type == PageBlockType.DatabaseTable || block.type == PageBlockType.Table) {
                add(block)
            }
            collect(block.children)
        }
    }
    collect(this@collectClarificationTables)
}

private fun List<PageBlock>.resolveClarificationTable(payload: AiActionWire): PageBlock? {
    if (payload.blockId.isNotBlank()) {
        firstOrNull { table -> table.id == payload.blockId }?.let { return it }
    }
    val title = payload.tableTitle.trim()
    if (title.isNotBlank()) {
        filter { table -> table.table.title.equals(title, ignoreCase = true) }
            .singleOrNull()
            ?.let { return it }
    }
    return singleOrNull()
}

private fun PageTable.uniqueColumnMatching(reply: String): PageTableColumn? {
    val target = reply.cleanClarificationReply().clarificationKey()
    if (target.isBlank()) return null
    return columns.filter { column -> column.name.clarificationKey() == target }.singleOrNull()
}

private fun PageTable.findClarificationColumn(
    columnId: String,
    columnName: String,
): PageTableColumn? {
    if (columnId.isNotBlank()) {
        columns.firstOrNull { column -> column.id == columnId }?.let { return it }
    }
    return columns.firstOrNull { column -> column.name.equals(columnName, ignoreCase = true) }
}

private fun PageTable.rowsMatchingClarification(reply: String): List<PageTableRow> {
    val query = reply.cleanClarificationReply()
    if (query.isBlank()) return emptyList()
    return rows.filter { row ->
        row.cells.values.any { value -> value.matchesClarificationValue(query) }
    }
}

private fun PageTableRow.primaryTitle(table: PageTable): String {
    return table.columns.firstOrNull()
        ?.let { column -> cells[column.id].orEmpty().trim() }
        .orEmpty()
}

private fun String.matchesClarificationValue(query: String): Boolean {
    if (clarificationKey() == query.clarificationKey()) return true
    val currentMonth = toAiMonthReferenceOrNull()
    val queryMonth = query.toAiMonthReferenceOrNull()
    return currentMonth != null && queryMonth != null && currentMonth.matches(queryMonth)
}

private val SourcePageClarificationFields = setOf("sourcepageid", "sourcepagetitle")
private val ParentPageClarificationFields = setOf("parentpageid", "parentpagetitle")

private fun String.cleanClarificationReply(): String {
    return trim()
        .removePrefix("@")
        .replace(
            Regex(
                """^(?:row|baris|column|kolum|lajur|page|halaman)\s+(?:itu\s+|yang\s+)?""",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .trim()
}

private fun String.clarificationKey(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

private fun String.requestsAllMatches(): Boolean {
    val words = clarificationKey().split(' ').filter(String::isNotBlank).toSet()
    return words.any { word -> word in AllMatchWords }
}

private fun String.cancelsPendingAction(): Boolean {
    val words = clarificationKey().split(' ').filter(String::isNotBlank).toSet()
    return words.any { word -> word in CancelWords }
}

private fun String.explicitlyConfirmsDestructiveActions(): Boolean {
    val normalized = clarificationKey()
    return normalized in ExplicitConfirmationPhrases
}

private val ColumnClarificationFields = setOf("columnid", "columnname", "propertyname")
private val RowClarificationFields = setOf("rowid", "rowtitle")
private val AllMatchWords = setOf("all", "semua", "kesemua", "seluruh")
private val CancelWords = setOf("batal", "batalkan", "cancel", "nevermind", "stop")
private val ExplicitConfirmationPhrases = setOf(
    "confirm",
    "confirm changes",
    "confirm destructive changes",
    "confirm deletion",
    "sahkan",
    "sahkan perubahan",
    "sahkan pemadaman",
    "ya padam",
    "yes delete",
    "proceed with deletion",
    "teruskan pemadaman",
)

private val PendingActionJson = Json {
    encodeDefaults = true
}
