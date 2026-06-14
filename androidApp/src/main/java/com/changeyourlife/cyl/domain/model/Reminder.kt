package com.changeyourlife.cyl.domain.model

data class Reminder(
    val id: String,
    val workspaceId: String,
    val pageId: String?,
    val taskId: String?,
    val title: String,
    val remindAt: Long,
    val isDone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

