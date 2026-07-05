package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "page_blocks",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId", "sortOrder"]),
        Index(value = ["parentBlockId"]),
    ],
)
data class PageBlockEntity(
    val id: String,
    val pageId: String,
    val parentBlockId: String?,
    val type: String,
    val text: String = "",
    val richTextJson: String = "[]",
    val mediaJson: String = "[]",
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val metadataJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "page_properties",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId", "sortOrder"]),
    ],
)
data class PagePropertyEntity(
    val id: String,
    val pageId: String,
    val name: String,
    val type: String,
    val value: String = "",
    val sortOrder: Int = 0,
    val metadataJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "page_tables",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PageBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId"]),
        Index(value = ["blockId"]),
    ],
)
data class PageTableEntity(
    val id: String,
    val pageId: String,
    val blockId: String,
    val title: String = "",
    val view: String = "Table",
    val viewConfigJson: String = "{}",
    val sortJson: String = "{}",
    val filterJson: String = "{}",
    val groupByColumnId: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "page_table_columns",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PageTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tableId", "sortOrder"]),
    ],
)
data class PageTableColumnEntity(
    val id: String,
    val tableId: String,
    val name: String,
    val type: String,
    val sortOrder: Int = 0,
    val configJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "page_table_rows",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PageTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tableId", "sortOrder"]),
        Index(value = ["tableId", "isFavorite"]),
    ],
)
data class PageTableRowEntity(
    val id: String,
    val tableId: String,
    val sortOrder: Int = 0,
    val contentJson: String = "[]",
    val icon: String = "",
    val isFavorite: Boolean = false,
    val metadataJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "page_table_cells",
    primaryKeys = ["rowId", "columnId"],
    foreignKeys = [
        ForeignKey(
            entity = PageTableRowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PageTableColumnEntity::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["columnId"]),
    ],
)
data class PageTableCellEntity(
    val rowId: String,
    val columnId: String,
    val value: String = "",
    val valueType: String = "Text",
    val valueJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
