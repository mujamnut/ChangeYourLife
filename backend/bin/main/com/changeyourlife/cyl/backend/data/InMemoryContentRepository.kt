package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.ContentSearchQuery
import com.changeyourlife.cyl.backend.domain.ContentSearchResult
import com.changeyourlife.cyl.backend.domain.AiActionPlanCommitCommand
import com.changeyourlife.cyl.backend.domain.AiActionPlanCommitReceipt
import com.changeyourlife.cyl.backend.domain.AiActionPlanCommitResult
import com.changeyourlife.cyl.backend.domain.AiActionPlanPageMutation
import com.changeyourlife.cyl.backend.domain.AiActionPlanPageOperation
import com.changeyourlife.cyl.backend.domain.PageMutationResult
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.util.concurrent.ConcurrentHashMap

class InMemoryContentRepository : ContentRepository {
    private val workspacesByKey = ConcurrentHashMap<String, WorkspaceRecord>()
    private val pagesByKey = ConcurrentHashMap<String, PageRecord>()
    private val aiPlanCommitsByKey = ConcurrentHashMap<String, InMemoryAiPlanCommit>()

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

    override suspend fun commitAiActionPlan(
        userId: String,
        command: AiActionPlanCommitCommand,
    ): AiActionPlanCommitResult = synchronized(pagesByKey) {
        val ledgerKey = "$userId:${command.idempotencyKey}"
        aiPlanCommitsByKey[ledgerKey]?.let { existing ->
            return@synchronized if (existing.requestFingerprint == command.requestFingerprint) {
                AiActionPlanCommitResult.Committed(
                    receipt = existing.receipt,
                    replayed = true,
                )
            } else {
                AiActionPlanCommitResult.IdempotencyConflict(existing.requestFingerprint)
            }
        }

        val duplicatePageId = command.mutations
            .groupingBy { mutation -> mutation.pageId }
            .eachCount()
            .entries
            .firstOrNull { entry -> entry.value > 1 }
            ?.key
        if (duplicatePageId != null) {
            return@synchronized AiActionPlanCommitResult.Rejected(
                code = "duplicate_page_mutation",
                message = "Page $duplicatePageId appears more than once in the AI plan.",
            )
        }
        val deletePageIds = command.mutations
            .filter { mutation -> mutation.operation == AiActionPlanPageOperation.PERMANENT_DELETE }
            .mapTo(mutableSetOf(), AiActionPlanPageMutation::pageId)
        val upsertBelowDeletedPage = command.mutations.firstOrNull { mutation ->
            mutation.operation == AiActionPlanPageOperation.UPSERT &&
                mutation.page?.parentPageId in deletePageIds
        }
        if (upsertBelowDeletedPage != null) {
            return@synchronized AiActionPlanCommitResult.Rejected(
                code = "parent_scheduled_for_deletion",
                message = "Page ${upsertBelowDeletedPage.pageId} cannot be saved under a page being deleted.",
            )
        }
        if (workspacesByKey[workspaceKey(userId, command.workspaceId)] == null) {
            return@synchronized AiActionPlanCommitResult.Forbidden(command.workspaceId)
        }

        val stagedPages = pagesByKey.toMutableMap()
        for (mutation in command.mutations.sortedBy { mutation -> mutation.pageId }) {
            val current = stagedPages[pageKey(userId, mutation.pageId)]
            if (current == null) {
                if (mutation.operation == AiActionPlanPageOperation.PERMANENT_DELETE) {
                    return@synchronized AiActionPlanCommitResult.NotFound(mutation.pageId)
                }
                if (mutation.expectedRevision != 0L) {
                    return@synchronized AiActionPlanCommitResult.NotFound(mutation.pageId)
                }
            } else if (current.revision != mutation.expectedRevision) {
                return@synchronized AiActionPlanCommitResult.RevisionConflict(
                    pageId = mutation.pageId,
                    expectedRevision = mutation.expectedRevision,
                    currentPage = current,
                )
            }

            val requestedPage = mutation.page
            if (mutation.operation == AiActionPlanPageOperation.UPSERT) {
                if (
                    requestedPage == null ||
                    requestedPage.id != mutation.pageId ||
                    requestedPage.workspaceId != command.workspaceId
                ) {
                    return@synchronized AiActionPlanCommitResult.Rejected(
                        code = "invalid_page_snapshot",
                        message = "The upsert mutation for ${mutation.pageId} has an invalid page snapshot.",
                    )
                }
            } else if (current?.deletedAt == null) {
                return@synchronized AiActionPlanCommitResult.Rejected(
                    code = "page_not_in_trash",
                    message = "Page ${mutation.pageId} must be in trash before permanent deletion.",
                )
            }
        }

        val upserts = command.mutations
            .filter { mutation -> mutation.operation == AiActionPlanPageOperation.UPSERT }
            .orderParentFirst()
        for (mutation in upserts) {
            val requestedPage = requireNotNull(mutation.page)
            val parent = requestedPage.parentPageId?.let { parentId ->
                stagedPages[pageKey(userId, parentId)]
            }
            if (
                requestedPage.parentPageId != null &&
                (parent == null || parent.workspaceId != command.workspaceId)
            ) {
                return@synchronized AiActionPlanCommitResult.Forbidden(requestedPage.parentPageId)
            }

            val key = pageKey(userId, mutation.pageId)
            val current = stagedPages[key]
            stagedPages[key] = if (current == null) {
                requestedPage.copy(revision = 1L)
            } else {
                requestedPage.copy(
                    createdAt = current.createdAt,
                    updatedAt = maxOf(requestedPage.updatedAt, current.updatedAt + 1L),
                    revision = current.revision + 1L,
                )
            }
        }

        val permanentlyDeletedPageIds = mutableListOf<String>()
        command.mutations
            .filter { mutation -> mutation.operation == AiActionPlanPageOperation.PERMANENT_DELETE }
            .forEach { mutation ->
                val pending = ArrayDeque<String>().apply { add(mutation.pageId) }
                while (pending.isNotEmpty()) {
                    val deletingPageId = pending.removeFirst()
                    stagedPages.entries
                        .asSequence()
                        .filter { entry ->
                            entry.key.startsWith("$userId:") &&
                                entry.value.parentPageId == deletingPageId
                        }
                        .mapTo(pending) { entry -> entry.value.id }
                    stagedPages.remove(pageKey(userId, deletingPageId))
                    permanentlyDeletedPageIds += deletingPageId
                }
            }

        val userPrefix = "$userId:"
        pagesByKey.keys
            .filter { key -> key.startsWith(userPrefix) }
            .forEach(pagesByKey::remove)
        stagedPages
            .filterKeys { key -> key.startsWith(userPrefix) }
            .forEach(pagesByKey::put)

        val receipt = AiActionPlanCommitReceipt(
            auditId = command.auditId,
            workspaceId = command.workspaceId,
            pages = upserts.map { mutation ->
                requireNotNull(pagesByKey[pageKey(userId, mutation.pageId)])
            },
            permanentlyDeletedPageIds = permanentlyDeletedPageIds.distinct(),
            actionCount = command.actionCount,
            committedAt = command.committedAt,
        )
        aiPlanCommitsByKey[ledgerKey] = InMemoryAiPlanCommit(
            requestFingerprint = command.requestFingerprint,
            receipt = receipt,
        )
        AiActionPlanCommitResult.Committed(receipt = receipt, replayed = false)
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

private data class InMemoryAiPlanCommit(
    val requestFingerprint: String,
    val receipt: AiActionPlanCommitReceipt,
)

private fun List<com.changeyourlife.cyl.backend.domain.AiActionPlanPageMutation>.orderParentFirst():
    List<com.changeyourlife.cyl.backend.domain.AiActionPlanPageMutation> {
    val mutationsByPageId = associateBy { mutation -> mutation.pageId }
    val depthByPageId = mutableMapOf<String, Int>()

    fun depth(pageId: String, visiting: Set<String> = emptySet()): Int {
        depthByPageId[pageId]?.let { return it }
        if (pageId in visiting) return 0
        val parentId = mutationsByPageId[pageId]?.page?.parentPageId
        val value = if (parentId == null || parentId !in mutationsByPageId) {
            0
        } else {
            depth(parentId, visiting + pageId) + 1
        }
        depthByPageId[pageId] = value
        return value
    }

    return sortedWith(compareBy({ mutation -> depth(mutation.pageId) }, { mutation -> mutation.pageId }))
}

private val WorkspaceRecord.key: String
    get() = workspaceKey(userId, id)

private fun workspaceKey(userId: String, workspaceId: String): String = "$userId:$workspaceId"

private fun pageKey(userId: String, pageId: String): String = "$userId:$pageId"
