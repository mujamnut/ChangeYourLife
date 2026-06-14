package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.TaskEntity
import com.changeyourlife.cyl.domain.model.TaskItem

fun TaskEntity.toDomain(): TaskItem {
    return TaskItem(
        id = id,
        workspaceId = workspaceId,
        pageId = pageId,
        title = title,
        notes = notes,
        isCompleted = isCompleted,
        dueAt = dueAt,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

fun TaskItem.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        workspaceId = workspaceId,
        pageId = pageId,
        title = title,
        notes = notes,
        isCompleted = isCompleted,
        dueAt = dueAt,
        priority = priority,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

