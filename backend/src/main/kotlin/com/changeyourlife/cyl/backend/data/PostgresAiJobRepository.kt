package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobPhases
import com.changeyourlife.cyl.backend.domain.AiJobRepository
import com.changeyourlife.cyl.backend.domain.AiJobStatus
import com.changeyourlife.cyl.backend.model.ai.AiDiagnostics
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PostgresAiJobRepository(
    private val dataSource: DataSource,
    private val json: Json = AiJobJson,
) : AiJobRepository {
    override suspend fun upsert(job: AiChatActionsJob): AiChatActionsJob = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO ai_jobs (
                    job_id,
                    user_id,
                    status,
                    phase,
                    diagnostics_json,
                    result_json,
                    error,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, job_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    phase = EXCLUDED.phase,
                    diagnostics_json = EXCLUDED.diagnostics_json,
                    result_json = EXCLUDED.result_json,
                    error = EXCLUDED.error,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            ).use { statement ->
                statement.bind(job, json)
                statement.executeUpdate()
            }
        }
        job
    }

    override suspend fun get(ownerId: String, jobId: String): AiChatActionsJob? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT job_id,
                       user_id,
                       status,
                       phase,
                       diagnostics_json,
                       result_json,
                       error,
                       created_at,
                       updated_at
                FROM ai_jobs
                WHERE user_id = ? AND job_id = ?
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, ownerId)
                statement.setString(2, jobId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toAiChatActionsJob(json) else null
                }
            }
        }
    }

    override suspend fun markActiveJobsInterrupted(now: Long, message: String): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val activeJobs = connection.prepareStatement(
                """
                SELECT job_id,
                       user_id,
                       status,
                       phase,
                       diagnostics_json,
                       result_json,
                       error,
                       created_at,
                       updated_at
                FROM ai_jobs
                WHERE status IN (?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, AiJobStatus.Queued.wireValue)
                statement.setString(2, AiJobStatus.Running.wireValue)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toAiChatActionsJob(json))
                    }
                }
            }

            activeJobs.forEach { job ->
                connection.prepareStatement(
                    """
                    UPDATE ai_jobs
                    SET status = ?,
                        phase = ?,
                        diagnostics_json = ?,
                        error = ?,
                        updated_at = ?
                    WHERE user_id = ? AND job_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, AiJobStatus.Failed.wireValue)
                    statement.setString(2, AiJobPhases.Failed)
                    statement.setString(
                        3,
                        json.encodeToString(
                            job.diagnostics.copy(
                                phase = AiJobPhases.Failed,
                                warning = message,
                            ),
                        ),
                    )
                    statement.setString(4, message)
                    statement.setLong(5, now)
                    statement.setString(6, job.ownerId)
                    statement.setString(7, job.jobId)
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun cleanup(now: Long, ttlMillis: Long, maxRetainedJobs: Int): Unit = withContext(Dispatchers.IO) {
        val expiresBefore = now - ttlMillis
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM ai_jobs
                WHERE created_at < ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, expiresBefore)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                """
                WITH ranked_jobs AS (
                    SELECT user_id,
                           job_id,
                           ROW_NUMBER() OVER (ORDER BY created_at DESC) AS row_number
                    FROM ai_jobs
                )
                DELETE FROM ai_jobs
                USING ranked_jobs
                WHERE ai_jobs.user_id = ranked_jobs.user_id
                  AND ai_jobs.job_id = ranked_jobs.job_id
                  AND ranked_jobs.row_number > ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, maxRetainedJobs)
                statement.executeUpdate()
            }
        }
    }
}

private fun PreparedStatement.bind(job: AiChatActionsJob, json: Json) {
    setString(1, job.jobId)
    setString(2, job.ownerId)
    setString(3, job.status.wireValue)
    setString(4, job.phase)
    setString(5, json.encodeToString(job.diagnostics))
    setString(6, job.result?.let { result -> json.encodeToString(result) })
    setString(7, job.error)
    setLong(8, job.createdAtEpochMillis)
    setLong(9, job.updatedAtEpochMillis)
}

private fun ResultSet.toAiChatActionsJob(json: Json): AiChatActionsJob {
    val diagnosticsJson = getString("diagnostics_json").orEmpty()
    val resultJson = getString("result_json")
    return AiChatActionsJob(
        jobId = getString("job_id"),
        ownerId = getString("user_id"),
        status = getString("status").toAiJobStatus(),
        phase = getString("phase"),
        diagnostics = diagnosticsJson
            .takeIf { value -> value.isNotBlank() }
            ?.let { value -> json.decodeFromString<AiDiagnostics>(value) }
            ?: AiDiagnostics(),
        result = resultJson
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { value -> json.decodeFromString<ChatWithActionsResponse>(value) },
        error = getString("error"),
        createdAtEpochMillis = getLong("created_at"),
        updatedAtEpochMillis = getLong("updated_at"),
    )
}

private fun String.toAiJobStatus(): AiJobStatus =
    AiJobStatus.entries.firstOrNull { status -> status.wireValue == lowercase() }
        ?: AiJobStatus.Failed

private val AiJobJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
