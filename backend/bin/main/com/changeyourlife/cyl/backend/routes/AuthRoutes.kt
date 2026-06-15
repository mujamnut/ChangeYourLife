package com.changeyourlife.cyl.backend.routes

import com.changeyourlife.cyl.backend.data.normalizeEmail
import com.changeyourlife.cyl.backend.domain.DuplicateEmailException
import com.changeyourlife.cyl.backend.domain.UserAccount
import com.changeyourlife.cyl.backend.domain.UserRepository
import com.changeyourlife.cyl.backend.model.ErrorResponse
import com.changeyourlife.cyl.backend.model.auth.AuthResponse
import com.changeyourlife.cyl.backend.model.auth.ForgotPasswordRequest
import com.changeyourlife.cyl.backend.model.auth.ForgotPasswordResponse
import com.changeyourlife.cyl.backend.model.auth.LoginRequest
import com.changeyourlife.cyl.backend.model.auth.RegisterRequest
import com.changeyourlife.cyl.backend.model.auth.ResetPasswordRequest
import com.changeyourlife.cyl.backend.model.auth.ResetPasswordResponse
import com.changeyourlife.cyl.backend.model.auth.UserResponse
import com.changeyourlife.cyl.backend.service.JwtService
import com.changeyourlife.cyl.backend.service.PasswordHasher
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.security.SecureRandom

fun Route.authRoutes(
    userRepository: UserRepository,
    jwtService: JwtService,
    passwordHasher: PasswordHasher = PasswordHasher(),
    passwordResetDebugCodes: Boolean = false,
) {
    val resetCodeGenerator = PasswordResetCodeGenerator()

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val validationError = validateRegisterRequest(request)
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                return@post
            }

            val passwordHash = passwordHasher.hash(request.password)
            val user = try {
                userRepository.createUser(
                    email = request.email,
                    passwordHash = passwordHash,
                    displayName = request.displayName,
                )
            } catch (_: DuplicateEmailException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already registered."))
                return@post
            }

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    token = jwtService.generateToken(user),
                    user = user.toResponse(),
                ),
            )
        }

        post("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            val email = request.email.normalizeEmail()
            if (email.isBlank() || "@" !in email) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("A valid email is required."))
                return@post
            }

            val user = userRepository.findByEmail(email)
            val debugCode = if (user != null) {
                val now = System.currentTimeMillis()
                val code = resetCodeGenerator.nextCode()
                userRepository.createPasswordResetCode(
                    userId = user.id,
                    codeHash = passwordHasher.hash(code),
                    expiresAt = now + PasswordResetCodeTtlMillis,
                    createdAt = now,
                )
                if (passwordResetDebugCodes) code else null
            } else {
                null
            }

            call.respond(
                ForgotPasswordResponse(
                    message = "If the email exists, a reset code has been sent.",
                    debugCode = debugCode,
                ),
            )
        }

        post("/reset-password") {
            val request = call.receive<ResetPasswordRequest>()
            val validationError = validateResetPasswordRequest(request)
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                return@post
            }

            val now = System.currentTimeMillis()
            val resetCode = userRepository.findActivePasswordResetCode(request.email, now)
            if (resetCode == null || !passwordHasher.verify(request.code.trim(), resetCode.codeHash)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid or expired reset code."))
                return@post
            }

            userRepository.updatePasswordHash(
                userId = resetCode.userId,
                passwordHash = passwordHasher.hash(request.password),
                updatedAt = now,
            )
            userRepository.markPasswordResetCodeUsed(
                codeId = resetCode.id,
                usedAt = now,
            )

            call.respond(
                ResetPasswordResponse(
                    message = "Password has been reset. You can log in with the new password.",
                ),
            )
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = userRepository.findByEmail(request.email)

            if (user == null || !passwordHasher.verify(request.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid email or password."))
                return@post
            }

            call.respond(
                AuthResponse(
                    token = jwtService.generateToken(user),
                    user = user.toResponse(),
                ),
            )
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.subject
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing user identity."))
                    return@get
                }

                val user = userRepository.findById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found."))
                    return@get
                }

                call.respond(user.toResponse())
            }
        }
    }
}

private fun validateRegisterRequest(request: RegisterRequest): String? {
    val email = request.email.normalizeEmail()
    return when {
        email.isBlank() || "@" !in email -> "A valid email is required."
        request.password.length < 8 -> "Password must be at least 8 characters."
        request.displayName != null && request.displayName.length > 80 -> "Display name is too long."
        else -> null
    }
}

private fun validateResetPasswordRequest(request: ResetPasswordRequest): String? {
    val email = request.email.normalizeEmail()
    val code = request.code.trim()
    return when {
        email.isBlank() || "@" !in email -> "A valid email is required."
        code.length != 6 || code.any { !it.isDigit() } -> "Enter the 6-digit reset code."
        request.password.length < 8 -> "Password must be at least 8 characters."
        else -> null
    }
}

private fun UserAccount.toResponse(): UserResponse {
    return UserResponse(
        id = id,
        email = email,
        displayName = displayName,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private class PasswordResetCodeGenerator {
    private val random = SecureRandom()

    fun nextCode(): String {
        return "%06d".format(random.nextInt(1_000_000))
    }
}

private const val PasswordResetCodeTtlMillis = 15L * 60L * 1_000L
