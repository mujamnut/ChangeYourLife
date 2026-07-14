package com.changeyourlife.cyl.backend.domain

data class AiSkillRecord(
    val id: String,
    val workspaceId: String,
    val name: String,
    val whenToUse: String,
    val instructions: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

interface AiSkillSyncRepository {
    suspend fun list(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean = false,
    ): List<AiSkillRecord>

    suspend fun upsert(
        userId: String,
        skill: AiSkillRecord,
    ): AiSkillRecord?
}
