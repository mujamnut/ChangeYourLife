package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.util.concurrent.ConcurrentHashMap

class InMemoryContentRepository : ContentRepository {
    private val workspacesById = ConcurrentHashMap<String, WorkspaceRecord>()
    private val pagesById = ConcurrentHashMap<String, PageRecord>()

    override suspend fun listWorkspaces(userId: String, includeDeleted: Boolean): List<WorkspaceRecord> {
        return workspacesById.values
            .asSequence()
            .filter { workspace -> workspace.userId == userId }
            .filter { workspace -> includeDeleted || workspace.deletedAt == null }
            .sortedByDescending { workspace -> workspace.updatedAt }
            .toList()
    }

    override suspend fun upsertWorkspace(workspace: WorkspaceRecord): WorkspaceRecord? {
        val existing = workspacesById[workspace.id]
        if (existing != null && existing.userId != workspace.userId) return null
        workspacesById[workspace.id] = workspace
        return workspace
    }

    override suspend fun softDeleteWorkspace(userId: String, workspaceId: String, deletedAt: Long): Boolean {
        val workspace = workspacesById[workspaceId] ?: return false
        if (workspace.userId != userId) return false
        workspacesById[workspaceId] = workspace.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        pagesById.replaceAll { _, page ->
            if (page.workspaceId == workspaceId) page.copy(deletedAt = deletedAt, updatedAt = deletedAt) else page
        }
        return true
    }

    override suspend fun listPages(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean,
    ): List<PageRecord> {
        val workspace = workspacesById[workspaceId]
        if (workspace?.userId != userId) return emptyList()
        return pagesById.values
            .asSequence()
            .filter { page -> page.workspaceId == workspaceId }
            .filter { page -> includeDeleted || page.deletedAt == null }
            .sortedWith(compareBy<PageRecord> { it.sortOrder }.thenByDescending { it.updatedAt })
            .toList()
    }

    override suspend fun getPage(userId: String, pageId: String, includeDeleted: Boolean): PageRecord? {
        val page = pagesById[pageId] ?: return null
        val workspace = workspacesById[page.workspaceId] ?: return null
        if (workspace.userId != userId) return null
        if (!includeDeleted && page.deletedAt != null) return null
        return page
    }

    override suspend fun upsertPage(userId: String, page: PageRecord): PageRecord? {
        val workspace = workspacesById[page.workspaceId] ?: return null
        if (workspace.userId != userId) return null
        val existing = pagesById[page.id]
        if (existing != null && workspacesById[existing.workspaceId]?.userId != userId) return null
        pagesById[page.id] = page
        return page
    }

    override suspend fun softDeletePage(userId: String, pageId: String, deletedAt: Long): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesById[pageId] = page.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        pagesById.replaceAll { _, existing ->
            if (existing.parentPageId == pageId) existing.copy(deletedAt = deletedAt, updatedAt = deletedAt) else existing
        }
        return true
    }

    override suspend fun restorePage(userId: String, pageId: String, restoredAt: Long): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesById[pageId] = page.copy(deletedAt = null, updatedAt = restoredAt)
        pagesById.replaceAll { _, existing ->
            if (existing.parentPageId == pageId) existing.copy(deletedAt = null, updatedAt = restoredAt) else existing
        }
        return true
    }

    override suspend fun deletePagePermanently(userId: String, pageId: String): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesById.entries.removeIf { entry -> entry.key == page.id || entry.value.parentPageId == page.id }
        return true
    }
}
