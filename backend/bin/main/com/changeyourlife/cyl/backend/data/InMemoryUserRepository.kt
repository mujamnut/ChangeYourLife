package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.DuplicateEmailException
import com.changeyourlife.cyl.backend.domain.PasswordResetCode
import com.changeyourlife.cyl.backend.domain.UserAccount
import com.changeyourlife.cyl.backend.domain.UserRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    private val usersById = ConcurrentHashMap<String, UserAccount>()
    private val userIdsByEmail = ConcurrentHashMap<String, String>()
    private val passwordResetCodesById = ConcurrentHashMap<String, InMemoryPasswordResetCode>()

    override suspend fun createUser(
        email: String,
        passwordHash: String,
        displayName: String?,
    ): UserAccount {
        val normalizedEmail = email.normalizeEmail()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val user = UserAccount(
            id = id,
            email = normalizedEmail,
            passwordHash = passwordHash,
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
            createdAt = now,
            updatedAt = now,
        )

        val existing = userIdsByEmail.putIfAbsent(normalizedEmail, id)
        if (existing != null) {
            throw DuplicateEmailException(normalizedEmail)
        }

        usersById[id] = user
        return user
    }

    override suspend fun findByEmail(email: String): UserAccount? {
        val id = userIdsByEmail[email.normalizeEmail()] ?: return null
        return usersById[id]
    }

    override suspend fun findById(id: String): UserAccount? {
        return usersById[id]
    }

    override suspend fun createPasswordResetCode(
        userId: String,
        codeHash: String,
        expiresAt: Long,
        createdAt: Long,
    ): PasswordResetCode {
        passwordResetCodesById.replaceAll { _, code ->
            if (code.userId == userId && code.usedAt == null) {
                code.copy(usedAt = createdAt)
            } else {
                code
            }
        }

        val code = InMemoryPasswordResetCode(
            id = UUID.randomUUID().toString(),
            userId = userId,
            codeHash = codeHash,
            expiresAt = expiresAt,
            createdAt = createdAt,
            usedAt = null,
        )
        passwordResetCodesById[code.id] = code
        return code.toDomain()
    }

    override suspend fun findActivePasswordResetCode(email: String, now: Long): PasswordResetCode? {
        val userId = userIdsByEmail[email.normalizeEmail()] ?: return null
        return passwordResetCodesById.values
            .asSequence()
            .filter { code -> code.userId == userId && code.usedAt == null && code.expiresAt > now }
            .maxByOrNull { code -> code.createdAt }
            ?.toDomain()
    }

    override suspend fun markPasswordResetCodeUsed(codeId: String, usedAt: Long) {
        passwordResetCodesById.computeIfPresent(codeId) { _, code ->
            code.copy(usedAt = usedAt)
        }
    }

    override suspend fun updatePasswordHash(
        userId: String,
        passwordHash: String,
        updatedAt: Long,
    ) {
        usersById.computeIfPresent(userId) { _, user ->
            user.copy(
                passwordHash = passwordHash,
                updatedAt = updatedAt,
            )
        }
    }
}

fun String.normalizeEmail(): String {
    return trim().lowercase()
}

private data class InMemoryPasswordResetCode(
    val id: String,
    val userId: String,
    val codeHash: String,
    val expiresAt: Long,
    val createdAt: Long,
    val usedAt: Long?,
)

private fun InMemoryPasswordResetCode.toDomain(): PasswordResetCode {
    return PasswordResetCode(
        id = id,
        userId = userId,
        codeHash = codeHash,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )
}
