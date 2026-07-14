package com.changeyourlife.cyl.data.repository

import android.content.Context
import com.changeyourlife.cyl.data.local.dao.AiSkillDao
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.data.sync.BackgroundSyncQueue
import com.changeyourlife.cyl.domain.model.AiSkill
import com.changeyourlife.cyl.domain.repository.AiSkillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Singleton
class AiSkillRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val aiSkillDao: AiSkillDao,
    private val backgroundSyncQueue: BackgroundSyncQueue,
    private val json: Json,
) : AiSkillRepository {
    private val legacyPreferences = context.getSharedPreferences(LegacyPreferencesName, Context.MODE_PRIVATE)
    private val legacyMigrationMutex = Mutex()

    override fun observeSkills(workspaceId: String): Flow<List<AiSkill>> = flow {
        migrateLegacySkillsIfNeeded()
        emitAll(
            aiSkillDao.observeActiveByWorkspace(workspaceId)
                .map { entities -> entities.map { entity -> entity.toDomain() } },
        )
    }

    override suspend fun upsertSkill(skill: AiSkill) {
        migrateLegacySkillsIfNeeded()
        val existing = aiSkillDao.getByIdIncludingDeleted(skill.id)
        require(existing == null || existing.workspaceId == skill.workspaceId) {
            "AI skill belongs to another workspace."
        }
        aiSkillDao.upsert(
            skill.copy(createdAt = existing?.createdAt ?: skill.createdAt)
                .toEntity(
                    syncStatus = SyncStatus.PendingPush,
                    remoteUpdatedAt = existing?.remoteUpdatedAt ?: 0L,
                    lastSyncedAt = existing?.lastSyncedAt ?: 0L,
                ),
        )
        backgroundSyncQueue.enqueuePendingPushDebounced()
    }

    override suspend fun deleteSkill(workspaceId: String, skillId: String) {
        migrateLegacySkillsIfNeeded()
        val changed = aiSkillDao.softDelete(
            workspaceId = workspaceId,
            skillId = skillId,
            deletedAt = System.currentTimeMillis(),
        )
        if (changed > 0) backgroundSyncQueue.enqueuePendingPushDebounced()
    }

    override suspend fun setSkillEnabled(workspaceId: String, skillId: String, enabled: Boolean) {
        migrateLegacySkillsIfNeeded()
        val changed = aiSkillDao.setEnabled(
            workspaceId = workspaceId,
            skillId = skillId,
            enabled = enabled,
            updatedAt = System.currentTimeMillis(),
        )
        if (changed > 0) backgroundSyncQueue.enqueuePendingPushDebounced()
    }

    private suspend fun migrateLegacySkillsIfNeeded() {
        if (legacyPreferences.getBoolean(LegacyMigrationKey, false)) return
        legacyMigrationMutex.withLock {
            if (legacyPreferences.getBoolean(LegacyMigrationKey, false)) return
            val encoded = legacyPreferences.getString(LegacySkillsKey, null)
            val records = encoded
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value ->
                    runCatching {
                        json.decodeFromString<List<LegacyAiSkillRecord>>(value)
                    }.getOrDefault(emptyList())
                }
                .orEmpty()

            val importable = records.mapNotNull { record ->
                if (
                    record.id.isBlank() ||
                    record.workspaceId.isBlank() ||
                    record.name.isBlank() ||
                    record.whenToUse.isBlank() ||
                    record.instructions.isBlank()
                ) {
                    null
                } else {
                    val existing = aiSkillDao.getByIdIncludingDeleted(record.id)
                    if (existing != null) {
                        null
                    } else {
                        AiSkill(
                            id = record.id,
                            workspaceId = record.workspaceId,
                            name = record.name,
                            whenToUse = record.whenToUse,
                            instructions = record.instructions,
                            isEnabled = record.isEnabled,
                            createdAt = record.createdAt,
                            updatedAt = record.updatedAt,
                        ).toEntity(syncStatus = SyncStatus.PendingPush)
                    }
                }
            }
            if (importable.isNotEmpty()) aiSkillDao.upsertAll(importable)
            val committed = withContext(Dispatchers.IO) {
                legacyPreferences.edit()
                    .remove(LegacySkillsKey)
                    .putBoolean(LegacyMigrationKey, true)
                    .commit()
            }
            check(committed) { "Unable to finish AI skill migration." }
            if (importable.isNotEmpty()) backgroundSyncQueue.enqueuePendingPushDebounced()
        }
    }

    private companion object {
        private const val LegacyPreferencesName = "ai_skills"
        private const val LegacySkillsKey = "workspace_skills_json"
        private const val LegacyMigrationKey = "room_migration_complete_v1"
    }
}

@Serializable
private data class LegacyAiSkillRecord(
    val id: String,
    val workspaceId: String,
    val name: String,
    val whenToUse: String,
    val instructions: String,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
