package com.changeyourlife.cyl.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApi {
    @GET("ai/status")
    suspend fun status(): AiStatusResponseDto

    @POST("ai/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequestDto
    ): ChatResponseDto

    @POST("ai/chat-actions")
    suspend fun chatWithActions(
        @Header("Authorization") authorization: String,
        @Body request: ChatWithActionsRequestDto
    ): ChatWithActionsResponseDto

    @POST("ai/chat-actions/jobs")
    suspend fun createChatWithActionsJob(
        @Header("Authorization") authorization: String,
        @Body request: ChatWithActionsRequestDto
    ): AiChatActionsJobAcceptedResponseDto

    @GET("ai/chat-actions/jobs/{jobId}")
    suspend fun chatWithActionsJobStatus(
        @Header("Authorization") authorization: String,
        @Path("jobId") jobId: String
    ): AiChatActionsJobStatusResponseDto
}
