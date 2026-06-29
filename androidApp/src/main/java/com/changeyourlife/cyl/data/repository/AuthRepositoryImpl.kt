package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.session.AuthTokenStore
import com.changeyourlife.cyl.data.remote.auth.AuthApi
import com.changeyourlife.cyl.data.remote.auth.ForgotPasswordRequestDto
import com.changeyourlife.cyl.data.remote.auth.LoginRequestDto
import com.changeyourlife.cyl.data.remote.auth.RegisterRequestDto
import com.changeyourlife.cyl.data.remote.auth.ResetPasswordRequestDto
import com.changeyourlife.cyl.data.remote.auth.toDomain
import com.changeyourlife.cyl.data.sync.SessionSyncCoordinator
import com.changeyourlife.cyl.domain.model.AuthSession
import com.changeyourlife.cyl.domain.model.AuthUser
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AuthState
import com.changeyourlife.cyl.domain.repository.PasswordResetStartResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: AuthTokenStore,
    private val sessionSyncCoordinator: SessionSyncCoordinator,
) : AuthRepository {
    override val authState: Flow<AuthState> = tokenStore.token.map { token ->
        if (token.isNullOrBlank()) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(token = token, user = null)
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AuthSession> {
        return runCatching {
            val session = authApi.register(
                RegisterRequestDto(
                    email = email.trim(),
                    password = password,
                    displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
                ),
            ).toDomain()
            tokenStore.saveToken(session.token)
            runCatching { sessionSyncCoordinator.syncAfterAuth() }
            session
        }
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Result<AuthSession> {
        return runCatching {
            val session = authApi.login(
                LoginRequestDto(
                    email = email.trim(),
                    password = password,
                ),
            ).toDomain()
            tokenStore.saveToken(session.token)
            runCatching { sessionSyncCoordinator.syncAfterAuth() }
            session
        }
    }

    override suspend fun requestPasswordReset(email: String): Result<PasswordResetStartResult> {
        return runCatching {
            val response = authApi.forgotPassword(
                ForgotPasswordRequestDto(
                    email = email.trim(),
                ),
            )
            PasswordResetStartResult(
                debugCode = response.debugCode,
            )
        }
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
    ): Result<Unit> {
        return runCatching {
            authApi.resetPassword(
                ResetPasswordRequestDto(
                    email = email.trim(),
                    code = code.trim(),
                    password = password,
                ),
            )
            Unit
        }
    }

    override suspend fun refreshCurrentUser(): Result<AuthUser> {
        return runCatching {
            val token = tokenStore.token.value
                ?: error("No active auth token.")
            authApi.me("Bearer $token").toDomain()
        }.onFailure {
            tokenStore.clearToken()
        }
    }

    override suspend fun logout() {
        tokenStore.clearToken()
    }
}
