package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.domain.model.Page

fun PageEntity.toDomain(): Page {
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

fun Page.toEntity(): PageEntity {
    return PageEntity(
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

