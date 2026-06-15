package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.DuplicateEmailException
import com.changeyourlife.cyl.backend.domain.PasswordResetCode
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

    override suspend fun createPasswordResetCode(
        userId: String,
        codeHash: String,
        expiresAt: Long,
        createdAt: Long,
    ): PasswordResetCode = withContext(Dispatchers.IO) {
        val code = PasswordResetCode(
            id = UUID.randomUUID().toString(),
            userId = userId,
            codeHash = codeHash,
            expiresAt = expiresAt,
            createdAt = createdAt,
        )

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    UPDATE password_reset_codes
                    SET used_at = ?
                    WHERE user_id = ? AND used_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, createdAt)
                    statement.setString(2, userId)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO password_reset_codes (
                        id, user_id, code_hash, expires_at, created_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, code.id)
                    statement.setString(2, code.userId)
                    statement.setString(3, code.codeHash)
                    statement.setLong(4, code.expiresAt)
                    statement.setLong(5, code.createdAt)
                    statement.executeUpdate()
                }

                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }

        code
    }

    override suspend fun findActivePasswordResetCode(
        email: String,
        now: Long,
    ): PasswordResetCode? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT password_reset_codes.id,
                       password_reset_codes.user_id,
                       password_reset_codes.code_hash,
                       password_reset_codes.expires_at,
                       password_reset_codes.created_at
                FROM password_reset_codes
                INNER JOIN users ON users.id = password_reset_codes.user_id
                WHERE users.email = ?
                  AND password_reset_codes.used_at IS NULL
                  AND password_reset_codes.expires_at > ?
                ORDER BY password_reset_codes.created_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, email.normalizeEmail())
                statement.setLong(2, now)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toPasswordResetCode() else null
                }
            }
        }
    }

    override suspend fun markPasswordResetCodeUsed(codeId: String, usedAt: Long): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE password_reset_codes
                SET used_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, usedAt)
                statement.setString(2, codeId)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun updatePasswordHash(
        userId: String,
        passwordHash: String,
        updatedAt: Long,
    ): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE users
                SET password_hash = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, passwordHash)
                statement.setLong(2, updatedAt)
                statement.setString(3, userId)
                statement.executeUpdate()
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

    private fun ResultSet.toPasswordResetCode(): PasswordResetCode {
        return PasswordResetCode(
            id = getString("id"),
            userId = getString("user_id"),
            codeHash = getString("code_hash"),
            expiresAt = getLong("expires_at"),
            createdAt = getLong("created_at"),
        )
    }

    private companion object {
        const val POSTGRES_UNIQUE_VIOLATION = "23505"
    }
}
