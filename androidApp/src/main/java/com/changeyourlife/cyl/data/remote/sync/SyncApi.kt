package com.changeyourlife.cyl.data.remote.sync

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncApi {
    @GET("api/v1/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String,
        @Query("q") query: String,
        @Query("scope") scope: String,
        @Query("limit") limit: Int,
    ): SearchListResponseDto

    @GET("api/v1/workspaces")
    suspend fun listWorkspaces(
        @Header("Authorization") authorization: String,
        @Query("includeDeleted") includeDeleted: Boolean = false,
    ): WorkspaceListResponseDto

    @PUT("api/v1/workspaces/{id}")
    suspend fun upsertWorkspace(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body workspace: WorkspaceSyncDto,
    ): WorkspaceSyncDto

    @DELETE("api/v1/workspaces/{id}")
    suspend fun deleteWorkspace(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )

    @GET("api/v1/pages")
    suspend fun listPages(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String,
        @Query("includeDeleted") includeDeleted: Boolean = false,
    ): PageListResponseDto

    @GET("api/v1/pages/{id}")
    suspend fun getPage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Query("includeDeleted") includeDeleted: Boolean = false,
    ): PageSyncDto

    @PUT("api/v1/pages/{id}")
    suspend fun upsertPage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body page: PageSyncDto,
    ): PageSyncDto

    @GET("api/v1/ai-skills")
    suspend fun listAiSkills(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String,
        @Query("includeDeleted") includeDeleted: Boolean = false,
    ): AiSkillListResponseDto

    @PUT("api/v1/ai-skills/{id}")
    suspend fun upsertAiSkill(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body skill: AiSkillSyncDto,
    ): AiSkillSyncDto

    @GET("api/v1/ai-action-logs")
    suspend fun listAiActionLogs(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String,
        @Query("updatedAfter") updatedAfter: Long = 0L,
    ): AiActionLogListResponseDto

    @PUT("api/v1/ai-action-logs/{auditId}")
    suspend fun upsertAiActionLog(
        @Header("Authorization") authorization: String,
        @Path("auditId") auditId: String,
        @Body actionLog: AiActionLogSyncDto,
    ): AiActionLogSyncDto

    @GET("api/v1/chat-sessions")
    suspend fun listChatSessions(
        @Header("Authorization") authorization: String,
        @Query("scopeId") scopeId: String,
        @Query("updatedAfter") updatedAfter: Long = 0L,
    ): ChatSessionListResponseDto

    @PUT("api/v1/chat-sessions/{id}")
    suspend fun upsertChatSession(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body session: ChatSessionSyncDto,
    ): ChatSessionSyncDto

    @GET("api/v1/chat-sessions/{sessionId}/messages")
    suspend fun listChatMessages(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String,
        @Query("updatedAfter") updatedAfter: Long = 0L,
    ): ChatMessageListResponseDto

    @PUT("api/v1/chat-messages/{id}")
    suspend fun upsertChatMessage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body message: ChatMessageSyncDto,
    ): ChatMessageSyncDto

    @POST("api/v1/pages/{id}/blocks")
    suspend fun addPageBlock(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: PageBlockCreateRequestDto,
    ): PageSyncDto

    @DELETE("api/v1/pages/{id}/blocks/{blockId}")
    suspend fun deletePageBlock(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("blockId") blockId: String,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/blocks/{blockId}/position")
    suspend fun movePageBlock(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("blockId") blockId: String,
        @Body request: PageElementPositionPatchRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/blocks/{blockId}")
    suspend fun updatePageBlockText(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("blockId") blockId: String,
        @Body request: PageBlockPatchRequestDto,
    ): PageSyncDto

    @POST("api/v1/pages/{id}/properties")
    suspend fun addPageProperty(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: PagePropertyCreateRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/properties")
    suspend fun updatePagePropertyValue(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: PagePropertyValuePatchRequestDto,
    ): PageSyncDto

    @DELETE("api/v1/pages/{id}/properties/{propertyId}")
    suspend fun deletePageProperty(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("propertyId") propertyId: String,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/properties/{propertyId}/position")
    suspend fun movePageProperty(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("propertyId") propertyId: String,
        @Body request: PageElementPositionPatchRequestDto,
    ): PageSyncDto

    @POST("api/v1/pages/{id}/tables/{tableBlockId}/columns")
    suspend fun addPageTableColumn(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Body request: PageTableColumnCreateRequestDto,
    ): PageSyncDto

    @DELETE("api/v1/pages/{id}/tables/{tableBlockId}/columns/{columnId}")
    suspend fun deletePageTableColumn(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("columnId") columnId: String,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/tables/{tableBlockId}/columns/{columnId}/position")
    suspend fun movePageTableColumn(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("columnId") columnId: String,
        @Body request: PageElementPositionPatchRequestDto,
    ): PageSyncDto

    @POST("api/v1/pages/{id}/tables/{tableBlockId}/rows")
    suspend fun addPageTableRow(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Body request: PageTableRowCreateRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/tables/{tableBlockId}/rows/{rowId}")
    suspend fun updatePageTableRow(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("rowId") rowId: String,
        @Body request: PageTableRowPatchRequestDto,
    ): PageSyncDto

    @DELETE("api/v1/pages/{id}/tables/{tableBlockId}/rows/{rowId}")
    suspend fun deletePageTableRow(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("rowId") rowId: String,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/tables/{tableBlockId}/rows/{rowId}/position")
    suspend fun movePageTableRow(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("rowId") rowId: String,
        @Body request: PageElementPositionPatchRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/table-cells")
    suspend fun updatePageTableCellValue(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: PageTableCellValuePatchRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/tables/{tableBlockId}")
    suspend fun updatePageTable(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Body request: PageTablePatchRequestDto,
    ): PageSyncDto

    @PATCH("api/v1/pages/{id}/tables/{tableBlockId}/columns/{columnId}")
    suspend fun updatePageTableColumn(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Path("tableBlockId") tableBlockId: String,
        @Path("columnId") columnId: String,
        @Body request: PageTableColumnPatchRequestDto,
    ): PageSyncDto

    @DELETE("api/v1/pages/{id}")
    suspend fun deletePage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )

    @POST("api/v1/pages/{id}/restore")
    suspend fun restorePage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )

    @DELETE("api/v1/pages/{id}/permanent")
    suspend fun deletePagePermanently(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )
}
