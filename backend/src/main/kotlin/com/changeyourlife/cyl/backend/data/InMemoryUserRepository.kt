package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.DuplicateEmailException
import com.changeyourlife.cyl.backend.domain.UserAccount
import com.changeyourlife.cyl.backend.domain.UserRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    private val usersById = ConcurrentHashMap<String, UserAccount>()
    private val userIdsByEmail = ConcurrentHashMap<String, String>()

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
}

fun String.normalizeEmail(): String {
    return trim().lowercase()
}

