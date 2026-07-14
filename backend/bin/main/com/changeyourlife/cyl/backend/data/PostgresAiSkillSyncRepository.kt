package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiSkillRecord
import com.changeyourlife.cyl.backend.domain.AiSkillSyncRepository
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresAiSkillSyncRepository(
    private val dataSource: DataSource,
) : AiSkillSyncRepository {
    override suspend fun list(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean,
    ): List<AiSkillRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT ai_skills.id,
                       ai_skills.workspace_id,
                       ai_skills.name,
                       ai_skills.when_to_use,
                       ai_skills.instructions,
                       ai_skills.is_enabled,
                       ai_skills.created_at,
                       ai_skills.updated_at,
                       ai_skills.deleted_at
                FROM ai_skills
                INNER JOIN workspaces ON workspaces.id = ai_skills.workspace_id
                WHERE workspaces.user_id = ?
                  AND workspaces.id = ?
                  AND workspaces.deleted_at IS NULL
                  AND (? OR ai_skills.deleted_at IS NULL)
                ORDER BY ai_skills.updated_at ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, workspaceId)
                statement.setBoolean(3, includeDeleted)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toAiSkillRecord())
                    }
                }
            }
        }
    }

    override suspend fun upsert(userId: String, skill: AiSkillRecord): AiSkillRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                val saved = connection.prepareStatement(
                    """
                    INSERT INTO ai_skills (
                        id,
                        workspace_id,
                        name,
                        when_to_use,
                        instructions,
                        is_enabled,
                        created_at,
                        updated_at,
                        deleted_at
                    )
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE EXISTS (
                        SELECT 1
                        FROM workspaces
                        WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                    )
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        when_to_use = EXCLUDED.when_to_use,
                        instructions = EXCLUDED.instructions,
                        is_enabled = EXCLUDED.is_enabled,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at
                    WHERE ai_skills.workspace_id = EXCLUDED.workspace_id
                      AND EXCLUDED.updated_at >= ai_skills.updated_at
                    RETURNING id,
                              workspace_id,
                              name,
                              when_to_use,
                              instructions,
                              is_enabled,
                              created_at,
                              updated_at,
                              deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.bind(skill = skill, userId = userId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toAiSkillRecord() else null
                    }
                }
                saved ?: connection.selectOwnedSkill(userId = userId, skillId = skill.id)
            }
        }
}

private fun PreparedStatement.bind(skill: AiSkillRecord, userId: String) {
    setString(1, skill.id)
    setString(2, skill.workspaceId)
    setString(3, skill.name)
    setString(4, skill.whenToUse)
    setString(5, skill.instructions)
    setBoolean(6, skill.isEnabled)
    setLong(7, skill.createdAt)
    setLong(8, skill.updatedAt)
    if (skill.deletedAt == null) setObject(9, null) else setLong(9, skill.deletedAt)
    setString(10, skill.workspaceId)
    setString(11, userId)
}

private fun java.sql.Connection.selectOwnedSkill(userId: String, skillId: String): AiSkillRecord? {
    return prepareStatement(
        """
        SELECT ai_skills.id,
               ai_skills.workspace_id,
               ai_skills.name,
               ai_skills.when_to_use,
               ai_skills.instructions,
               ai_skills.is_enabled,
               ai_skills.created_at,
               ai_skills.updated_at,
               ai_skills.deleted_at
        FROM ai_skills
        INNER JOIN workspaces ON workspaces.id = ai_skills.workspace_id
        WHERE workspaces.user_id = ? AND ai_skills.id = ?
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, userId)
        statement.setString(2, skillId)
        statement.executeQuery().use { resultSet ->
            if (resultSet.next()) resultSet.toAiSkillRecord() else null
        }
    }
}

private fun ResultSet.toAiSkillRecord(): AiSkillRecord {
    val deletedAtValue = getLong("deleted_at")
    val deletedAt = if (wasNull()) null else deletedAtValue
    return AiSkillRecord(
        id = getString("id"),
        workspaceId = getString("workspace_id"),
        name = getString("name"),
        whenToUse = getString("when_to_use"),
        instructions = getString("instructions"),
        isEnabled = getBoolean("is_enabled"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = deletedAt,
    )
}
