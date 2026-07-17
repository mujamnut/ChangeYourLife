package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity

@Dao
interface SearchIndexDao {
    @Query("SELECT COUNT(*) FROM search_index WHERE workspaceId = :workspaceId")
    suspend fun countForWorkspace(workspaceId: String): Int

    @Query("SELECT COUNT(*) FROM search_index WHERE workspaceId = :workspaceId AND targetType = :targetType")
    suspend fun countForWorkspaceAndType(workspaceId: String, targetType: String): Int

    @Query(
        """
        SELECT * FROM search_index
        WHERE workspaceId = :workspaceId
            AND targetType IN (:targetTypes)
            AND (:includeDeleted = 1 OR deletedAt IS NULL)
            AND (
                :normalizedQuery = ''
                OR normalizedText LIKE '%' || :normalizedQuery || '%'
            )
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(
        workspaceId: String,
        targetTypes: List<String>,
        normalizedQuery: String,
        includeDeleted: Boolean,
        limit: Int,
    ): List<SearchIndexEntity>

    @Query("DELETE FROM search_index WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: String)

    @Query("DELETE FROM search_index WHERE chatSessionId = :sessionId")
    suspend fun deleteForChatSession(sessionId: String)

    @Query(
        """
        DELETE FROM search_index
        WHERE pageId IN (
            SELECT id FROM pages WHERE id = :pageId OR parentPageId = :pageId
        )
        """,
    )
    suspend fun deleteForPageTree(pageId: String)

    @Query(
        """
        UPDATE search_index
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE pageId IN (
            SELECT id FROM pages WHERE id = :pageId OR parentPageId = :pageId
        )
        """,
    )
    suspend fun markPageTreeDeleted(pageId: String, deletedAt: Long)

    @Query(
        """
        UPDATE search_index
        SET deletedAt = NULL, updatedAt = :restoredAt
        WHERE pageId IN (
            SELECT id FROM pages WHERE id = :pageId OR parentPageId = :pageId
        )
        """,
    )
    suspend fun markPageTreeRestored(pageId: String, restoredAt: Long)

    @Upsert
    suspend fun upsertAll(entries: List<SearchIndexEntity>)

    @Transaction
    suspend fun replacePageEntries(pageId: String, entries: List<SearchIndexEntity>) {
        deleteForPage(pageId)
        if (entries.isNotEmpty()) {
            upsertAll(entries)
        }
    }

    @Transaction
    suspend fun replaceChatSessionEntries(sessionId: String, entries: List<SearchIndexEntity>) {
        deleteForChatSession(sessionId)
        if (entries.isNotEmpty()) {
            upsertAll(entries)
        }
    }
}
