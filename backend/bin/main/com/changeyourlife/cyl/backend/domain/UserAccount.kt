package com.changeyourlife.cyl.backend.domain

data class UserAccount(
    val id: String,
    val email: String,
    val passwordHash: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

