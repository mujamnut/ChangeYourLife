package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL AND isCompleted = 0
        ORDER BY
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            priority DESC,
            updatedAt DESC
        """,
    )
    fun observeOpenTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND isCompleted = 0
        ORDER BY
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            priority DESC,
            updatedAt DESC
        """,
    )
    fun observeOpenTasks(workspaceId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId AND deletedAt IS NULL LIMIT 1")
    fun observeTask(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId AND deletedAt IS NULL LIMIT 1")
    suspend fun getTask(taskId: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND isCompleted = 0")
    fun observeOpenTaskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND isCompleted = 0")
    fun observeOpenTaskCount(workspaceId: String): Flow<Int>

    @Upsert
    suspend fun upsertTask(task: TaskEntity)
}
