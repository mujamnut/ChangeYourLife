package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.repository.SearchRepository
import javax.inject.Inject

class SearchWorkspaceUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(query: SearchQuery): List<SearchResult> {
        val normalized = query.normalized()
        if (!normalized.hasQuery) return emptyList()
        return searchRepository.search(normalized)
    }
}
