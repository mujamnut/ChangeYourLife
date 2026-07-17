package com.changeyourlife.cyl.data.search

import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.SearchIndexDao
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.SearchTargetType
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexRebuilderTest {
    @Test
    fun rebuildPageIndexesPageBlockPropertyTableRowCellAndRowPageContent() = runBlocking {
        val searchIndexDao = FakeSearchIndexDao()
        val rebuilder = SearchIndexRebuilder(
            searchIndexDao = searchIndexDao,
            pageDao = unusedDao(),
            pageContentDao = unusedDao(),
            chatMessageDao = unusedDao(),
        )

        rebuilder.rebuildPage(
            page = pageEntity(),
            document = PageBlockDocument(
                properties = listOf(
                    PageProperty(
                        id = "property-month",
                        name = "Month",
                        type = PagePropertyType.Date,
                        value = "2026-07",
                    ),
                ),
                blocks = listOf(
                    PageBlock(
                        id = "block-note",
                        type = PageBlockType.Text,
                        text = "Monthly opening note",
                    ),
                    PageBlock(
                        id = "block-transactions",
                        type = PageBlockType.DatabaseTable,
                        table = PageTable(
                            title = "Transactions",
                            columns = listOf(
                                PageTableColumn(
                                    id = "column-category",
                                    name = "Category",
                                    type = PageTableColumnType.Select,
                                ),
                                PageTableColumn(
                                    id = "column-amount",
                                    name = "Amount",
                                    type = PageTableColumnType.Number,
                                ),
                            ),
                            rows = listOf(
                                PageTableRow(
                                    id = "row-food",
                                    cells = mapOf(
                                        "column-category" to "Food",
                                        "column-amount" to "29",
                                    ),
                                    cellValues = mapOf(
                                        "column-amount" to PageTableCellValue(
                                            type = PageTableColumnType.Number,
                                            number = "29",
                                        ),
                                    ),
                                    blocks = listOf(
                                        PageBlock(
                                            id = "row-note",
                                            type = PageBlockType.Text,
                                            text = "receipt image uploaded",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val entries = searchIndexDao.entries
        assertEquals(
            setOf(
                SearchTargetType.Page.name,
                SearchTargetType.Block.name,
                SearchTargetType.Property.name,
                SearchTargetType.Table.name,
                SearchTargetType.Column.name,
                SearchTargetType.Row.name,
                SearchTargetType.Cell.name,
            ),
            entries.map { entry -> entry.targetType }.toSet(),
        )

        assertNotNull(entries.firstOrNull { entry ->
            entry.targetType == SearchTargetType.Property.name &&
                entry.propertyId == "property-month" &&
                entry.normalizedText.contains("2026 07")
        })
        assertNotNull(entries.firstOrNull { entry ->
            entry.targetType == SearchTargetType.Table.name &&
                entry.blockId == "block-transactions" &&
                entry.tableBlockId == "block-transactions"
        })
        assertNotNull(entries.firstOrNull { entry ->
            entry.targetType == SearchTargetType.Row.name &&
                entry.rowId == "row-food" &&
                entry.tableBlockId == "block-transactions" &&
                entry.normalizedText.contains("receipt image")
        })
        assertNotNull(entries.firstOrNull { entry ->
            entry.targetType == SearchTargetType.Cell.name &&
                entry.rowId == "row-food" &&
                entry.columnId == "column-amount" &&
                entry.normalizedText.contains("29")
        })
        assertNotNull(entries.firstOrNull { entry ->
            entry.targetType == SearchTargetType.Block.name &&
                entry.blockId == "row-note" &&
                entry.rowId == "row-food"
        })
    }

    @Test
    fun markDeletedAndRestoredUpdatesIndexedPageTargets() = runBlocking {
        val searchIndexDao = FakeSearchIndexDao()
        val rebuilder = SearchIndexRebuilder(
            searchIndexDao = searchIndexDao,
            pageDao = unusedDao(),
            pageContentDao = unusedDao(),
            chatMessageDao = unusedDao(),
        )
        rebuilder.rebuildPage(
            page = pageEntity(),
            document = PageBlockDocument(blocks = listOf(PageBlock(id = "block-1", type = PageBlockType.Text, text = "hello"))),
        )

        rebuilder.markPageTreeDeleted(pageId = "page-budget", deletedAt = 10_000L)
        assertTrue(searchIndexDao.entries.all { entry -> entry.deletedAt == 10_000L })

        rebuilder.markPageTreeRestored(pageId = "page-budget", restoredAt = 11_000L)
        assertTrue(searchIndexDao.entries.all { entry -> entry.deletedAt == null && entry.updatedAt == 11_000L })
    }

    private fun pageEntity(): PageEntity =
        PageEntity(
            id = "page-budget",
            workspaceId = "workspace-1",
            parentPageId = null,
            title = "Budget Tracker",
            content = "",
            sortOrder = 0,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            deletedAt = null,
        )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> unusedDao(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args ->
            when (method.name) {
                "toString" -> "Unused${T::class.java.simpleName}"
                "hashCode" -> 0
                "equals" -> args?.firstOrNull() === this
                else -> error("${T::class.java.simpleName}.${method.name} should not be called in this test")
            }
        } as T
    }

    private class FakeSearchIndexDao : SearchIndexDao {
        val entries = mutableListOf<SearchIndexEntity>()

        override suspend fun countForWorkspace(workspaceId: String): Int =
            entries.count { entry -> entry.workspaceId == workspaceId }

        override suspend fun countForWorkspaceAndType(workspaceId: String, targetType: String): Int =
            entries.count { entry -> entry.workspaceId == workspaceId && entry.targetType == targetType }

        override suspend fun search(
            workspaceId: String,
            targetTypes: List<String>,
            normalizedQuery: String,
            includeDeleted: Boolean,
            limit: Int,
        ): List<SearchIndexEntity> =
            entries
                .filter { entry -> entry.workspaceId == workspaceId }
                .filter { entry -> entry.targetType in targetTypes }
                .filter { entry -> includeDeleted || entry.deletedAt == null }
                .filter { entry -> normalizedQuery.isBlank() || entry.normalizedText.contains(normalizedQuery) }
                .take(limit)

        override suspend fun deleteForPage(pageId: String) {
            entries.removeAll { entry -> entry.pageId == pageId }
        }

        override suspend fun deleteForChatSession(sessionId: String) {
            entries.removeAll { entry -> entry.chatSessionId == sessionId }
        }

        override suspend fun deleteForPageTree(pageId: String) {
            entries.removeAll { entry -> entry.pageId == pageId }
        }

        override suspend fun markPageTreeDeleted(pageId: String, deletedAt: Long) {
            entries.replaceAll { entry ->
                if (entry.pageId == pageId) entry.copy(deletedAt = deletedAt, updatedAt = deletedAt) else entry
            }
        }

        override suspend fun markPageTreeRestored(pageId: String, restoredAt: Long) {
            entries.replaceAll { entry ->
                if (entry.pageId == pageId) entry.copy(deletedAt = null, updatedAt = restoredAt) else entry
            }
        }

        override suspend fun upsertAll(entries: List<SearchIndexEntity>) {
            entries.forEach { next ->
                this.entries.removeAll { existing -> existing.id == next.id }
                this.entries += next
            }
        }
    }
}
