package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiRetrievalScope
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsRequest

internal class AiRetrievalPrivacyBoundary(
    private val contentRepository: ContentRepository,
) {
    suspend fun enforce(
        userId: String,
        request: ChatWithActionsRequest,
    ): AiRetrievalBoundaryResult {
        val ownedWorkspaces = contentRepository.listWorkspaces(
            userId = userId,
            includeDeleted = false,
        )
        val requestedWorkspaceId = request.retrievalScope.workspaceId.trim()
        val isLegacyRequest = requestedWorkspaceId.isBlank()
        val workspaceId = requestedWorkspaceId.ifBlank {
            ownedWorkspaces.singleOrNull()?.id.orEmpty()
        }
        if (workspaceId.isBlank()) {
            return AiRetrievalBoundaryResult.Rejected(
                code = "missing_workspace_scope",
                message = "AI retrieval requires one explicit active workspace.",
                forbidden = false,
            )
        }
        if (ownedWorkspaces.none { workspace -> workspace.id == workspaceId }) {
            return AiRetrievalBoundaryResult.Rejected(
                code = "workspace_forbidden",
                message = "The requested AI workspace is not available to this user.",
                forbidden = true,
            )
        }

        val requestedDetailedPageIds = buildList {
            request.retrievalScope.currentPageId
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(::add)
            request.retrievalScope.explicitPageIds
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .take(MaxExplicitPages)
                .forEach(::add)
            request.retrievalScope.retrievedPageIds
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .take(MaxRetrievedPages)
                .forEach(::add)
            if (isLegacyRequest) {
                request.pages
                    .firstOrNull(AiPageContext::isFocused)
                    ?.id
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }.distinct()
        for (pageId in requestedDetailedPageIds) {
            val storedPage = contentRepository.getPage(
                userId = userId,
                pageId = pageId,
                includeDeleted = true,
            ) ?: continue
            if (storedPage.workspaceId != workspaceId || storedPage.deletedAt != null) {
                return AiRetrievalBoundaryResult.Rejected(
                    code = "page_scope_forbidden",
                    message = "A requested AI page is outside the active workspace or has been deleted.",
                    forbidden = true,
                )
            }
        }

        val syncedPageIds = contentRepository.listPages(
            userId = userId,
            workspaceId = workspaceId,
            includeDeleted = false,
        ).mapTo(mutableSetOf()) { page -> page.id }
        val candidatePages = request.pages
            .asSequence()
            .filter { page -> page.id.isNotBlank() }
            .filter { page ->
                when {
                    page.workspaceId == workspaceId -> true
                    page.workspaceId.isNotBlank() -> false
                    isLegacyRequest -> true
                    else -> page.id in syncedPageIds
                }
            }
            .distinctBy(AiPageContext::id)
            .take(MaxCatalogPages)
            .toList()
        val availablePageIds = candidatePages.mapTo(mutableSetOf(), AiPageContext::id)
        val legacyFocusedIds = candidatePages
            .asSequence()
            .filter(AiPageContext::isFocused)
            .map(AiPageContext::id)
            .take(1)
            .toList()
        val currentPageId = request.retrievalScope.currentPageId
            .trim()
            .takeIf(availablePageIds::contains)
            .orEmpty()
            .ifBlank { legacyFocusedIds.firstOrNull().orEmpty() }
        val explicitPageIds = request.retrievalScope.explicitPageIds
            .normalizedPageIds(
                availablePageIds = availablePageIds,
                excludedPageIds = setOf(currentPageId),
                limit = MaxExplicitPages,
            )
        val retrievedPageIds = request.retrievalScope.retrievedPageIds
            .normalizedPageIds(
                availablePageIds = availablePageIds,
                excludedPageIds = explicitPageIds.toSet() + currentPageId,
                limit = MaxRetrievedPages,
            )
        val targetPageIds = buildSet {
            if (currentPageId.isNotBlank()) add(currentPageId)
            addAll(explicitPageIds)
        }
        val readablePageIds = targetPageIds + retrievedPageIds
        val securedPages = candidatePages.map { page ->
            val access = when (page.id) {
                in targetPageIds -> AccessTarget
                in retrievedPageIds -> AccessRetrieved
                else -> AccessMetadata
            }
            page.copy(
                workspaceId = workspaceId,
                access = access,
                blocks = if (page.id in readablePageIds) page.blocks else emptyList(),
                totalBlockCount = if (page.id in readablePageIds) page.totalBlockCount else 0,
                isFocused = page.id in targetPageIds,
                contextComplete = page.id in readablePageIds && page.contextComplete,
            )
        }
        val securedScope = AiRetrievalScope(
            workspaceId = workspaceId,
            mode = if (targetPageIds.isNotEmpty()) ModePage else ModeWorkspace,
            currentPageId = currentPageId,
            explicitPageIds = explicitPageIds,
            retrievedPageIds = retrievedPageIds,
            includeTasks = request.retrievalScope.includeTasks,
        )
        return AiRetrievalBoundaryResult.Allowed(
            request.copy(
                retrievalScope = securedScope,
                pages = securedPages,
                tasks = if (securedScope.includeTasks) {
                    request.tasks
                        .filter { task ->
                            task.workspaceId == workspaceId ||
                                isLegacyRequest && task.workspaceId.isBlank()
                        }
                        .filter { task -> task.id.isNotBlank() || task.title.isNotBlank() }
                        .distinctBy { task -> task.id.ifBlank { task.title } }
                        .take(MaxTasks)
                        .map { task -> task.copy(workspaceId = workspaceId) }
                } else {
                    emptyList()
                },
            ),
        )
    }

    private fun List<String>.normalizedPageIds(
        availablePageIds: Set<String>,
        excludedPageIds: Set<String>,
        limit: Int,
    ): List<String> = asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filter(availablePageIds::contains)
        .filterNot(excludedPageIds::contains)
        .distinct()
        .take(limit)
        .toList()

    private companion object {
        const val ModeWorkspace = "Workspace"
        const val ModePage = "Page"
        const val AccessMetadata = "Metadata"
        const val AccessTarget = "Target"
        const val AccessRetrieved = "Retrieved"
        const val MaxCatalogPages = 250
        const val MaxExplicitPages = 8
        const val MaxRetrievedPages = 4
        const val MaxTasks = 200
    }
}

internal sealed interface AiRetrievalBoundaryResult {
    data class Allowed(
        val request: ChatWithActionsRequest,
    ) : AiRetrievalBoundaryResult

    data class Rejected(
        val code: String,
        val message: String,
        val forbidden: Boolean,
    ) : AiRetrievalBoundaryResult
}
