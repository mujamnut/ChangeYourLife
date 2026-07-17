package com.changeyourlife.cyl.data.search

import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.SearchIndexDao
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.data.local.mapper.toDocument
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.model.normalizedSearchText
import com.changeyourlife.cyl.domain.model.displayValue as domainDisplayValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexRebuilder @Inject constructor(
    private val searchIndexDao: SearchIndexDao,
    private val pageDao: PageDao,
    private val pageContentDao: PageContentDao,
    private val chatMessageDao: ChatMessageDao,
) : ChatSearchIndexUpdater {
    suspend fun ensureWorkspaceIndexed(workspaceId: String) {
        if (workspaceId.isBlank()) return
        if (searchIndexDao.countForWorkspace(workspaceId) == 0) {
            rebuildWorkspace(workspaceId)
            return
        }
        ensureChatScopeIndexed(homeChatScopeId(workspaceId))
    }

    suspend fun rebuildWorkspace(workspaceId: String) {
        pageDao.getPagesForWorkspaceIncludingDeleted(workspaceId)
            .forEach { page -> rebuildPage(page) }
        rebuildChatScope(homeChatScopeId(workspaceId))
    }

    suspend fun rebuildPage(page: PageEntity) {
        rebuildPage(
            page = page,
            document = page.toSearchDocument(),
        )
    }

    suspend fun rebuildPage(
        page: PageEntity,
        document: PageBlockDocument,
    ) {
        val entries = buildPageEntries(page, document)
        searchIndexDao.replacePageEntries(pageId = page.id, entries = entries)
    }

    suspend fun markPageTreeDeleted(pageId: String, deletedAt: Long) {
        searchIndexDao.markPageTreeDeleted(pageId = pageId, deletedAt = deletedAt)
    }

    suspend fun markPageTreeRestored(pageId: String, restoredAt: Long) {
        searchIndexDao.markPageTreeRestored(pageId = pageId, restoredAt = restoredAt)
    }

    suspend fun deletePageTree(pageId: String) {
        searchIndexDao.deleteForPageTree(pageId)
    }

    override suspend fun rebuildChatScope(scopeId: String) {
        chatMessageDao.getSessionsForScopeIncludingDeleted(scopeId)
            .forEach { session -> rebuildChatSession(session) }
    }

    override suspend fun rebuildChatSession(sessionId: String) {
        val session = chatMessageDao.getSessionIncludingDeleted(sessionId)
        if (session == null) {
            searchIndexDao.deleteForChatSession(sessionId)
            return
        }
        rebuildChatSession(session)
    }

    suspend fun rebuildChatSession(session: ChatSessionEntity) {
        val entries = buildChatEntries(session)
        searchIndexDao.replaceChatSessionEntries(sessionId = session.id, entries = entries)
    }

    suspend fun deleteChatSession(sessionId: String) {
        searchIndexDao.deleteForChatSession(sessionId)
    }

    private suspend fun ensureChatScopeIndexed(scopeId: String) {
        val workspaceId = workspaceIdFromChatScope(scopeId)
        if (workspaceId.isBlank()) return
        val hasChatIndex = searchIndexDao.countForWorkspaceAndType(
            workspaceId = workspaceId,
            targetType = SearchTargetType.Chat.name,
        ) > 0
        if (!hasChatIndex && chatMessageDao.getSessionsForScopeIncludingDeleted(scopeId).isNotEmpty()) {
            rebuildChatScope(scopeId)
        }
    }

    private fun buildPageEntries(
        page: PageEntity,
        document: PageBlockDocument,
    ): List<SearchIndexEntity> {
        val entries = mutableListOf<SearchIndexEntity>()
        val pageTitle = page.title.ifBlank { "Untitled page" }
        val pageSnippet = document.blocks
            .asSequence()
            .flatMap { block -> block.searchTexts().asSequence() }
            .firstOrNull { text -> text.isNotBlank() }
            .orEmpty()
        entries += page.searchEntry(
            type = SearchTargetType.Page,
            title = pageTitle,
            subtitle = "Page",
            snippet = pageSnippet,
        )

        document.properties.forEach { property ->
            entries += page.propertyEntry(property)
        }
        document.blocks.forEach { block ->
            entries += block.toSearchEntries(page = page, pageTitle = pageTitle)
        }
        return entries.distinctBy(SearchIndexEntity::id)
    }

    private suspend fun buildChatEntries(session: ChatSessionEntity): List<SearchIndexEntity> {
        val workspaceId = workspaceIdFromChatScope(session.scopeId)
        if (workspaceId.isBlank()) return emptyList()

        val messages = chatMessageDao.getMessagesForSession(session.id)
        val sessionTitle = session.title.cleanSearchSnippet().ifBlank { "New chat" }
        val latestMessage = messages.maxByOrNull { message -> message.updatedAt.coerceAtLeast(message.createdAt) }
        val entries = mutableListOf<SearchIndexEntity>()
        entries += chatSearchEntry(
            id = "${SearchTargetType.Chat.name}:${session.id}",
            workspaceId = workspaceId,
            session = session,
            title = sessionTitle,
            subtitle = "Chat",
            snippet = latestMessage?.toChatSnippet().orEmpty(),
            updatedAt = maxOf(session.updatedAt, latestMessage?.updatedAt ?: session.updatedAt),
        )
        messages.forEach { message ->
            val snippet = message.toChatSnippet()
            if (snippet.isBlank()) return@forEach
            entries += chatSearchEntry(
                id = "${SearchTargetType.Chat.name}:${session.id}:${message.id}",
                workspaceId = workspaceId,
                session = session,
                message = message,
                title = sessionTitle,
                subtitle = message.role.toChatRoleLabel(),
                snippet = snippet,
                updatedAt = message.updatedAt.coerceAtLeast(message.createdAt),
            )
        }
        return entries.distinctBy(SearchIndexEntity::id)
    }

    private suspend fun PageEntity.toSearchDocument(): PageBlockDocument {
        val snapshot = pageContentDao.getPageContentSnapshot(id)
        val hasProjection = snapshot.blocks.isNotEmpty() ||
            snapshot.properties.isNotEmpty() ||
            snapshot.tables.isNotEmpty()
        return if (hasProjection) {
            snapshot.toDocument()
        } else {
            PageContentCodec.decodeDocument(content)
        }
    }

    private fun PageBlock.toSearchEntries(
        page: PageEntity,
        pageTitle: String,
        parentRowId: String = "",
    ): List<SearchIndexEntity> {
        val entries = mutableListOf<SearchIndexEntity>()
        val blockText = searchTexts().joinToString(separator = " ").cleanSearchSnippet()
        if (blockText.isNotBlank() && type != PageBlockType.DatabaseTable && type != PageBlockType.Table) {
            entries += page.searchEntry(
                type = SearchTargetType.Block,
                title = blockTitle(),
                subtitle = pageTitle,
                snippet = blockText,
                blockId = id,
                rowId = parentRowId,
            )
        }

        if (type == PageBlockType.DatabaseTable || type == PageBlockType.Table) {
            entries += page.tableEntries(
                blockId = id,
                table = table,
                pageTitle = pageTitle,
            )
        }

        children.forEach { child ->
            entries += child.toSearchEntries(
                page = page,
                pageTitle = pageTitle,
                parentRowId = parentRowId,
            )
        }
        return entries
    }

    private fun PageEntity.tableEntries(
        blockId: String,
        table: PageTable,
        pageTitle: String,
    ): List<SearchIndexEntity> {
        val entries = mutableListOf<SearchIndexEntity>()
        val tableTitle = table.title.ifBlank { pageTitle }
        val visibleColumns = table.columns.filterNot { column -> column.config.isHidden }
        entries += searchEntry(
            type = SearchTargetType.Table,
            title = tableTitle.ifBlank { "Table" },
            subtitle = pageTitle,
            snippet = visibleColumns.joinToString(separator = ", ") { column -> column.name },
            blockId = blockId,
            tableBlockId = blockId,
        )

        visibleColumns.forEach { column ->
            entries += searchEntry(
                type = SearchTargetType.Column,
                title = column.name.ifBlank { column.type.name },
                subtitle = tableTitle,
                snippet = column.type.name,
                blockId = blockId,
                tableBlockId = blockId,
                columnId = column.id,
            )
        }

        table.rows.forEach { row ->
            entries += rowEntries(
                tableBlockId = blockId,
                table = table,
                visibleColumns = visibleColumns,
                row = row,
                tableTitle = tableTitle,
            )
        }
        return entries
    }

    private fun PageEntity.rowEntries(
        tableBlockId: String,
        table: PageTable,
        visibleColumns: List<PageTableColumn>,
        row: PageTableRow,
        tableTitle: String,
    ): List<SearchIndexEntity> {
        val entries = mutableListOf<SearchIndexEntity>()
        val rowTitle = row.title(visibleColumns).ifBlank { "Untitled row" }
        val cellText = visibleColumns
            .mapNotNull { column -> row.displayValue(column).takeIf(String::isNotBlank) }
        val rowBlockText = row.blocks
            .flatMap { block -> block.searchTexts() }
            .filter(String::isNotBlank)
        entries += searchEntry(
            type = SearchTargetType.Row,
            title = rowTitle,
            subtitle = tableTitle,
            snippet = (cellText + rowBlockText).joinToString(separator = " ").cleanSearchSnippet(),
            blockId = tableBlockId,
            tableBlockId = tableBlockId,
            rowId = row.id,
        )

        visibleColumns.forEach { column ->
            val value = row.displayValue(column)
            if (value.isNotBlank()) {
                entries += searchEntry(
                    type = SearchTargetType.Cell,
                    title = rowTitle,
                    subtitle = column.name.ifBlank { table.title.ifBlank { "Table" } },
                    snippet = value.cleanSearchSnippet(),
                    blockId = tableBlockId,
                    tableBlockId = tableBlockId,
                    rowId = row.id,
                    columnId = column.id,
                )
            }
        }

        row.blocks.forEach { block ->
            entries += block.toSearchEntries(
                page = this,
                pageTitle = "$tableTitle / $rowTitle",
                parentRowId = row.id,
            )
        }
        return entries
    }

    private fun PageEntity.propertyEntry(property: PageProperty): SearchIndexEntity =
        searchEntry(
            type = SearchTargetType.Property,
            title = property.name.ifBlank { property.type.name },
            subtitle = title.ifBlank { "Untitled page" },
            snippet = property.value.cleanSearchSnippet(),
            propertyId = property.id,
        )

    private fun PageEntity.searchEntry(
        type: SearchTargetType,
        title: String,
        subtitle: String = "",
        snippet: String = "",
        blockId: String = "",
        tableBlockId: String = "",
        rowId: String = "",
        columnId: String = "",
        propertyId: String = "",
    ): SearchIndexEntity {
        val safeTitle = title.cleanSearchSnippet().ifBlank { "Untitled" }
        val safeSubtitle = subtitle.cleanSearchSnippet()
        val safeSnippet = snippet.cleanSearchSnippet()
        val normalizedText = listOf(safeTitle, safeSubtitle, safeSnippet)
            .joinToString(separator = " ")
            .normalizedSearchText()
        return SearchIndexEntity(
            id = listOf(
                type.name,
                id,
                blockId,
                tableBlockId,
                rowId,
                columnId,
                propertyId,
            ).joinToString(separator = ":"),
            workspaceId = workspaceId,
            targetType = type.name,
            pageId = id,
            blockId = blockId,
            tableBlockId = tableBlockId,
            rowId = rowId,
            columnId = columnId,
            propertyId = propertyId,
            title = safeTitle,
            subtitle = safeSubtitle,
            snippet = safeSnippet,
            normalizedText = normalizedText,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )
    }

    private fun chatSearchEntry(
        id: String,
        workspaceId: String,
        session: ChatSessionEntity,
        message: ChatMessageEntity? = null,
        title: String,
        subtitle: String,
        snippet: String,
        updatedAt: Long,
    ): SearchIndexEntity {
        val safeTitle = title.cleanSearchSnippet().ifBlank { "New chat" }
        val safeSubtitle = subtitle.cleanSearchSnippet()
        val safeSnippet = snippet.cleanSearchSnippet()
        val normalizedText = listOf(safeTitle, safeSubtitle, safeSnippet)
            .joinToString(separator = " ")
            .normalizedSearchText()
        return SearchIndexEntity(
            id = id,
            workspaceId = workspaceId,
            targetType = SearchTargetType.Chat.name,
            chatSessionId = session.id,
            chatMessageId = message?.id.orEmpty(),
            title = safeTitle,
            subtitle = safeSubtitle,
            snippet = safeSnippet,
            normalizedText = normalizedText,
            updatedAt = updatedAt,
            deletedAt = session.deletedAt,
        )
    }

    private fun PageBlock.blockTitle(): String {
        val textTitle = text.cleanSearchSnippet()
        if (textTitle.isNotBlank()) return textTitle.take(MaxTitleChars)
        return when (type) {
            PageBlockType.Heading -> "Heading"
            PageBlockType.Todo -> "To-do"
            PageBlockType.Bullet -> "Bullet"
            PageBlockType.Numbered -> "Numbered"
            PageBlockType.Quote -> "Quote"
            PageBlockType.Code -> "Code"
            PageBlockType.MediaFile -> mediaAttachments.firstOrNull()?.name.orEmpty().ifBlank { "File" }
            else -> type.name
        }
    }

    private fun PageBlock.searchTexts(): List<String> = buildList {
        if (text.isNotBlank()) add(text)
        if (isChecked) add("checked done complete")
        mediaAttachments.map(PageMediaAttachment::name).filter(String::isNotBlank).forEach(::add)
        if (type == PageBlockType.DatabaseTable || type == PageBlockType.Table) {
            table.title.takeIf(String::isNotBlank)?.let(::add)
            table.columns.map(PageTableColumn::name).filter(String::isNotBlank).forEach(::add)
        }
    }

    private fun PageTableRow.title(columns: List<PageTableColumn>): String {
        val firstColumn = columns.firstOrNull() ?: return ""
        return displayValue(firstColumn)
    }

    private fun PageTableRow.displayValue(column: PageTableColumn): String {
        val typed = cellValues[column.id]
        return typed?.searchDisplayValue(fallback = cells[column.id].orEmpty())
            ?: cells[column.id].orEmpty()
    }

    private fun PageTableCellValue.searchDisplayValue(fallback: String): String {
        return when (type) {
            PageTableColumnType.FilesMedia -> files.joinToString(separator = ", ") { file -> file.name }
            PageTableColumnType.Checkbox -> if (checked) "Checked" else ""
            else -> domainDisplayValue(fallback = fallback)
        }
    }

    private fun String.cleanSearchSnippet(): String =
        replace(Regex("\\s+"), " ")
            .replace('\u0000', ' ')
            .trim()
            .let { value -> if (value.length <= MaxSnippetChars) value else value.take(MaxSnippetChars - 1).trimEnd() + "..." }

    private fun ChatMessageEntity.toChatSnippet(): String {
        val text = content.cleanSearchSnippet()
        if (text.isNotBlank()) return text
        return when {
            attachmentsJson.isNotBlank() && attachmentsJson != "[]" -> "Attachment"
            pageLinksJson.isNotBlank() && pageLinksJson != "[]" -> "Page link"
            actionMetadataJson.isNotBlank() -> "AI action"
            else -> ""
        }
    }

    private fun String.toChatRoleLabel(): String =
        when {
            equals("user", ignoreCase = true) -> "You"
            equals("assistant", ignoreCase = true) -> "CYL"
            else -> "Chat"
        }

    private fun homeChatScopeId(workspaceId: String): String = "$HomeChatScopePrefix$workspaceId"

    private fun workspaceIdFromChatScope(scopeId: String): String =
        scopeId.removePrefix(HomeChatScopePrefix).trim()

    private companion object {
        private const val HomeChatScopePrefix = "home:"
        private const val MaxTitleChars = 80
        private const val MaxSnippetChars = 240
    }
}
