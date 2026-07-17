package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.SearchIndexDao
import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.sync.SearchResultSyncDto
import com.changeyourlife.cyl.data.remote.sync.SyncApi
import com.changeyourlife.cyl.data.search.SearchIndexRebuilder
import com.changeyourlife.cyl.data.search.SearchRanker
import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.repository.SearchRepository
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val searchIndexDao: SearchIndexDao,
    private val searchIndexRebuilder: SearchIndexRebuilder,
    private val searchRanker: SearchRanker,
    private val syncApi: SyncApi,
    private val tokenStore: AuthTokenStore,
) : SearchRepository {
    override suspend fun search(query: SearchQuery): List<SearchResult> {
        val normalized = query.normalized()
        if (normalized.workspaceId.isBlank()) return emptyList()
        searchIndexRebuilder.ensureWorkspaceIndexed(normalized.workspaceId)
        val terms = searchRanker.tokenize(normalized.normalizedQuery)
        val entries = fetchCandidates(normalized, terms)
        val localResults = entries
            .asSequence()
            .map { entry -> searchRanker.rank(entry, terms, normalized.currentPageId) }
            .filter { ranked -> terms.isEmpty() || ranked.score > 0 }
            .sortedWith(
                compareByDescending<com.changeyourlife.cyl.data.search.RankedSearchEntry> { ranked -> ranked.score }
                    .thenByDescending { ranked -> ranked.entry.updatedAt },
            )
            .take(normalized.limit)
            .map { ranked ->
                ranked.entry.toDomain(
                    score = ranked.score,
                    matchedTerms = ranked.matchedTerms,
                    snippetOverride = ranked.snippet,
                )
            }
            .toList()
        val remoteResults = fetchRemoteResults(normalized, terms)
        return (localResults + remoteResults)
            .distinctBy { result -> result.target.key }
            .sortedWith(
                compareByDescending<SearchResult> { result -> result.score }
                    .thenByDescending { result -> result.updatedAt },
            )
            .take(normalized.limit)
    }

    private suspend fun fetchCandidates(
        query: SearchQuery,
        terms: List<String>,
    ): List<SearchIndexEntity> {
        val candidateQueries = when {
            terms.isEmpty() -> listOf("")
            else -> buildList {
                val phrase = terms.joinToString(" ")
                add(phrase)
                addAll(terms)
            }
                .distinct()
                .take(MaxCandidateQueries)
        }
        return candidateQueries
            .flatMap { candidateQuery ->
                searchIndexDao.search(
                    workspaceId = query.workspaceId,
                    targetTypes = query.scopes.map { type -> type.name },
                    normalizedQuery = candidateQuery,
                    includeDeleted = query.includeDeleted,
                    limit = query.limit * CandidateMultiplier,
                )
            }
            .distinctBy(SearchIndexEntity::id)
    }

    private suspend fun fetchRemoteResults(
        query: SearchQuery,
        terms: List<String>,
    ): List<SearchResult> {
        if (!query.hasQuery) return emptyList()
        val token = tokenStore.token.value?.takeIf { value -> value.isNotBlank() } ?: return emptyList()
        return runCatching {
            syncApi.search(
                authorization = "Bearer $token",
                workspaceId = query.workspaceId,
                query = query.normalizedQuery,
                scope = query.scopes.joinToString(separator = ",") { type -> type.name },
                limit = query.limit,
            ).results.mapNotNull { result -> result.toDomainSearchResult(terms) }
        }.getOrElse { emptyList() }
    }

    private fun SearchResultSyncDto.toDomainSearchResult(terms: List<String>): SearchResult? {
        val targetType = SearchTargetType.entries.firstOrNull { type ->
            type.name.equals(targetType, ignoreCase = true)
        } ?: return null
        return SearchResult(
            target = SearchTarget(
                type = targetType,
                workspaceId = workspaceId,
                pageId = pageId,
                blockId = blockId,
                tableBlockId = tableBlockId,
                rowId = rowId,
                columnId = columnId,
                propertyId = propertyId,
                chatSessionId = chatSessionId,
                chatMessageId = chatMessageId,
            ),
            title = title,
            subtitle = subtitle,
            snippet = snippet,
            score = score,
            updatedAt = updatedAt,
            matchedTerms = terms,
        )
    }

    private companion object {
        private const val CandidateMultiplier = 6
        private const val MaxCandidateQueries = 6
    }
}
