package com.changeyourlife.cyl.data.local.model

import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.PageTableCellEntity
import com.changeyourlife.cyl.data.local.entity.PageTableColumnEntity
import com.changeyourlife.cyl.data.local.entity.PageTableEntity
import com.changeyourlife.cyl.data.local.entity.PageTableRowEntity

data class PageContentSnapshot(
    val blocks: List<PageBlockEntity>,
    val properties: List<PagePropertyEntity>,
    val tables: List<PageTableEntity>,
    val columns: List<PageTableColumnEntity>,
    val rows: List<PageTableRowEntity>,
    val cells: List<PageTableCellEntity>,
)
