package com.changeyourlife.cyl.domain.model

data class AuthSession(
    val token: String,
    val user: AuthUser,
)

