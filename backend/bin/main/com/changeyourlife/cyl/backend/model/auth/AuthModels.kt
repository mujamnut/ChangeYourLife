package com.changeyourlife.cyl.backend.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val password: String,
)

@Serializable
data class ForgotPasswordResponse(
    val message: String,
    val debugCode: String? = null,
)

@Serializable
data class ResetPasswordResponse(
    val message: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
