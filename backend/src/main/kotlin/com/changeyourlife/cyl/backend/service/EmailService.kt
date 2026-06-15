package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.config.EmailConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface PasswordResetEmailSender {
    suspend fun sendPasswordResetCode(to: String, code: String): EmailSendResult
}

object DisabledPasswordResetEmailSender : PasswordResetEmailSender {
    override suspend fun sendPasswordResetCode(to: String, code: String): EmailSendResult {
        return EmailSendResult.NotConfigured
    }
}

sealed class EmailSendResult {
    object Sent : EmailSendResult()
    object NotConfigured : EmailSendResult()
    data class Failed(val statusCode: Int? = null, val message: String) : EmailSendResult()
}

class EmailService(
    private val config: EmailConfig,
) : PasswordResetEmailSender {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    override suspend fun sendPasswordResetCode(to: String, code: String): EmailSendResult {
        val apiKey = config.resendApiKey?.takeIf { it.isNotBlank() }
        val from = config.from?.takeIf { it.isNotBlank() }
        if (apiKey == null || from == null) {
            return EmailSendResult.NotConfigured
        }

        val requestBody = json.encodeToString(
            ResendEmailRequest(
                from = from,
                to = listOf(to),
                subject = "${config.appName} reset code",
                text = buildPasswordResetText(code),
                html = buildPasswordResetHtml(code),
                replyTo = config.replyTo?.takeIf { it.isNotBlank() }?.let(::listOf),
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) {
                    EmailSendResult.Sent
                } else {
                    EmailSendResult.Failed(
                        statusCode = response.statusCode(),
                        message = response.body().take(500),
                    )
                }
            } catch (e: Exception) {
                EmailSendResult.Failed(message = e.localizedMessage ?: "Email request failed.")
            }
        }
    }

    private fun buildPasswordResetText(code: String): String {
        return """
            Your ${config.appName} reset code is:

            $code

            This code expires in 15 minutes. If you did not request this, you can ignore this email.
        """.trimIndent()
    }

    private fun buildPasswordResetHtml(code: String): String {
        val safeAppName = config.appName.escapeHtml()
        return """
            <div style="font-family: Arial, sans-serif; line-height: 1.5; color: #1f2937;">
              <h2 style="margin: 0 0 16px;">$safeAppName password reset</h2>
              <p>Your reset code is:</p>
              <p style="font-size: 28px; font-weight: 700; letter-spacing: 6px; margin: 18px 0;">$code</p>
              <p>This code expires in 15 minutes.</p>
              <p style="color: #6b7280;">If you did not request this, you can ignore this email.</p>
            </div>
        """.trimIndent()
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    @Serializable
    private data class ResendEmailRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val text: String,
        val html: String,
        @SerialName("reply_to")
        val replyTo: List<String>? = null,
    )
}
