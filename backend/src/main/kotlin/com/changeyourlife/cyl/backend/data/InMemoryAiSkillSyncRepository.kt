package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiSkillRecord
import com.changeyourlife.cyl.backend.domain.AiSkillSyncRepository
import com.changeyourlife.cyl.backend.domain.ContentRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryAiSkillSyncRepository(
    private val contentRepository: ContentRepository,
) : AiSkillSyncRepository {
    private val skills = ConcurrentHashMap<String, AiSkillRecord>()

    override suspend fun list(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean,
    ): List<AiSkillRecord> {
        val canAccessWorkspace = contentRepository.listWorkspaces(
            userId = userId,
            includeDeleted = false,
        ).any { workspace -> workspace.id == workspaceId }
        if (!canAccessWorkspace) return emptyList()
        return skills.values
            .asSequence()
            .filter { skill -> skill.workspaceId == workspaceId }
            .filter { skill -> includeDeleted || skill.deletedAt == null }
            .sortedBy { skill -> skill.updatedAt }
            .toList()
    }

    override suspend fun upsert(userId: String, skill: AiSkillRecord): AiSkillRecord? {
        val canAccessWorkspace = contentRepository.listWorkspaces(
            userId = userId,
            includeDeleted = false,
        ).any { workspace -> workspace.id == skill.workspaceId }
        if (!canAccessWorkspace) return null

        val existing = skills[skill.id]
        if (existing != null && existing.workspaceId != skill.workspaceId) return null
        val saved = skill.copy(createdAt = existing?.createdAt ?: skill.createdAt)
        skills[skill.id] = saved
        return saved
    }
}
