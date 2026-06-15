package com.changeyourlife.cyl.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        uiState = uiState,
        onModeChange = viewModel::setMode,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onResetCodeChange = viewModel::updateResetCode,
        onDisplayNameChange = viewModel::updateDisplayName,
        onSubmit = {
            viewModel.submit(onAuthenticated)
        },
        modifier = modifier,
    )
}

@Composable
private fun AuthScreen(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onResetCodeChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val isLogin = uiState.mode == AuthMode.Login
    val isRegister = uiState.mode == AuthMode.Register
    val isForgotPassword = uiState.mode == AuthMode.ForgotPassword
    val isResetPassword = uiState.mode == AuthMode.ResetPassword

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            AuthBrandHeader(isLogin = isLogin)

            if (uiState.mode == AuthMode.Login || uiState.mode == AuthMode.Register) {
                AuthModeSwitch(
                    selectedMode = uiState.mode,
                    onModeChange = onModeChange,
                )
            } else {
                TextButton(
                    onClick = { onModeChange(AuthMode.Login) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(text = "Back to login")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = uiState.mode.titleText(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.mode.descriptionText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isRegister) {
                    AuthTextField(
                        value = uiState.displayName,
                        onValueChange = onDisplayNameChange,
                        label = "Name",
                        placeholder = "Your name",
                        leadingIcon = Icons.Rounded.Person,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }

                if (!isResetPassword || uiState.email.isBlank()) {
                    AuthTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = "Email",
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Rounded.Email,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (isForgotPassword) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = if (isForgotPassword) {
                            KeyboardActions(onDone = { onSubmit() })
                        } else {
                            KeyboardActions.Default
                        },
                    )
                } else {
                    AuthReadonlyEmail(email = uiState.email)
                }

                if (isResetPassword) {
                    AuthTextField(
                        value = uiState.resetCode,
                        onValueChange = onResetCodeChange,
                        label = "Reset code",
                        placeholder = "6-digit code",
                        leadingIcon = Icons.Rounded.Lock,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }

                if (!isForgotPassword) {
                    AuthTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = if (isResetPassword) "New password" else "Password",
                        placeholder = "At least 8 characters",
                        leadingIcon = Icons.Rounded.Lock,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (isPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        },
                    )
                }

                if (isLogin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onModeChange(AuthMode.ForgotPassword) }
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Forgot password?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    AuthErrorMessage(message = message)
                }

                uiState.infoMessage?.let { message ->
                    AuthInfoMessage(message = message)
                }

                Button(
                    onClick = onSubmit,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            uiState.isSubmitting -> "Please wait"
                            else -> uiState.mode.submitText()
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(
    isLogin: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = "ChangeYourLife",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isLogin) {
                "Plan, write, and build your life system in one place."
            } else {
                "Create a CYL account and keep your workspace synced."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun AuthReadonlyEmail(
    email: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Email,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Email",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AuthModeSwitch(
    selectedMode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthModeButton(
            text = "Login",
            selected = selectedMode == AuthMode.Login,
            onClick = { onModeChange(AuthMode.Login) },
            modifier = Modifier.weight(1f),
        )
        AuthModeButton(
            text = "Register",
            selected = selectedMode == AuthMode.Register,
            onClick = { onModeChange(AuthMode.Register) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AuthModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
            )
        },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun AuthErrorMessage(
    message: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun AuthInfoMessage(
    message: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun AuthMode.titleText(): String {
    return when (this) {
        AuthMode.Login -> "Welcome back"
        AuthMode.Register -> "Create your account"
        AuthMode.ForgotPassword -> "Reset your password"
        AuthMode.ResetPassword -> "Enter reset code"
    }
}

private fun AuthMode.descriptionText(): String {
    return when (this) {
        AuthMode.Login -> "Continue to your CYL workspace."
        AuthMode.Register -> "Start with one private workspace and sync it through CYL."
        AuthMode.ForgotPassword -> "Enter your email and we will send a 6-digit reset code."
        AuthMode.ResetPassword -> "Use the code from your email and choose a new password."
    }
}

private fun AuthMode.submitText(): String {
    return when (this) {
        AuthMode.Login -> "Log in"
        AuthMode.Register -> "Create account"
        AuthMode.ForgotPassword -> "Send reset code"
        AuthMode.ResetPassword -> "Reset password"
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthLoginPreview() {
    ChangeYourLifeTheme {
        AuthScreen(
            uiState = AuthUiState(mode = AuthMode.Login),
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onResetCodeChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthRegisterPreview() {
    ChangeYourLifeTheme {
        AuthScreen(
            uiState = AuthUiState(mode = AuthMode.Register),
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onResetCodeChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthResetPasswordPreview() {
    ChangeYourLifeTheme {
        AuthScreen(
            uiState = AuthUiState(
                mode = AuthMode.ResetPassword,
                email = "person@example.com",
                infoMessage = "If the email exists, a reset code has been sent. Dev code: 123456",
            ),
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onResetCodeChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
        )
    }
}
