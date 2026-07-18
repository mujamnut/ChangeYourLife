package com.changeyourlife.cyl.backend.domain

import kotlinx.serialization.json.JsonObject

data class WorkspaceRecord(
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class PageRecord(
    val id: String,
    val workspaceId: String,
    val parentPageId: String?,
    val title: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val revision: Long = 0L,
)

sealed interface PageMutationResult {
    data class Applied(val page: PageRecord) : PageMutationResult

    data class Conflict(
        val expectedRevision: Long,
        val currentPage: PageRecord,
    ) : PageMutationResult

    data object NotFound : PageMutationResult

    data object Forbidden : PageMutationResult

    data object Rejected : PageMutationResult

    data object PermanentlyDeleted : PageMutationResult
}

data class ContentSearchQuery(
    val workspaceId: String,
    val query: String,
    val scopes: Set<String>,
    val limit: Int,
)

data class ContentSearchResult(
    val targetType: String,
    val workspaceId: String,
    val pageId: String = "",
    val blockId: String = "",
    val tableBlockId: String = "",
    val rowId: String = "",
    val columnId: String = "",
    val propertyId: String = "",
    val chatSessionId: String = "",
    val chatMessageId: String = "",
    val title: String,
    val subtitle: String = "",
    val snippet: String = "",
    val score: Int = 0,
    val updatedAt: Long = 0L,
)

interface ContentRepository {
    suspend fun listWorkspaces(userId: String, includeDeleted: Boolean = false): List<WorkspaceRecord>

    suspend fun upsertWorkspace(workspace: WorkspaceRecord): WorkspaceRecord?

    suspend fun softDeleteWorkspace(userId: String, workspaceId: String, deletedAt: Long): Boolean

    suspend fun listPages(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean = false,
    ): List<PageRecord>

    suspend fun getPage(userId: String, pageId: String, includeDeleted: Boolean = false): PageRecord?

    suspend fun search(userId: String, query: ContentSearchQuery): List<ContentSearchResult>

    suspend fun upsertPage(userId: String, page: PageRecord): PageMutationResult

    suspend fun mutatePageContent(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        updatedAt: Long,
        transform: (String) -> String?,
    ): PageMutationResult

    suspend fun updatePageBlockText(
        userId: String,
        pageId: String,
        blockId: String,
        text: String,
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult

    suspend fun updatePagePropertyValue(
        userId: String,
        pageId: String,
        propertyId: String = "",
        propertyName: String = "",
        value: String,
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult

    suspend fun updatePageTableCellValue(
        userId: String,
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
        valueJson: JsonObject?,
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult

    suspend fun softDeletePage(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        deletedAt: Long,
    ): PageMutationResult

    suspend fun restorePage(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        restoredAt: Long,
    ): PageMutationResult

    suspend fun deletePagePermanently(
        userId: String,
        pageId: String,
        expectedRevision: Long,
    ): PageMutationResult
}
