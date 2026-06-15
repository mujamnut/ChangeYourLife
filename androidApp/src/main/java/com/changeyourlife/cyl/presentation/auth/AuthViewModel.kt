package com.changeyourlife.cyl.presentation.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import kotlinx.serialization.SerializationException

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun setMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun updateResetCode(resetCode: String) {
        _uiState.update {
            it.copy(
                resetCode = resetCode.filter(Char::isDigit).take(6),
                errorMessage = null,
            )
        }
    }

    fun updateDisplayName(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, errorMessage = null) }
    }

    fun submit(onAuthenticated: () -> Unit) {
        val state = _uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            when (state.mode) {
                AuthMode.Login -> submitLogin(state, onAuthenticated)
                AuthMode.Register -> submitRegister(state, onAuthenticated)
                AuthMode.ForgotPassword -> submitForgotPassword(state)
                AuthMode.ResetPassword -> submitResetPassword(state)
            }
        }
    }

    private suspend fun submitLogin(
        state: AuthUiState,
        onAuthenticated: () -> Unit,
    ) {
        authRepository.login(
            email = state.email,
            password = state.password,
        )
            .onSuccess {
                _uiState.update { it.copy(isSubmitting = false) }
                onAuthenticated()
            }
            .onFailure { throwable ->
                handleFailure(throwable)
            }
    }

    private suspend fun submitRegister(
        state: AuthUiState,
        onAuthenticated: () -> Unit,
    ) {
        authRepository.register(
            email = state.email,
            password = state.password,
            displayName = state.displayName,
        )
            .onSuccess {
                _uiState.update { it.copy(isSubmitting = false) }
                onAuthenticated()
            }
            .onFailure { throwable ->
                handleFailure(throwable)
            }
    }

    private suspend fun submitForgotPassword(state: AuthUiState) {
        authRepository.requestPasswordReset(email = state.email)
            .onSuccess { result ->
                _uiState.update {
                    it.copy(
                        mode = AuthMode.ResetPassword,
                        isSubmitting = false,
                        infoMessage = buildString {
                            append("If the email exists, a reset code has been sent.")
                            if (!result.debugCode.isNullOrBlank()) {
                                append(" Dev code: ${result.debugCode}")
                            }
                        },
                        resetCode = result.debugCode.orEmpty(),
                    )
                }
            }
            .onFailure { throwable ->
                handleFailure(throwable)
            }
    }

    private suspend fun submitResetPassword(state: AuthUiState) {
        authRepository.resetPassword(
            email = state.email,
            code = state.resetCode,
            password = state.password,
        )
            .onSuccess {
                _uiState.update {
                    it.copy(
                        mode = AuthMode.Login,
                        password = "",
                        resetCode = "",
                        isSubmitting = false,
                        infoMessage = "Password reset. Log in with your new password.",
                    )
                }
            }
            .onFailure { throwable ->
                handleFailure(throwable)
            }
    }

    private fun handleFailure(throwable: Throwable) {
        Log.w(TAG, "Auth request failed.", throwable)
        _uiState.update {
            it.copy(
                isSubmitting = false,
                errorMessage = throwable.toUserMessage(),
            )
        }
    }

    private fun validate(state: AuthUiState): String? {
        return when {
            state.email.isBlank() || "@" !in state.email -> "Enter a valid email."
            state.mode == AuthMode.ForgotPassword -> null
            state.mode == AuthMode.ResetPassword && state.resetCode.length != 6 -> "Enter the 6-digit reset code."
            state.password.length < 8 -> "Password must be at least 8 characters."
            state.mode == AuthMode.Register && state.displayName.length > 80 -> "Name is too long."
            else -> null
        }
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is IOException -> "Cannot reach CYL backend. Start the backend and try again."
            is SerializationException -> "Backend response could not be read. Check the server logs."
            is HttpException -> when (code()) {
                400 -> "Check your email and password, then try again."
                401 -> "Invalid email or password."
                409 -> "Email already registered."
                else -> "Server error ${code()}."
            }
            else -> message ?: "Something went wrong."
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val resetCode: String = "",
    val displayName: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

enum class AuthMode {
    Login,
    Register,
    ForgotPassword,
    ResetPassword,
}
