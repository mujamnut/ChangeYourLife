package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.AuthSession
import com.changeyourlife.cyl.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AuthSession>

    suspend fun login(
        email: String,
        password: String,
    ): Result<AuthSession>

    suspend fun requestPasswordReset(
        email: String,
    ): Result<PasswordResetStartResult>

    suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
    ): Result<Unit>

    suspend fun refreshCurrentUser(): Result<AuthUser>

    suspend fun logout()
}

data class PasswordResetStartResult(
    val debugCode: String?,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val token: String, val user: AuthUser?) : AuthState
}
