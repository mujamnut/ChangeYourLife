package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.PageTableCellEntity
import com.changeyourlife.cyl.data.local.entity.PageTableColumnEntity
import com.changeyourlife.cyl.data.local.entity.PageTableEntity
import com.changeyourlife.cyl.data.local.entity.PageTableRowEntity
import com.changeyourlife.cyl.data.local.model.PageContentSnapshot

@Dao
interface PageContentDao {
    @Query("SELECT * FROM page_blocks WHERE pageId = :pageId ORDER BY sortOrder ASC")
    suspend fun getBlocks(pageId: String): List<PageBlockEntity>

    @Query("SELECT * FROM page_properties WHERE pageId = :pageId ORDER BY sortOrder ASC")
    suspend fun getProperties(pageId: String): List<PagePropertyEntity>

    @Query("SELECT * FROM page_tables WHERE pageId = :pageId")
    suspend fun getTables(pageId: String): List<PageTableEntity>

    @Query(
        """
        SELECT * FROM page_table_columns
        WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId)
        ORDER BY tableId ASC, sortOrder ASC
        """,
    )
    suspend fun getColumns(pageId: String): List<PageTableColumnEntity>

    @Query(
        """
        SELECT * FROM page_table_rows
        WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId)
        ORDER BY tableId ASC, sortOrder ASC
        """,
    )
    suspend fun getRows(pageId: String): List<PageTableRowEntity>

    @Query(
        """
        SELECT * FROM page_table_cells
        WHERE rowId IN (
            SELECT id FROM page_table_rows
            WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId)
        )
        ORDER BY rowId ASC, columnId ASC
        """,
    )
    suspend fun getCells(pageId: String): List<PageTableCellEntity>

    @Query(
        """
        UPDATE page_blocks
        SET text = :text, updatedAt = :updatedAt, deletedAt = NULL
        WHERE pageId = :pageId AND id = :blockId
        """,
    )
    suspend fun updateBlockText(
        pageId: String,
        blockId: String,
        text: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE page_blocks
        SET text = :text,
            richTextJson = :richTextJson,
            mediaJson = :mediaJson,
            isChecked = :isChecked,
            updatedAt = :updatedAt,
            deletedAt = NULL
        WHERE pageId = :pageId AND id = :blockId
        """,
    )
    suspend fun updateBlockContent(
        pageId: String,
        blockId: String,
        text: String,
        richTextJson: String,
        mediaJson: String,
        isChecked: Boolean,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE page_properties
        SET value = :value, updatedAt = :updatedAt, deletedAt = NULL
        WHERE pageId = :pageId
            AND (
                id = :propertyId
                OR (:propertyName != '' AND lower(name) = lower(:propertyName))
            )
        """,
    )
    suspend fun updatePropertyValue(
        pageId: String,
        propertyId: String,
        propertyName: String,
        value: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE page_table_cells
        SET value = :value, valueJson = '{}', updatedAt = :updatedAt, deletedAt = NULL
        WHERE rowId = :rowId AND columnId = :columnId
        """,
    )
    suspend fun updateTableCellValue(
        rowId: String,
        columnId: String,
        value: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM page_table_cells WHERE rowId IN (SELECT id FROM page_table_rows WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId))")
    suspend fun deleteCellsForPage(pageId: String)

    @Query("DELETE FROM page_table_rows WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId)")
    suspend fun deleteRowsForPage(pageId: String)

    @Query("DELETE FROM page_table_columns WHERE tableId IN (SELECT id FROM page_tables WHERE pageId = :pageId)")
    suspend fun deleteColumnsForPage(pageId: String)

    @Query("DELETE FROM page_tables WHERE pageId = :pageId")
    suspend fun deleteTablesForPage(pageId: String)

    @Query("DELETE FROM page_properties WHERE pageId = :pageId")
    suspend fun deletePropertiesForPage(pageId: String)

    @Query("DELETE FROM page_blocks WHERE pageId = :pageId")
    suspend fun deleteBlocksForPage(pageId: String)

    @Upsert
    suspend fun upsertBlocks(blocks: List<PageBlockEntity>)

    @Upsert
    suspend fun upsertProperties(properties: List<PagePropertyEntity>)

    @Upsert
    suspend fun upsertTables(tables: List<PageTableEntity>)

    @Upsert
    suspend fun upsertColumns(columns: List<PageTableColumnEntity>)

    @Upsert
    suspend fun upsertRows(rows: List<PageTableRowEntity>)

    @Upsert
    suspend fun upsertCells(cells: List<PageTableCellEntity>)

    @Transaction
    suspend fun getPageContentSnapshot(pageId: String): PageContentSnapshot {
        return PageContentSnapshot(
            blocks = getBlocks(pageId),
            properties = getProperties(pageId),
            tables = getTables(pageId),
            columns = getColumns(pageId),
            rows = getRows(pageId),
            cells = getCells(pageId),
        )
    }

    @Transaction
    suspend fun replacePageContentProjection(
        pageId: String,
        blocks: List<PageBlockEntity>,
        properties: List<PagePropertyEntity>,
        tables: List<PageTableEntity>,
        columns: List<PageTableColumnEntity>,
        rows: List<PageTableRowEntity>,
        cells: List<PageTableCellEntity>,
    ) {
        deleteCellsForPage(pageId)
        deleteRowsForPage(pageId)
        deleteColumnsForPage(pageId)
        deleteTablesForPage(pageId)
        deletePropertiesForPage(pageId)
        deleteBlocksForPage(pageId)

        upsertBlocks(blocks)
        upsertProperties(properties)
        upsertTables(tables)
        upsertColumns(columns)
        upsertRows(rows)
        upsertCells(cells)
    }
}
