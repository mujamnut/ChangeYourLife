package com.changeyourlife.cyl.data.remote.sync

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.Workspace
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceSyncDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class WorkspaceListResponseDto(
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
data class PageListResponseDto(
    val pages: List<PageSyncDto>,
)

@Serializable
data class PageBlockTextPatchRequestDto(
    val text: String,
)

@Serializable
data class PageBlockPatchRequestDto(
    val text: String? = null,
    val richTextSpans: List<PageTextSpan>? = null,
    val mediaAttachments: List<PageMediaAttachment>? = null,
    val isChecked: Boolean? = null,
)

@Serializable
data class PagePropertyValuePatchRequestDto(
    val propertyId: String = "",
    val propertyName: String = "",
    val value: String,
)

@Serializable
data class PageTableCellValuePatchRequestDto(
    val rowId: String,
    val columnId: String,
    val value: String,
)

@Serializable
data class PageTablePatchRequestDto(
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
data class PageTableColumnPatchRequestDto(
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
data class PageBlockCreateRequestDto(
    val blockId: String = "",
    val type: String = "Text",
    val text: String = "",
    val parentBlockId: String = "",
    val afterBlockId: String = "",
    val targetIndex: Int? = null,
)

@Serializable
data class PagePropertyCreateRequestDto(
    val propertyId: String = "",
    val name: String,
    val type: String = "Text",
    val value: String = "",
    val targetIndex: Int? = null,
)

@Serializable
data class PageElementPositionPatchRequestDto(
    val targetIndex: Int,
)

@Serializable
data class PageTableColumnCreateRequestDto(
    val columnId: String = "",
    val name: String,
    val type: String = "Text",
    val cellValues: Map<String, String> = emptyMap(),
    val targetIndex: Int? = null,
)

@Serializable
data class PageTableRowCreateRequestDto(
    val rowId: String = "",
    val cells: Map<String, String> = emptyMap(),
    val targetIndex: Int? = null,
)

@Serializable
data class PageTableRowPatchRequestDto(
    val blocks: List<PageBlock>? = null,
)

fun Workspace.toSyncDto(): WorkspaceSyncDto {
    return WorkspaceSyncDto(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun WorkspaceSyncDto.toDomain(): Workspace {
    return Workspace(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Page.toSyncDto(): PageSyncDto {
    return PageSyncDto(
        id = id,
        workspaceId = workspaceId,
        parentPageId = parentPageId,
        title = title,
        content = content,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

fun PageSyncDto.toDomain(): Page {
    return Page(
        id = id,
        workspaceId = workspaceId,
        parentPageId = parentPageId,
        title = title,
        content = content,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
