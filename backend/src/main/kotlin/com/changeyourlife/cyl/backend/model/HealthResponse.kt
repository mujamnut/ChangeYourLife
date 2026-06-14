package com.changeyourlife.cyl.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val database: String,
)
