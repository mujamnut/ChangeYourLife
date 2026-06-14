package com.changeyourlife.cyl.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
)

