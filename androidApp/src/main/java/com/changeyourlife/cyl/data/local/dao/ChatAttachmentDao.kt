package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.ChatAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatAttachmentDao {
    @Query(
        """
        SELECT * FROM chat_attachments
        WHERE sessionId = :sessionId AND status != :deletedStatus
        ORDER BY createdAt ASC
        """,
    )
    fun observeBySession(
        sessionId: String,
        deletedStatus: String = "deleted",
    ): Flow<List<ChatAttachmentEntity>>

    @Query(
        """
        SELECT * FROM chat_attachments
        WHERE messageId = :messageId AND status != :deletedStatus
        ORDER BY createdAt ASC
        """,
    )
    fun observeByMessage(
        messageId: String,
        deletedStatus: String = "deleted",
    ): Flow<List<ChatAttachmentEntity>>

    @Query("SELECT * FROM chat_attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun getById(attachmentId: String): ChatAttachmentEntity?

    @Query(
        """
        SELECT * FROM chat_attachments
        WHERE messageId IS NOT NULL
          AND localPath IS NOT NULL
          AND status IN (:statuses)
        ORDER BY updatedAt ASC
        """,
    )
    suspend fun getPendingUploads(statuses: List<String>): List<ChatAttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: ChatAttachmentEntity)

    @Query(
        """
        UPDATE chat_attachments
        SET messageId = :messageId, sessionId = :sessionId, updatedAt = :updatedAt
        WHERE id = :attachmentId
        """,
    )
    suspend fun linkToMessage(
        attachmentId: String,
        messageId: String,
        sessionId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE chat_attachments
        SET status = :status,
            progressPercent = :progressPercent,
            errorCode = :errorCode,
            updatedAt = :updatedAt
        WHERE id = :attachmentId
        """,
    )
    suspend fun updateStatus(
        attachmentId: String,
        status: String,
        progressPercent: Int,
        errorCode: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE chat_attachments
        SET status = :nextStatus,
            progressPercent = :progressPercent,
            errorCode = :errorCode,
            sha256 = CASE WHEN :sha256 IS NULL THEN sha256 ELSE :sha256 END,
            remoteAssetId = CASE
                WHEN :remoteAssetId IS NULL THEN remoteAssetId
                ELSE :remoteAssetId
            END,
            updatedAt = :updatedAt
        WHERE id = :attachmentId
          AND status IN (:expectedStatuses)
        """,
    )
    suspend fun transitionUploadState(
        attachmentId: String,
        expectedStatuses: List<String>,
        nextStatus: String,
        progressPercent: Int,
        errorCode: String?,
        sha256: String?,
        remoteAssetId: String?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM chat_attachments WHERE id = :attachmentId")
    suspend fun deleteById(attachmentId: String)
}
