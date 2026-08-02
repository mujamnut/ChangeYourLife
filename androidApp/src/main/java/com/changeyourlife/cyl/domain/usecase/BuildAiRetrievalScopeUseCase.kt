package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.repository.AiPageContextAccess
import com.changeyourlife.cyl.domain.repository.AiRetrievalMode
import com.changeyourlife.cyl.domain.repository.AiRetrievalScope
import javax.inject.Inject

class BuildAiRetrievalScopeUseCase @Inject constructor() {
    operator fun invoke(
        workspaceId: String,
        pages: List<Page>,
        prompt: String,
        attachedPageId: String?,
        explicitlyMentionedPageIds: List<String>,
        searchResults: List<SearchResult>,
    ): AiRetrievalSelection {
        val workspacePages = pages
            .asSequence()
            .filter { page ->
                page.workspaceId == workspaceId &&
                    page.deletedAt == null &&
                    page.id.isNotBlank()
            }
            .distinctBy(Page::id)
            .toList()
        val pageIds = workspacePages.map(Page::id).toSet()
        val currentPageId = attachedPageId
            .orEmpty()
            .takeIf(pageIds::contains)
            .orEmpty()
        val explicitPageIds = explicitlyMentionedPageIds
            .asSequence()
            .filter(pageIds::contains)
            .filterNot { pageId -> pageId == currentPageId }
            .distinct()
            .take(MaxExplicitTargets)
            .toList()
        val directTargetIds = buildSet {
            if (currentPageId.isNotBlank()) add(currentPageId)
            addAll(explicitPageIds)
        }
        val retrievedPageIds = if (directTargetIds.isNotEmpty()) {
            emptyList()
        } else {
            searchResults
                .asSequence()
                .filter { result -> result.target.workspaceId == workspaceId }
                .map { result -> result.target.pageId }
                .filter(pageIds::contains)
                .distinct()
                .take(MaxRetrievedTargets)
                .toList()
        }
        val scope = AiRetrievalScope(
            workspaceId = workspaceId,
            mode = if (directTargetIds.isNotEmpty()) {
                AiRetrievalMode.Page
            } else {
                AiRetrievalMode.Workspace
            },
            currentPageId = currentPageId,
            explicitPageIds = explicitPageIds,
            retrievedPageIds = retrievedPageIds,
            includeTasks = prompt.requestsTaskContext(),
        )
        val accessByPageId = buildMap {
            workspacePages.forEach { page -> put(page.id, AiPageContextAccess.Metadata) }
            retrievedPageIds.forEach { pageId -> put(pageId, AiPageContextAccess.Retrieved) }
            explicitPageIds.forEach { pageId -> put(pageId, AiPageContextAccess.Target) }
            if (currentPageId.isNotBlank()) put(currentPageId, AiPageContextAccess.Target)
        }
        val priorityByPageId = buildMap {
            scope.detailedPageIds.forEachIndexed { index, pageId -> put(pageId, index) }
        }
        val orderedPages = workspacePages.sortedWith(
            compareBy<Page> { page -> priorityByPageId[page.id] ?: Int.MAX_VALUE }
                .thenByDescending(Page::updatedAt),
        ).take(MaxCatalogPages)
        return AiRetrievalSelection(
            scope = scope,
            pages = orderedPages,
            accessByPageId = accessByPageId,
        )
    }

    private fun String.requestsTaskContext(): Boolean {
        val normalized = lowercase()
        return TaskContextTerms.any { term -> normalized.contains(term) }
    }

    private companion object {
        const val MaxExplicitTargets = 8
        const val MaxRetrievedTargets = 4
        const val MaxCatalogPages = 250

        val TaskContextTerms = setOf(
            "task",
            "tasks",
            "todo",
            "to-do",
            "tugas",
            "reminder",
            "remind",
            "peringatan",
            "ingatkan",
            "deadline",
            "due date",
        )
    }
}

data class AiRetrievalSelection(
    val scope: AiRetrievalScope,
    val pages: List<Page>,
    val accessByPageId: Map<String, AiPageContextAccess>,
) {
    val detailedPageIds: Set<String>
        get() = scope.detailedPageIds

    fun accessFor(pageId: String): AiPageContextAccess =
        accessByPageId[pageId] ?: AiPageContextAccess.Metadata
}
