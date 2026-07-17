package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.MentionCandidate
import com.changeyourlife.cyl.domain.model.MentionQuery
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.repository.SearchRepository
import javax.inject.Inject

class ResolveMentionUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(query: MentionQuery): List<MentionCandidate> {
        val searchQuery = query.toSearchQuery().normalized()
        if (searchQuery.workspaceId.isBlank()) return emptyList()

        return searchRepository.search(searchQuery)
            .asSequence()
            .filter { result -> result.target.type == SearchTargetType.Page }
            .filter { result -> result.target.pageId.isNotBlank() }
            .map { result ->
                MentionCandidate(
                    pageId = result.target.pageId,
                    title = result.title.ifBlank { "Untitled page" },
                    subtitle = result.subtitle,
                    score = result.score,
                    updatedAt = result.updatedAt,
                )
            }
            .distinctBy(MentionCandidate::pageId)
            .sortedWith(
                compareByDescending<MentionCandidate> { candidate -> candidate.score }
                    .thenByDescending { candidate -> candidate.updatedAt },
            )
            .toList()
    }
}
