package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.data.PageContentProjectionParser
import com.changeyourlife.cyl.backend.data.PageProjectionSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PageContentProjectionParserTest {
    @Test
    fun projectsNestedBlocksPropertiesAndTypedTableCells() {
        val projection = PageContentProjectionParser.parse(
            PageProjectionSource(
                pageId = "page-budget",
                content = """
                    {
                      "version": 1,
                      "properties": [
                        {
                          "id": "property-owner",
                          "name": "Owner",
                          "type": "Person",
                          "value": "Kumar"
                        }
                      ],
                      "blocks": [
                        {
                          "id": "heading-1",
                          "type": "Heading",
                          "text": "July budget",
                          "children": [
                            {
                              "id": "note-1",
                              "type": "Text",
                              "text": "Keep fuel receipts"
                            }
                          ]
                        },
                        {
                          "id": "table-1",
                          "type": "DatabaseTable",
                          "table": {
                            "title": "Transactions",
                            "view": "Table",
                            "columns": [
                              {
                                "id": "column-name",
                                "name": "Name",
                                "type": "Text"
                              },
                              {
                                "id": "column-amount",
                                "name": "Amount",
                                "type": "Number"
                              },
                              {
                                "id": "column-paid",
                                "name": "Paid",
                                "type": "Checkbox"
                              }
                            ],
                            "rows": [
                              {
                                "id": "row-fuel",
                                "cells": {
                                  "column-name": "Fuel",
                                  "column-amount": "5",
                                  "column-paid": ""
                                },
                                "cellValues": {
                                  "column-name": {
                                    "type": "Text",
                                    "text": "Fuel"
                                  },
                                  "column-amount": {
                                    "type": "Number",
                                    "number": "5"
                                  },
                                  "column-paid": {
                                    "type": "Checkbox",
                                    "checked": true
                                  }
                                },
                                "metadata": {
                                  "icon": "car",
                                  "isFavorite": true,
                                  "createdBy": "Kumar"
                                },
                                "blocks": [
                                  {
                                    "id": "row-note",
                                    "type": "Text",
                                    "text": "Motorcycle fuel"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                createdAt = 100,
                updatedAt = 200,
                deletedAt = null,
            ),
        )

        assertEquals(listOf("heading-1", "note-1", "table-1"), projection.blocks.map { it.id })
        assertEquals("heading-1", projection.blocks[1].parentBlockId)
        assertEquals("Owner", projection.properties.single().name)
        assertEquals("Transactions", projection.tables.single().title)
        assertEquals(
            listOf("Name", "Amount", "Paid"),
            projection.columns.map { column -> column.name },
        )
        assertEquals("car", projection.rows.single().icon)
        assertEquals(true, projection.rows.single().isFavorite)
        assertEquals(
            mapOf(
                "column-name" to "Fuel",
                "column-amount" to "5",
                "column-paid" to "true",
            ),
            projection.cells.associate { cell -> cell.columnId to cell.value },
        )
    }

    @Test
    fun projectsLegacyPlainTextIntoDeterministicBlocks() {
        val projection = PageContentProjectionParser.parse(
            PageProjectionSource(
                pageId = "legacy-page",
                content = "First line\nSecond line",
                createdAt = 100,
                updatedAt = 200,
                deletedAt = null,
            ),
        )

        assertEquals(
            listOf("legacy-page:legacy-block:0", "legacy-page:legacy-block:1"),
            projection.blocks.map { block -> block.id },
        )
        assertEquals(listOf("First line", "Second line"), projection.blocks.map { block -> block.text })
    }
}
