package com.changeyourlife.cyl.backend.model.sync

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
