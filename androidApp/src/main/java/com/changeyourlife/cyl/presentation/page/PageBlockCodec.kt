package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow

object PageBlockCodec {
    fun decode(content: String): List<PageBlock> {
        return PageContentCodec.decode(content)
    }

    fun decodeDocument(content: String): PageBlockDocument {
        return PageContentCodec.decodeDocument(content)
    }

    fun encode(blocks: List<PageBlock>): String {
        return PageContentCodec.encode(blocks)
    }

    fun encodeDocument(document: PageBlockDocument): String {
        return PageContentCodec.encodeDocument(document)
    }

    fun newBlock(type: PageBlockType): PageBlock {
        return PageContentCodec.newBlock(type)
    }

    fun newProperty(
        type: PagePropertyType,
        name: String,
    ): PageProperty {
        return PageContentCodec.newProperty(type, name)
    }

    fun newTableColumn(
        name: String,
        type: PageTableColumnType = PageTableColumnType.Text,
    ): PageTableColumn {
        return PageContentCodec.newTableColumn(name, type)
    }

    fun newTableRow(columns: List<PageTableColumn>): PageTableRow {
        return PageContentCodec.newTableRow(columns)
    }
}
