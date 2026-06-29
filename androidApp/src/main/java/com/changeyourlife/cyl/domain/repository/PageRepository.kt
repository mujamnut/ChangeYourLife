package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableRow
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun observePages(workspaceId: String): Flow<List<Page>>

    fun observeChildPages(parentPageId: String): Flow<List<Page>>

    fun observeRecentPages(limit: Int = 5): Flow<List<Page>>

    fun observeRecentPages(workspaceId: String, limit: Int = 5): Flow<List<Page>>

    fun observeDeletedPages(workspaceId: String): Flow<List<Page>>

    fun observePage(pageId: String): Flow<Page?>

    fun observePageSyncState(pageId: String): Flow<PageSyncState>

    fun observePageCount(): Flow<Int>

    fun observePageCount(workspaceId: String): Flow<Int>

    suspend fun getPage(pageId: String): Page?

    suspend fun upsertPage(page: Page)

    suspend fun updateBlockText(
        pageId: String,
        blockId: String,
        text: String,
    ): Boolean

    suspend fun updateBlock(
        pageId: String,
        block: PageBlock,
    ): Boolean

    suspend fun updatePropertyValue(
        pageId: String,
        propertyId: String = "",
        propertyName: String = "",
        value: String,
    ): Boolean

    suspend fun updateTableCellValue(
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
    ): Boolean

    suspend fun updateTable(
        pageId: String,
        tableBlockId: String,
        table: PageTable,
    ): Boolean

    suspend fun updateTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
    ): Boolean

    suspend fun addBlock(
        pageId: String,
        block: PageBlock,
        parentBlockId: String = "",
        afterBlockId: String = "",
        targetIndex: Int? = null,
    ): Boolean

    suspend fun deleteBlock(
        pageId: String,
        blockId: String,
    ): Boolean

    suspend fun moveBlock(
        pageId: String,
        blockId: String,
        targetIndex: Int,
    ): Boolean

    suspend fun addProperty(
        pageId: String,
        property: PageProperty,
        targetIndex: Int? = null,
    ): Boolean

    suspend fun deleteProperty(
        pageId: String,
        propertyId: String,
    ): Boolean

    suspend fun moveProperty(
        pageId: String,
        propertyId: String,
        targetIndex: Int,
    ): Boolean

    suspend fun addTableColumn(
        pageId: String,
        tableBlockId: String,
        column: PageTableColumn,
        cellValues: Map<String, String> = emptyMap(),
        targetIndex: Int? = null,
    ): Boolean

    suspend fun deleteTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
    ): Boolean

    suspend fun moveTableColumn(
        pageId: String,
        tableBlockId: String,
        columnId: String,
        targetIndex: Int,
    ): Boolean

    suspend fun addTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
        targetIndex: Int? = null,
    ): Boolean

    suspend fun updateTableRow(
        pageId: String,
        tableBlockId: String,
        row: PageTableRow,
    ): Boolean

    suspend fun deleteTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
    ): Boolean

    suspend fun moveTableRow(
        pageId: String,
        tableBlockId: String,
        rowId: String,
        targetIndex: Int,
    ): Boolean

    suspend fun createPage(
        workspaceId: String,
        title: String,
        content: String = "",
        parentPageId: String? = null,
    ): Page

    suspend fun deletePage(pageId: String)

    suspend fun restorePage(pageId: String)

    suspend fun deletePagePermanently(pageId: String)

    suspend fun keepLocalPageConflict(pageId: String)

    suspend fun useRemotePageConflict(pageId: String)
}
