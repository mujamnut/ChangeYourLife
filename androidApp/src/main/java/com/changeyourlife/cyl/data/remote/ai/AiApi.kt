package com.changeyourlife.cyl.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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
}
