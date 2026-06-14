package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.PageRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao,
) : PageRepository {
    override fun observePages(workspaceId: String): Flow<List<Page>> {
        return pageDao.observePages(workspaceId)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeChildPages(parentPageId: String): Flow<List<Page>> {
        return pageDao.observeChildPages(parentPageId)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeRecentPages(limit: Int): Flow<List<Page>> {
        return pageDao.observeRecentPages(limit)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<Page>> {
        return pageDao.observeRecentPages(workspaceId, limit)
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observePage(pageId: String): Flow<Page?> {
        return pageDao.observePage(pageId)
            .map { page -> page?.toDomain() }
    }

    override fun observePageCount(): Flow<Int> {
        return pageDao.observePageCount()
    }

    override fun observePageCount(workspaceId: String): Flow<Int> {
        return pageDao.observePageCount(workspaceId)
    }

    override suspend fun getPage(pageId: String): Page? {
        return pageDao.getPage(pageId)?.toDomain()
    }

    override suspend fun upsertPage(page: Page) {
        pageDao.upsertPage(page.toEntity())
    }

    override suspend fun createPage(
        workspaceId: String,
        title: String,
        content: String,
        parentPageId: String?,
    ): Page {
        val now = System.currentTimeMillis()
        val page = Page(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            parentPageId = parentPageId,
            title = title,
            content = content,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        pageDao.upsertPage(page.toEntity())
        return page
    }
}
