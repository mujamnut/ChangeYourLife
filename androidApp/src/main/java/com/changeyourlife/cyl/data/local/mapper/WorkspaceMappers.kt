package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity
import com.changeyourlife.cyl.domain.model.Workspace

fun WorkspaceEntity.toDomain(): Workspace {
    return Workspace(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Workspace.toEntity(): WorkspaceEntity {
    return WorkspaceEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

