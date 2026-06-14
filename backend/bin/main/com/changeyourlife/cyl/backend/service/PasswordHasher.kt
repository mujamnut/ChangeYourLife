package com.changeyourlife.cyl.backend.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
    private val iterations: Int = 120_000,
    private val keyLengthBits: Int = 256,
) {
    private val secureRandom = SecureRandom()
    private val base64 = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = deriveKey(password, salt)

        return listOf(
            FORMAT,
            iterations.toString(),
            base64.encodeToString(salt),
            base64.encodeToString(derived),
        ).joinToString("$")
    }

    fun verify(password: String, encodedHash: String): Boolean {
        val parts = encodedHash.split("$")
        if (parts.size != 4 || parts[0] != FORMAT) return false

        val storedIterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { base64Decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { base64Decoder.decode(parts[3]) }.getOrNull() ?: return false
        val actual = deriveKey(password, salt, storedIterations)

        return MessageDigest.isEqual(expected, actual)
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
        iterationCount: Int = iterations,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private companion object {
        const val FORMAT = "pbkdf2_sha256"
        const val SALT_BYTES = 16
    }
}

