package com.changeyourlife.cyl.domain.model

data class TaskItem(
    val id: String,
    val workspaceId: String,
    val pageId: String?,
    val title: String,
    val notes: String,
    val isCompleted: Boolean,
    val dueAt: Long?,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

