package com.changeyourlife.cyl.backend.domain

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

    suspend fun upsertPage(userId: String, page: PageRecord): PageRecord?

    suspend fun updatePageBlockText(
        userId: String,
        pageId: String,
        blockId: String,
        text: String,
        updatedAt: Long,
    ): PageRecord?

    suspend fun updatePagePropertyValue(
        userId: String,
        pageId: String,
        propertyId: String = "",
        propertyName: String = "",
        value: String,
        updatedAt: Long,
    ): PageRecord?

    suspend fun updatePageTableCellValue(
        userId: String,
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
        updatedAt: Long,
    ): PageRecord?

    suspend fun softDeletePage(userId: String, pageId: String, deletedAt: Long): Boolean

    suspend fun restorePage(userId: String, pageId: String, restoredAt: Long): Boolean

    suspend fun deletePagePermanently(userId: String, pageId: String): Boolean
}
