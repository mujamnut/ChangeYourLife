package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.AiSkillEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.domain.model.AiSkill

fun AiSkillEntity.toDomain(): AiSkill = AiSkill(
    id = id,
    workspaceId = workspaceId,
    name = name,
    whenToUse = whenToUse,
    instructions = instructions,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiSkill.toEntity(
    deletedAt: Long? = null,
    syncStatus: String = SyncStatus.PendingPush,
    remoteUpdatedAt: Long = 0L,
    lastSyncedAt: Long = 0L,
): AiSkillEntity = AiSkillEntity(
    id = id,
    workspaceId = workspaceId,
    name = name,
    whenToUse = whenToUse,
    instructions = instructions,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    remoteUpdatedAt = remoteUpdatedAt,
    lastSyncedAt = lastSyncedAt,
)
