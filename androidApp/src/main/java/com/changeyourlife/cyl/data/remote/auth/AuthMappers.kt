package com.changeyourlife.cyl.data.remote.auth

import com.changeyourlife.cyl.domain.model.AuthSession
import com.changeyourlife.cyl.domain.model.AuthUser

fun AuthResponseDto.toDomain(): AuthSession {
    return AuthSession(
        token = token,
        user = user.toDomain(),
    )
}

fun UserResponseDto.toDomain(): AuthUser {
    return AuthUser(
        id = id,
        email = email,
        displayName = displayName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

