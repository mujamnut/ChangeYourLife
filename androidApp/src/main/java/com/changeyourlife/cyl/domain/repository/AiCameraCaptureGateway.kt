package com.changeyourlife.cyl.domain.repository

data class AiCameraCaptureTarget(
    val uri: String,
    val localPath: String,
)

interface AiCameraCaptureGateway {
    suspend fun createTarget(): AiCameraCaptureTarget?

    suspend fun discard(target: AiCameraCaptureTarget)
}
