package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.changeyourlife.cyl.data.local.entity.AiAppliedActionEntity

@Dao
interface AiAppliedActionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(action: AiAppliedActionEntity): Long

    @Query("SELECT * FROM applied_ai_actions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getByIdempotencyKey(idempotencyKey: String): AiAppliedActionEntity?

    @Query(
        """
        SELECT * FROM applied_ai_actions
        WHERE requestMessageId = :requestMessageId AND actionIndex = :actionIndex
        LIMIT 1
        """,
    )
    suspend fun getByRequestAction(requestMessageId: String, actionIndex: Int): AiAppliedActionEntity?

    @Query(
        """
        UPDATE applied_ai_actions
        SET state = :newState, failure = :failure, updatedAt = :updatedAt
        WHERE idempotencyKey = :idempotencyKey AND state = :claimedState
        """,
    )
    suspend fun transitionClaimed(
        idempotencyKey: String,
        newState: String,
        failure: String,
        updatedAt: Long,
        claimedState: String,
    ): Int
}
