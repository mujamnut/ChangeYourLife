package com.changeyourlife.cyl.backend.model.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class WorkspaceSyncDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class WorkspaceListResponse(
    val workspaces: List<WorkspaceSyncDto>,
)

@Serializable
data class PageSyncDto(
    val id: String,
    val workspaceId: String,
    val parentPageId: String? = null,
    val title: String,
    val content: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PageListResponse(
    val pages: List<PageSyncDto>,
)

@Serializable
data class PageBlockTextPatchRequest(
    val text: String,
)

@Serializable
data class PageBlockPatchRequest(
    val text: String? = null,
    val richTextSpans: JsonArray? = null,
    val mediaAttachments: JsonArray? = null,
    val isChecked: Boolean? = null,
)

@Serializable
data class PagePropertyValuePatchRequest(
    val propertyId: String = "",
    val propertyName: String = "",
    val value: String,
)

@Serializable
data class PageTableCellValuePatchRequest(
    val rowId: String,
    val columnId: String,
    val value: String,
)

@Serializable
data class PageTablePatchRequest(
    val title: String? = null,
    val view: String? = null,
    val calendarDateColumnId: String? = null,
    val timelineStartColumnId: String? = null,
    val timelineEndColumnId: String? = null,
    val dashboardMetricColumnId: String? = null,
    val dashboardGroupColumnId: String? = null,
    val sortColumnId: String? = null,
    val sortDirection: String? = null,
    val filterColumnId: String? = null,
    val filterQuery: String? = null,
    val groupByColumnId: String? = null,
)

@Serializable
data class PageTableColumnPatchRequest(
    val name: String? = null,
    val type: String? = null,
    val dateFormat: String? = null,
    val timeFormat: String? = null,
    val dateReminder: String? = null,
    val timezoneLabel: String? = null,
    val formula: String? = null,
    val relationTargetTableId: String? = null,
    val rollupRelationColumnId: String? = null,
    val rollupTargetColumnId: String? = null,
    val rollupAggregation: String? = null,
)

@Serializable
data class PageBlockCreateRequest(
    val blockId: String = "",
    val type: String = "Text",
    val text: String = "",
    val parentBlockId: String = "",
    val afterBlockId: String = "",
    val targetIndex: Int? = null,
)

@Serializable
data class PagePropertyCreateRequest(
    val propertyId: String = "",
    val name: String,
    val type: String = "Text",
    val value: String = "",
    val targetIndex: Int? = null,
)

@Serializable
data class PageElementPositionPatchRequest(
    val targetIndex: Int,
)

@Serializable
data class PageTableColumnCreateRequest(
    val columnId: String = "",
    val name: String,
    val type: String = "Text",
    val cellValues: Map<String, String> = emptyMap(),
    val targetIndex: Int? = null,
)

@Serializable
data class PageTableRowCreateRequest(
    val rowId: String = "",
    val cells: Map<String, String> = emptyMap(),
    val targetIndex: Int? = null,
)

@Serializable
data class PageTableRowPatchRequest(
    val blocks: JsonArray? = null,
)
