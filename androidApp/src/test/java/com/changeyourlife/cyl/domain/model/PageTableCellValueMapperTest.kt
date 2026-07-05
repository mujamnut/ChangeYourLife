package com.changeyourlife.cyl.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTableCellValueMapperTest {
    @Test
    fun filesMediaDisplayValueRoundTripsTypedAttachments() {
        val attachments = listOf(
            PageMediaAttachment(
                id = "file-1",
                uri = "content://file-1",
                name = "receipt.pdf",
                mimeType = "application/pdf",
                sizeBytes = 42,
            ),
        )
        val rawValue = json.encodeToString(attachments)

        val typedValue = rawValue.toTypedCellValue(PageTableColumnType.FilesMedia)

        assertEquals(PageTableColumnType.FilesMedia, typedValue.type)
        assertEquals(attachments, typedValue.files)
        assertEquals(rawValue, typedValue.displayValue())
    }

    @Test
    fun multiSelectDisplayValueNormalizesCommaSeparatedChoices() {
        val typedValue = " Food, fuel, food ,  Bills ".toTypedCellValue(PageTableColumnType.MultiSelect)

        assertEquals(PageTableColumnType.MultiSelect, typedValue.type)
        assertEquals("Food, fuel, Bills", typedValue.text)
        assertEquals("Food, fuel, Bills", typedValue.displayValue())
    }

    private companion object {
        val json = Json {
            encodeDefaults = true
        }
    }
}
