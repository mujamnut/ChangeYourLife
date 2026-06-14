package com.changeyourlife.cyl.backend.domain

interface UserRepository {
    suspend fun createUser(
        email: String,
        passwordHash: String,
        displayName: String?,
    ): UserAccount

    suspend fun findByEmail(email: String): UserAccount?

    suspend fun findById(id: String): UserAccount?
}

class DuplicateEmailException(email: String) : RuntimeException("Email already registered: $email")

