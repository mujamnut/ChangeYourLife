package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.ReminderEntity
import com.changeyourlife.cyl.domain.model.Reminder

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        workspaceId = workspaceId,
        pageId = pageId,
        taskId = taskId,
        title = title,
        remindAt = remindAt,
        isDone = isDone,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        workspaceId = workspaceId,
        pageId = pageId,
        taskId = taskId,
        title = title,
        remindAt = remindAt,
        isDone = isDone,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

