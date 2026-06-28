package com.changeyourlife.cyl.data.remote.sync

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncApi {
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
