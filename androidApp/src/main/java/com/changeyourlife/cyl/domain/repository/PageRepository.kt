package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun observePages(workspaceId: String): Flow<List<Page>>

    fun observeChildPages(parentPageId: String): Flow<List<Page>>

    fun observeRecentPages(limit: Int = 5): Flow<List<Page>>

    fun observeRecentPages(workspaceId: String, limit: Int = 5): Flow<List<Page>>

    fun observePage(pageId: String): Flow<Page?>

    fun observePageCount(): Flow<Int>

    fun observePageCount(workspaceId: String): Flow<Int>

    suspend fun getPage(pageId: String): Page?

    suspend fun upsertPage(page: Page)

    suspend fun createPage(
        workspaceId: String,
        title: String,
        content: String = "",
        parentPageId: String? = null,
    ): Page
}
