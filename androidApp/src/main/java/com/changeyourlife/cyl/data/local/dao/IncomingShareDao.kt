package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.changeyourlife.cyl.data.local.entity.IncomingShareDraftEntity
import com.changeyourlife.cyl.data.local.entity.IncomingShareItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomingShareDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraft(draft: IncomingShareDraftEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<IncomingShareItemEntity>)

    @Update
    suspend fun updateItem(item: IncomingShareItemEntity)

    @Query("SELECT * FROM incoming_share_drafts WHERE id = :draftId LIMIT 1")
    suspend fun getDraft(draftId: String): IncomingShareDraftEntity?

    @Query("SELECT * FROM incoming_share_drafts WHERE eventId = :eventId LIMIT 1")
    suspend fun getDraftByEventId(eventId: String): IncomingShareDraftEntity?

    @Query("SELECT * FROM incoming_share_items WHERE draftId = :draftId ORDER BY position ASC")
    suspend fun getItems(draftId: String): List<IncomingShareItemEntity>

    @Query("SELECT * FROM incoming_share_items WHERE id = :itemId LIMIT 1")
    suspend fun getItem(itemId: String): IncomingShareItemEntity?

    @Query("SELECT * FROM incoming_share_drafts WHERE id = :draftId LIMIT 1")
    fun observeDraftEntity(draftId: String): Flow<IncomingShareDraftEntity?>

    @Query("SELECT * FROM incoming_share_items WHERE draftId = :draftId ORDER BY position ASC")
    fun observeItems(draftId: String): Flow<List<IncomingShareItemEntity>>

    @Query(
        """
        UPDATE incoming_share_drafts
        SET status = :status, errorCode = :errorCode, updatedAt = :updatedAt
        WHERE id = :draftId AND status IN (:expectedStatuses)
        """,
    )
    suspend fun transitionDraft(
        draftId: String,
        expectedStatuses: List<String>,
        status: String,
        errorCode: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM incoming_share_drafts
        WHERE status IN (:statuses) AND expiresAt > :now
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getPendingDrafts(statuses: List<String>, now: Long): List<IncomingShareDraftEntity>

    @Query("DELETE FROM incoming_share_drafts WHERE id = :draftId")
    suspend fun deleteDraft(draftId: String)

    @Query("SELECT * FROM incoming_share_drafts WHERE expiresAt <= :now ORDER BY expiresAt ASC LIMIT :limit")
    suspend fun getExpiredDrafts(now: Long, limit: Int): List<IncomingShareDraftEntity>

    @Transaction
    suspend fun insertDraftWithItems(
        draft: IncomingShareDraftEntity,
        items: List<IncomingShareItemEntity>,
    ): Boolean {
        val inserted = insertDraft(draft) != -1L
        if (inserted && items.isNotEmpty()) insertItems(items)
        return inserted
    }
}
