package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.ContentSearchQuery
import com.changeyourlife.cyl.backend.domain.ContentSearchResult
import com.changeyourlife.cyl.backend.domain.PageMutationResult
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

    override suspend fun softDeleteWorkspace(
        userId: String,
        workspaceId: String,
        deletedAt: Long,
    ): Boolean = synchronized(pagesByKey) {
        val workspace = workspacesByKey[workspaceKey(userId, workspaceId)]
            ?: return@synchronized false
        workspacesByKey[workspace.key] = workspace.copy(deletedAt = deletedAt, updatedAt = deletedAt)
        pagesByKey.replaceAll { key, page ->
            if (key.startsWith("$userId:") && page.workspaceId == workspaceId) {
                page.copy(
                    deletedAt = deletedAt,
                    updatedAt = maxOf(deletedAt, page.updatedAt + 1L),
                    revision = page.revision + 1L,
                )
            } else {
                page
            }
        }
        true
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

    override suspend fun search(userId: String, query: ContentSearchQuery): List<ContentSearchResult> {
        val normalizedQuery = query.query.trim().lowercase()
        if (normalizedQuery.isBlank() || "Page" !in query.scopes) return emptyList()
        return listPages(
            userId = userId,
            workspaceId = query.workspaceId,
            includeDeleted = false,
        )
            .asSequence()
            .mapNotNull { page ->
                val haystack = "${page.title}\n${page.content}".lowercase()
                if (!haystack.contains(normalizedQuery)) return@mapNotNull null
                ContentSearchResult(
                    targetType = "Page",
                    workspaceId = page.workspaceId,
                    pageId = page.id,
                    title = page.title,
                    subtitle = "Page",
                    snippet = page.content.take(240),
                    score = if (page.title.lowercase().contains(normalizedQuery)) 1000 else 500,
                    updatedAt = page.updatedAt,
                )
            }
            .sortedWith(compareByDescending<ContentSearchResult> { it.score }.thenByDescending { it.updatedAt })
            .take(query.limit)
            .toList()
    }

    override suspend fun upsertPage(userId: String, page: PageRecord): PageMutationResult =
        synchronized(pagesByKey) {
            workspacesByKey[workspaceKey(userId, page.workspaceId)]
                ?: return@synchronized PageMutationResult.Forbidden
            if (page.parentPageId != null) {
                val parent = pagesByKey[pageKey(userId, page.parentPageId)]
                    ?: return@synchronized PageMutationResult.Forbidden
                if (parent.workspaceId != page.workspaceId) {
                    return@synchronized PageMutationResult.Forbidden
                }
            }

            val key = pageKey(userId, page.id)
            val existing = pagesByKey[key]
            if (existing == null) {
                if (page.revision != 0L) return@synchronized PageMutationResult.NotFound
                val created = page.copy(revision = 1L)
                pagesByKey[key] = created
                return@synchronized PageMutationResult.Applied(created)
            }
            if (existing.revision != page.revision) {
                return@synchronized PageMutationResult.Conflict(page.revision, existing)
            }

            val updated = page.copy(
                createdAt = existing.createdAt,
                updatedAt = maxOf(page.updatedAt, existing.updatedAt + 1L),
                revision = existing.revision + 1L,
            )
            pagesByKey[key] = updated
            PageMutationResult.Applied(updated)
        }

    override suspend fun updatePageBlockText(
        userId: String,
        pageId: String,
        blockId: String,
        text: String,
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult {
        return mutatePageContent(userId, pageId, expectedRevision, updatedAt) { content ->
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
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult {
        return mutatePageContent(userId, pageId, expectedRevision, updatedAt) { content ->
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
        expectedRevision: Long,
        updatedAt: Long,
    ): PageMutationResult {
        return mutatePageContent(userId, pageId, expectedRevision, updatedAt) { content ->
            PageContentJsonMutator.updateTableCellValue(
                content = content,
                rowId = rowId,
                columnId = columnId,
                value = value,
                valueJson = valueJson,
            )
        }
    }

    override suspend fun softDeletePage(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        deletedAt: Long,
    ): PageMutationResult = mutatePageTreeDeletion(
        userId = userId,
        pageId = pageId,
        expectedRevision = expectedRevision,
        deletedAt = deletedAt,
        updatedAt = deletedAt,
    )

    override suspend fun restorePage(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        restoredAt: Long,
    ): PageMutationResult = mutatePageTreeDeletion(
        userId = userId,
        pageId = pageId,
        expectedRevision = expectedRevision,
        deletedAt = null,
        updatedAt = restoredAt,
    )

    override suspend fun deletePagePermanently(
        userId: String,
        pageId: String,
        expectedRevision: Long,
    ): PageMutationResult = synchronized(pagesByKey) {
        val page = pagesByKey[pageKey(userId, pageId)]
            ?: return@synchronized PageMutationResult.NotFound
        if (page.revision != expectedRevision) {
            return@synchronized PageMutationResult.Conflict(expectedRevision, page)
        }
        pagesByKey.entries.removeIf { entry ->
            entry.key.startsWith("$userId:") &&
                (entry.value.id == page.id || entry.value.parentPageId == page.id)
        }
        PageMutationResult.PermanentlyDeleted
    }

    override suspend fun mutatePageContent(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        updatedAt: Long,
        transform: (String) -> String?,
    ): PageMutationResult {
        val key = pageKey(userId, pageId)
        return synchronized(pagesByKey) {
            val page = pagesByKey[key]?.takeIf { existing -> existing.deletedAt == null }
                ?: return@synchronized PageMutationResult.NotFound
            workspacesByKey[workspaceKey(userId, page.workspaceId)]
                ?.takeIf { existing -> existing.deletedAt == null }
                ?: return@synchronized PageMutationResult.NotFound
            if (page.revision != expectedRevision) {
                return@synchronized PageMutationResult.Conflict(expectedRevision, page)
            }
            val updatedContent = transform(page.content)
                ?: return@synchronized PageMutationResult.Rejected
            val nextUpdatedAt = maxOf(updatedAt, page.updatedAt + 1L)
            val updatedPage = page.copy(
                content = updatedContent,
                updatedAt = nextUpdatedAt,
                revision = page.revision + 1L,
            )
            pagesByKey[key] = updatedPage
            PageMutationResult.Applied(updatedPage)
        }
    }

    private fun mutatePageTreeDeletion(
        userId: String,
        pageId: String,
        expectedRevision: Long,
        deletedAt: Long?,
        updatedAt: Long,
    ): PageMutationResult = synchronized(pagesByKey) {
        val page = pagesByKey[pageKey(userId, pageId)]
            ?: return@synchronized PageMutationResult.NotFound
        if (page.revision != expectedRevision) {
            return@synchronized PageMutationResult.Conflict(expectedRevision, page)
        }

        val nextRoot = page.copy(
            deletedAt = deletedAt,
            updatedAt = maxOf(updatedAt, page.updatedAt + 1L),
            revision = page.revision + 1L,
        )
        pagesByKey[pageKey(userId, pageId)] = nextRoot
        pagesByKey.replaceAll { key, existing ->
            if (key.startsWith("$userId:") && existing.parentPageId == pageId) {
                existing.copy(
                    deletedAt = deletedAt,
                    updatedAt = maxOf(updatedAt, existing.updatedAt + 1L),
                    revision = existing.revision + 1L,
                )
            } else {
                existing
            }
        }
        PageMutationResult.Applied(nextRoot)
    }
}

private val WorkspaceRecord.key: String
    get() = workspaceKey(userId, id)

private fun workspaceKey(userId: String, workspaceId: String): String = "$userId:$workspaceId"

private fun pageKey(userId: String, pageId: String): String = "$userId:$pageId"
