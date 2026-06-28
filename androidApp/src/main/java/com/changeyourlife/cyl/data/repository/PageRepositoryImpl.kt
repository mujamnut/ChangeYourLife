package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.sync.SyncApi
import com.changeyourlife.cyl.data.remote.sync.toDomain as syncToDomain
import com.changeyourlife.cyl.data.remote.sync.toSyncDto
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.PageRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import retrofit2.HttpException

class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao,
    private val syncApi: SyncApi,
    private val tokenStore: AuthTokenStore,
) : PageRepository {
    override fun observePages(workspaceId: String): Flow<List<Page>> {
        return pageDao.observePages(workspaceId)
            .onStart { refreshPages(workspaceId, includeDeleted = false) }
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
            .onStart { refreshPages(workspaceId, includeDeleted = false) }
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observeDeletedPages(workspaceId: String): Flow<List<Page>> {
        return pageDao.observeDeletedPages(workspaceId)
            .onStart { refreshPages(workspaceId, includeDeleted = true) }
            .map { pages -> pages.map { it.toDomain() } }
    }

    override fun observePage(pageId: String): Flow<Page?> {
        return pageDao.observePage(pageId)
            .onStart { refreshPage(pageId, includeDeleted = false) }
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
        pushPage(page)
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
        pushPage(page)
        return page
    }

    override suspend fun deletePage(pageId: String) {
        val page = pageDao.getPage(pageId)?.toDomain()
        pageDao.softDeletePageTree(
            pageId = pageId,
            deletedAt = System.currentTimeMillis(),
        )
        if (page != null) deleteRemotePage(page.id)
    }

    override suspend fun restorePage(pageId: String) {
        val page = pageDao.getPage(pageId)?.toDomain()
        pageDao.restorePageTree(
            pageId = pageId,
            restoredAt = System.currentTimeMillis(),
        )
        if (page != null) restoreRemotePage(page.id)
    }

    override suspend fun deletePagePermanently(pageId: String) {
        pageDao.deletePageTreePermanently(pageId)
        deleteRemotePagePermanently(pageId)
    }

    private suspend fun refreshPages(workspaceId: String, includeDeleted: Boolean) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.listPages(
                authorization = header,
                workspaceId = workspaceId,
                includeDeleted = includeDeleted,
            ).pages
        }.onSuccess { remotePages ->
            remotePages
                .map { page -> page.syncToDomain().toEntity() }
                .forEach { page -> pageDao.upsertPage(page) }
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun refreshPage(pageId: String, includeDeleted: Boolean) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.getPage(
                authorization = header,
                id = pageId,
                includeDeleted = includeDeleted,
            )
        }.onSuccess { remotePage ->
            pageDao.upsertPage(remotePage.syncToDomain().toEntity())
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun pushPage(page: Page) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.upsertPage(
                authorization = header,
                id = page.id,
                page = page.toSyncDto(),
            )
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun deleteRemotePage(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePage(header, pageId)
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun restoreRemotePage(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.restorePage(header, pageId)
        }.onFailure(::handleSyncFailure)
    }

    private suspend fun deleteRemotePagePermanently(pageId: String) {
        val header = authHeader() ?: return
        runCatching {
            syncApi.deletePagePermanently(header, pageId)
        }.onFailure(::handleSyncFailure)
    }

    private fun authHeader(): String? {
        return tokenStore.token.value?.takeIf { it.isNotBlank() }?.let { token -> "Bearer $token" }
    }

    private fun handleSyncFailure(error: Throwable) {
        if (error is HttpException && error.code() == 401) {
            tokenStore.clearToken()
        }
    }
}
