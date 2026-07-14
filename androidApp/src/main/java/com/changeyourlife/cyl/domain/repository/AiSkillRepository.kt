package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.AiSkill
import kotlinx.coroutines.flow.Flow

interface AiSkillRepository {
    fun observeSkills(workspaceId: String): Flow<List<AiSkill>>
    suspend fun upsertSkill(skill: AiSkill)
    suspend fun deleteSkill(workspaceId: String, skillId: String)
    suspend fun setSkillEnabled(workspaceId: String, skillId: String, enabled: Boolean)
}
