package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult

interface SearchRepository {
    suspend fun search(query: SearchQuery): List<SearchResult>
}
