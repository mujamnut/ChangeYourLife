package com.changeyourlife.cyl.domain.model

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

