package com.changeyourlife.cyl.backend.domain

interface UserRepository {
    suspend fun createUser(
        email: String,
        passwordHash: String,
        displayName: String?,
    ): UserAccount

    suspend fun findByEmail(email: String): UserAccount?

    suspend fun findById(id: String): UserAccount?

    suspend fun createPasswordResetCode(
        userId: String,
        codeHash: String,
        expiresAt: Long,
        createdAt: Long,
    ): PasswordResetCode

    suspend fun findActivePasswordResetCode(
        email: String,
        now: Long,
    ): PasswordResetCode?

    suspend fun markPasswordResetCodeUsed(
        codeId: String,
        usedAt: Long,
    )

    suspend fun updatePasswordHash(
        userId: String,
        passwordHash: String,
        updatedAt: Long,
    )
}

class DuplicateEmailException(email: String) : RuntimeException("Email already registered: $email")

data class PasswordResetCode(
    val id: String,
    val userId: String,
    val codeHash: String,
    val expiresAt: Long,
    val createdAt: Long,
)
