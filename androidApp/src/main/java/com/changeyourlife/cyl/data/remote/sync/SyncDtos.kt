package com.changeyourlife.cyl.data.remote.sync

import com.changeyourlife.cyl.domain.model.Page
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
