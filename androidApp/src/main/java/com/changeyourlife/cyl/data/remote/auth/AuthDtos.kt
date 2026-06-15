package com.changeyourlife.cyl.data.remote.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class ForgotPasswordRequestDto(
    val email: String,
)

@Serializable
data class ResetPasswordRequestDto(
    val email: String,
    val code: String,
    val password: String,
)

@Serializable
data class ForgotPasswordResponseDto(
    val message: String,
    val debugCode: String? = null,
)

@Serializable
data class ResetPasswordResponseDto(
    val message: String,
)

@Serializable
data class AuthResponseDto(
    val token: String,
    val user: UserResponseDto,
)

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ErrorResponseDto(
    val message: String,
)
