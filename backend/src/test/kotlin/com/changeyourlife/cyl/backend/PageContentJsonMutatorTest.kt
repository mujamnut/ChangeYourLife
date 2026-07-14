package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.data.PageContentJsonMutator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class PageContentJsonMutatorTest {
    @Test
    fun updateTableColumnClearsStaleRelationRowsAndDependentRollupTarget() {
        val updated = PageContentJsonMutator.updateTableColumn(
            content = relationContent,
            tableBlockId = "table-1",
            columnId = "project",
            name = null,
            type = null,
            config = null,
            dateFormat = null,
            timeFormat = null,
            dateReminder = null,
            timezoneLabel = null,
            formula = null,
            relationTargetTableId = "new-target",
            rollupRelationColumnId = null,
            rollupTargetColumnId = null,
            rollupAggregation = null,
        )

        val table = requireNotNull(updated).tableObject()
        val projectColumn = table.columns().single { column -> column.stringValue("id") == "project" }
        val rollupColumn = table.columns().single { column -> column.stringValue("id") == "total" }
        val row = table.rows().single()
        val cells = row["cells"]!!.jsonObject
        val cellValues = row["cellValues"]!!.jsonObject

        assertEquals("new-target", projectColumn.stringValue("relationTargetTableId"))
        assertEquals("", rollupColumn.stringValue("rollupTargetColumnId"))
        assertEquals("", cells.stringValue("project"))
        assertEquals(emptyList<String>(), cellValues["project"]!!.jsonObject["relationRowIds"]!!.jsonArray.map { it.toString() })
    }

    @Test
    fun updateTableColumnRejectsRollupWhenRelationDependencyIsInvalid() {
        val error = assertFailsWith<IllegalArgumentException> {
            PageContentJsonMutator.updateTableColumn(
                content = relationContent,
                tableBlockId = "table-1",
                columnId = "total",
                name = null,
                type = null,
                config = null,
                dateFormat = null,
                timeFormat = null,
                dateReminder = null,
                timezoneLabel = null,
                formula = null,
                relationTargetTableId = null,
                rollupRelationColumnId = "name",
                rollupTargetColumnId = "amount",
                rollupAggregation = null,
            )
        }

        assertEquals("Rollup relation property must be a Relation property.", error.message)
    }

    @Test
    fun updateTableColumnRejectsFormulaPatchForNonFormulaColumn() {
        val error = assertFailsWith<IllegalArgumentException> {
            PageContentJsonMutator.updateTableColumn(
                content = relationContent,
                tableBlockId = "table-1",
                columnId = "name",
                name = null,
                type = null,
                config = null,
                dateFormat = null,
                timeFormat = null,
                dateReminder = null,
                timezoneLabel = null,
                formula = "{Amount} * 2",
                relationTargetTableId = null,
                rollupRelationColumnId = null,
                rollupTargetColumnId = null,
                rollupAggregation = null,
            )
        }

        assertEquals("Formula can only be set on Formula properties.", error.message)
    }

    @Test
    fun updateTableColumnRejectsRelationTargetPatchForNonRelationColumn() {
        val error = assertFailsWith<IllegalArgumentException> {
            PageContentJsonMutator.updateTableColumn(
                content = relationContent,
                tableBlockId = "table-1",
                columnId = "name",
                name = null,
                type = null,
                config = null,
                dateFormat = null,
                timeFormat = null,
                dateReminder = null,
                timezoneLabel = null,
                formula = null,
                relationTargetTableId = "other-table",
                rollupRelationColumnId = null,
                rollupTargetColumnId = null,
                rollupAggregation = null,
            )
        }

        assertEquals("Relation target can only be set on Relation properties.", error.message)
    }

    @Test
    fun addTableRowGeneratesNewIdWhenRequestedIdAlreadyExists() {
        val updated = PageContentJsonMutator.addTableRow(
            content = relationContent,
            tableBlockId = "table-1",
            rowId = "row-1",
            cells = mapOf("name" to "Second"),
            cellValues = emptyMap(),
            metadata = JsonObject(emptyMap()),
            targetIndex = null,
        )

        val rows = requireNotNull(updated).tableObject().rows()
        assertEquals(2, rows.size)
        assertEquals("row-1", rows.first().stringValue("id"))
        assertNotEquals("row-1", rows.last().stringValue("id"))
    }

    @Test
    fun deleteTableColumnRejectsPrimaryColumn() {
        val updated = PageContentJsonMutator.deleteTableColumn(
            content = relationContent,
            tableBlockId = "table-1",
            columnId = "name",
        )

        assertEquals(null, updated)
    }

    @Test
    fun moveTableColumnRejectsPrimaryColumnAndPrimarySlot() {
        val movePrimary = PageContentJsonMutator.moveTableColumn(
            content = relationContent,
            tableBlockId = "table-1",
            columnId = "name",
            targetIndex = 2,
        )
        val moveIntoPrimarySlot = PageContentJsonMutator.moveTableColumn(
            content = relationContent,
            tableBlockId = "table-1",
            columnId = "project",
            targetIndex = 0,
        )

        assertEquals(null, movePrimary)
        assertEquals(null, moveIntoPrimarySlot)
    }

    @Test
    fun updateTableClearsSortFilterAndGroupWhenPatchValuesAreBlank() {
        val content = relationContent.replace(
            "\"rows\": [",
            """
            "sort": { "columnId": "name", "direction": "Descending" },
                "filter": { "columnId": "name", "query": "food", "operator": "Contains" },
                "groupByColumnId": "project",
                "rows": [
            """.trimIndent(),
        )

        val updated = PageContentJsonMutator.updateTable(
            content = content,
            tableBlockId = "table-1",
            title = null,
            view = null,
            calendarDateColumnId = null,
            timelineStartColumnId = null,
            timelineEndColumnId = null,
            dashboardMetricColumnId = null,
            dashboardGroupColumnId = null,
            sortColumnId = "",
            sortDirection = "Ascending",
            filterColumnId = "",
            filterQuery = "",
            filterOperator = "Contains",
            groupByColumnId = "",
        )

        val table = requireNotNull(updated).tableObject()

        assertEquals("", table.stringValue("groupByColumnId"))
        assertEquals(emptySet(), table["sort"]!!.jsonObject.keys)
        assertEquals(emptySet(), table["filter"]!!.jsonObject.keys)
    }

    @Test
    fun updateTableClearsFilterWhenColumnIdIsBlankWithoutQueryPatch() {
        val content = relationContent.replace(
            "\"rows\": [",
            """
            "filter": { "columnId": "name", "query": "food", "operator": "Contains" },
                "rows": [
            """.trimIndent(),
        )

        val updated = PageContentJsonMutator.updateTable(
            content = content,
            tableBlockId = "table-1",
            title = null,
            view = null,
            calendarDateColumnId = null,
            timelineStartColumnId = null,
            timelineEndColumnId = null,
            dashboardMetricColumnId = null,
            dashboardGroupColumnId = null,
            sortColumnId = null,
            sortDirection = null,
            filterColumnId = "",
            filterQuery = null,
            filterOperator = null,
            groupByColumnId = null,
        )

        val table = requireNotNull(updated).tableObject()

        assertEquals(emptySet(), table["filter"]!!.jsonObject.keys)
    }

    private val relationContent = """
        {
          "blocks": [
            {
              "id": "table-1",
              "type": "DatabaseTable",
              "table": {
                "columns": [
                  { "id": "name", "name": "Name", "type": "Text" },
                  { "id": "project", "name": "Project", "type": "Relation", "relationTargetTableId": "old-target" },
                  { "id": "total", "name": "Total", "type": "Rollup", "rollupRelationColumnId": "project", "rollupTargetColumnId": "amount" }
                ],
                "rows": [
                  {
                    "id": "row-1",
                    "cells": { "name": "First", "project": "old-row" },
                    "cellValues": {
                      "name": { "type": "Text", "text": "First" },
                      "project": { "type": "Relation", "relationRowIds": ["old-row"] }
                    }
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    private fun String.tableObject(): JsonObject {
        return Json.parseToJsonElement(this)
            .jsonObject["blocks"]!!
            .jsonArray.first()
            .jsonObject["table"]!!
            .jsonObject
    }

    private fun JsonObject.columns(): List<JsonObject> {
        return (this["columns"] as? JsonArray).orEmpty().map { element -> element.jsonObject }
    }

    private fun JsonObject.rows(): List<JsonObject> {
        return (this["rows"] as? JsonArray).orEmpty().map { element -> element.jsonObject }
    }

    private fun JsonObject.stringValue(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
