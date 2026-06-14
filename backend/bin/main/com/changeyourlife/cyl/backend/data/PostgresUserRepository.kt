package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.DuplicateEmailException
import com.changeyourlife.cyl.backend.domain.UserAccount
import com.changeyourlife.cyl.backend.domain.UserRepository
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresUserRepository(
    private val dataSource: DataSource,
) : UserRepository {
    override suspend fun createUser(
        email: String,
        passwordHash: String,
        displayName: String?,
    ): UserAccount = withContext(Dispatchers.IO) {
        val normalizedEmail = email.normalizeEmail()
        val now = System.currentTimeMillis()
        val user = UserAccount(
            id = UUID.randomUUID().toString(),
            email = normalizedEmail,
            passwordHash = passwordHash,
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
            createdAt = now,
            updatedAt = now,
        )

        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, user.id)
                    statement.setString(2, user.email)
                    statement.setString(3, user.passwordHash)
                    statement.setString(4, user.displayName)
                    statement.setLong(5, user.createdAt)
                    statement.setLong(6, user.updatedAt)
                    statement.executeUpdate()
                }
            }
        } catch (exception: SQLException) {
            if (exception.sqlState == POSTGRES_UNIQUE_VIOLATION) {
                throw DuplicateEmailException(normalizedEmail)
            }
            throw exception
        }

        user
    }

    override suspend fun findByEmail(email: String): UserAccount? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, email, password_hash, display_name, created_at, updated_at
                FROM users
                WHERE email = ?
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, email.normalizeEmail())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toUserAccount() else null
                }
            }
        }
    }

    override suspend fun findById(id: String): UserAccount? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, email, password_hash, display_name, created_at, updated_at
                FROM users
                WHERE id = ?
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toUserAccount() else null
                }
            }
        }
    }

    private fun ResultSet.toUserAccount(): UserAccount {
        return UserAccount(
            id = getString("id"),
            email = getString("email"),
            passwordHash = getString("password_hash"),
            displayName = getString("display_name"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
        )
    }

    private companion object {
        const val POSTGRES_UNIQUE_VIOLATION = "23505"
    }
}

