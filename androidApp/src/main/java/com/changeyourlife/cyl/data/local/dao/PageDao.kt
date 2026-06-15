package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query(
        """
        SELECT * FROM pages
        WHERE workspaceId = :workspaceId AND deletedAt IS NULL
        ORDER BY sortOrder ASC, updatedAt DESC
        """,
    )
    fun observePages(workspaceId: String): Flow<List<PageEntity>>

    @Query(
        """
        SELECT * FROM pages
        WHERE parentPageId = :parentPageId AND deletedAt IS NULL
        ORDER BY sortOrder ASC, updatedAt DESC
        """,
    )
    fun observeChildPages(parentPageId: String): Flow<List<PageEntity>>

    @Query(
        """
        SELECT * FROM pages
        WHERE deletedAt IS NULL
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentPages(limit: Int): Flow<List<PageEntity>>

    @Query(
        """
        SELECT * FROM pages
        WHERE workspaceId = :workspaceId AND deletedAt IS NULL
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentPages(workspaceId: String, limit: Int): Flow<List<PageEntity>>

    @Query(
        """
        SELECT * FROM pages
        WHERE workspaceId = :workspaceId AND deletedAt IS NOT NULL
        ORDER BY deletedAt DESC, updatedAt DESC
        """,
    )
    fun observeDeletedPages(workspaceId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :pageId AND deletedAt IS NULL LIMIT 1")
    fun observePage(pageId: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :pageId LIMIT 1")
    suspend fun getPage(pageId: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE deletedAt IS NULL")
    fun observePageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pages WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    fun observePageCount(workspaceId: String): Flow<Int>

    @Upsert
    suspend fun upsertPage(page: PageEntity)

    @Query(
        """
        UPDATE pages
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :pageId OR parentPageId = :pageId
        """,
    )
    suspend fun softDeletePageTree(pageId: String, deletedAt: Long)

    @Query(
        """
        UPDATE pages
        SET deletedAt = NULL, updatedAt = :restoredAt
        WHERE id = :pageId OR parentPageId = :pageId
        """,
    )
    suspend fun restorePageTree(pageId: String, restoredAt: Long)

    @Query("DELETE FROM pages WHERE id = :pageId OR parentPageId = :pageId")
    suspend fun deletePageTreePermanently(pageId: String)
}
