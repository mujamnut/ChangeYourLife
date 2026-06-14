package com.changeyourlife.cyl.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AiApi {
    @POST("ai/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequestDto
    ): ChatResponseDto

    @POST("ai/summarize")
    suspend fun summarize(
        @Header("Authorization") authorization: String,
        @Body request: SummarizeRequestDto
    ): SummarizeResponseDto

    @POST("ai/generate-tasks")
    suspend fun generateTasks(
        @Header("Authorization") authorization: String,
        @Body request: GenerateTasksRequestDto
    ): GenerateTasksResponseDto

    @POST("ai/generate-plan")
    suspend fun generatePlan(
        @Header("Authorization") authorization: String,
        @Body request: GeneratePlanRequestDto
    ): GeneratePlanResponseDto

    @POST("ai/chat-actions")
    suspend fun chatWithActions(
        @Header("Authorization") authorization: String,
        @Body request: ChatWithActionsRequestDto
    ): ChatWithActionsResponseDto
}
