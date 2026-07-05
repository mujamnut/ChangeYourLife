package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.util.concurrent.ConcurrentHashMap

class InMemoryContentRepository : ContentRepository {
    private val workspacesByKey = ConcurrentHashMap<String, WorkspaceRecord>()
    private val pagesByKey = ConcurrentHashMap<String, PageRecord>()

    override suspend fun listWorkspaces(userId: String, includeDeleted: Boolean): List<WorkspaceRecord> {
        return workspacesByKey.values
            .asSequence()
            .filter { workspace -> workspace.userId == userId }
            .filter { workspace -> includeDeleted || workspace.deletedAt == null }
            .sortedByDescending { workspace -> workspace.updatedAt }
            .toList()
    }

    override suspend fun upsertWorkspace(workspace: WorkspaceRecord): WorkspaceRecord? {
        workspacesByKey[workspace.key] = workspace
        return workspace
    }

    override suspend fun softDeleteWorkspace(userId: String, workspaceId: String, deletedAt: Long): Boolean {
        val workspace = workspacesByKey[workspaceKey(userId, workspaceId)] ?: return false
        workspacesByKey[workspace.key] = workspace.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        pagesByKey.replaceAll { key, page ->
            if (key.startsWith("$userId:") && page.workspaceId == workspaceId) {
                page.copy(deletedAt = deletedAt, updatedAt = deletedAt)
            } else {
                page
            }
        }
        return true
    }

    override suspend fun listPages(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean,
    ): List<PageRecord> {
        val workspace = workspacesByKey[workspaceKey(userId, workspaceId)] ?: return emptyList()
        if (workspace.deletedAt != null && !includeDeleted) return emptyList()
        return pagesByKey.entries
            .asSequence()
            .filter { entry -> entry.key.startsWith("$userId:") }
            .map { entry -> entry.value }
            .filter { page -> page.workspaceId == workspaceId }
            .filter { page -> includeDeleted || page.deletedAt == null }
            .sortedWith(compareBy<PageRecord> { it.sortOrder }.thenByDescending { it.updatedAt })
            .toList()
    }

    override suspend fun getPage(userId: String, pageId: String, includeDeleted: Boolean): PageRecord? {
        val page = pagesByKey[pageKey(userId, pageId)] ?: return null
        val workspace = workspacesByKey[workspaceKey(userId, page.workspaceId)] ?: return null
        if (!includeDeleted && page.deletedAt != null) return null
        if (!includeDeleted && workspace.deletedAt != null) return null
        return page
    }

    override suspend fun upsertPage(userId: String, page: PageRecord): PageRecord? {
        workspacesByKey[workspaceKey(userId, page.workspaceId)] ?: return null
        if (page.parentPageId != null) {
            val parent = pagesByKey[pageKey(userId, page.parentPageId)] ?: return null
            if (parent.workspaceId != page.workspaceId) return null
        }
        pagesByKey[pageKey(userId, page.id)] = page
        return page
    }

    override suspend fun updatePageBlockText(
        userId: String,
        pageId: String,
        blockId: String,
        text: String,
        updatedAt: Long,
    ): PageRecord? {
        return mutatePageContent(userId, pageId, updatedAt) { content ->
            PageContentJsonMutator.updateBlockText(
                content = content,
                blockId = blockId,
                text = text,
            )
        }
    }

    override suspend fun updatePagePropertyValue(
        userId: String,
        pageId: String,
        propertyId: String,
        propertyName: String,
        value: String,
        updatedAt: Long,
    ): PageRecord? {
        return mutatePageContent(userId, pageId, updatedAt) { content ->
            PageContentJsonMutator.updatePropertyValue(
                content = content,
                propertyId = propertyId,
                propertyName = propertyName,
                value = value,
            )
        }
    }

    override suspend fun updatePageTableCellValue(
        userId: String,
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
        valueJson: kotlinx.serialization.json.JsonObject?,
        updatedAt: Long,
    ): PageRecord? {
        return mutatePageContent(userId, pageId, updatedAt) { content ->
            PageContentJsonMutator.updateTableCellValue(
                content = content,
                rowId = rowId,
                columnId = columnId,
                value = value,
                valueJson = valueJson,
            )
        }
    }

    override suspend fun softDeletePage(userId: String, pageId: String, deletedAt: Long): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesByKey[pageKey(userId, pageId)] = page.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        pagesByKey.replaceAll { key, existing ->
            if (key.startsWith("$userId:") && existing.parentPageId == pageId) {
                existing.copy(deletedAt = deletedAt, updatedAt = deletedAt)
            } else {
                existing
            }
        }
        return true
    }

    override suspend fun restorePage(userId: String, pageId: String, restoredAt: Long): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesByKey[pageKey(userId, pageId)] = page.copy(deletedAt = null, updatedAt = restoredAt)
        pagesByKey.replaceAll { key, existing ->
            if (key.startsWith("$userId:") && existing.parentPageId == pageId) {
                existing.copy(deletedAt = null, updatedAt = restoredAt)
            } else {
                existing
            }
        }
        return true
    }

    override suspend fun deletePagePermanently(userId: String, pageId: String): Boolean {
        val page = getPage(userId, pageId, includeDeleted = true) ?: return false
        pagesByKey.entries.removeIf { entry ->
            entry.key.startsWith("$userId:") && (entry.value.id == page.id || entry.value.parentPageId == page.id)
        }
        return true
    }

    private suspend fun mutatePageContent(
        userId: String,
        pageId: String,
        updatedAt: Long,
        transform: (String) -> String?,
    ): PageRecord? {
        val page = getPage(userId, pageId, includeDeleted = false) ?: return null
        val updatedContent = transform(page.content) ?: return null
        val updatedPage = page.copy(content = updatedContent, updatedAt = updatedAt)
        pagesByKey[pageKey(userId, pageId)] = updatedPage
        return updatedPage
    }
}

private val WorkspaceRecord.key: String
    get() = workspaceKey(userId, id)

private fun workspaceKey(userId: String, workspaceId: String): String = "$userId:$workspaceId"

private fun pageKey(userId: String, pageId: String): String = "$userId:$pageId"
