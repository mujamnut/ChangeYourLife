package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.AppConfig
import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.changeyourlife.cyl.backend.config.EmailConfig
import com.changeyourlife.cyl.backend.config.JwtConfig
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.model.auth.AuthResponse
import com.changeyourlife.cyl.backend.model.auth.ForgotPasswordResponse
import com.changeyourlife.cyl.backend.model.sync.PageListResponse
import com.changeyourlife.cyl.backend.model.sync.PageSyncDto
import com.changeyourlife.cyl.backend.model.sync.WorkspaceListResponse
import com.changeyourlife.cyl.backend.model.sync.WorkspaceSyncDto
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun testZhipuConnectionDirectly() {
        val config = AppConfig.fromEnvironment()
        val apiKey = config.glmApiKey
        println("Inspecting GLM Key: length=${apiKey?.length}, prefix=${apiKey?.take(5)}, suffix=${apiKey?.takeLast(5)}")
        if (apiKey.isNullOrBlank()) {
            println("GLM API KEY is blank, skipping direct test.")
            return
        }
        val service = AiService(glmApiKey = apiKey)
        val response = service.chat(listOf(ChatMessage(role = "user", content = "Hello")))
        println("Direct response from Zhipu: $response")
    }

    @Test
    fun testGeminiConnectionDirectly() {
        val config = AppConfig.fromEnvironment()
        val apiKey = config.geminiApiKey
        println("Inspecting Gemini Key: length=${apiKey?.length}, prefix=${apiKey?.take(5)}, suffix=${apiKey?.takeLast(5)}")
        if (apiKey.isNullOrBlank() || apiKey.startsWith("AIzaSyDummy")) {
            println("Gemini API KEY is blank or dummy, skipping direct test.")
            return
        }
        val service = AiService(geminiApiKey = apiKey)
        val response = service.chat(listOf(ChatMessage(role = "user", content = "Hello")))
        println("Direct response from Gemini: $response")
    }

    @Test
    fun testChatWithActionsDirectly() {
        val config = AppConfig.fromEnvironment()
        val apiKey = config.geminiApiKey
        if (apiKey.isNullOrBlank() || apiKey.startsWith("AIzaSyDummy")) {
            println("Gemini API KEY is blank or dummy, skipping direct test.")
            return
        }
        val service = AiService(geminiApiKey = apiKey)
        val response = service.chatWithActions(listOf(ChatMessage(role = "user", content = "create a page called Vacation Plan")))
        println("Direct response from chatWithActions: reply='${response.reply}', actions=${response.actions}")
    }

    @Test
    fun testOpenRouterConnectionDirectly() {
        val config = AppConfig.fromEnvironment()
        val apiKey = config.openRouterApiKey
        println("Inspecting OpenRouter Key: length=${apiKey?.length}, prefix=${apiKey?.take(5)}, suffix=${apiKey?.takeLast(5)}")
        if (apiKey.isNullOrBlank()) {
            println("OpenRouter API KEY is blank, skipping direct test.")
            return
        }
        val service = AiService(
            openRouterApiKey = apiKey,
            openRouterModel = config.openRouterModel,
        )
        val response = service.chat(listOf(ChatMessage(role = "user", content = "Hello")))
        println("Direct response from OpenRouter (${config.openRouterModel}): $response")
    }

    @Test
    fun healthReturnsOk() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun aiStatusReturnsSandboxModeByDefault() = testApplication {
        application {
            module()
        }

        val response = client.get("/ai/status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("mode"), "Expected 'mode' key in response body: $body")
        assertTrue(body.contains("model"), "Expected 'model' key in response body: $body")
        assertTrue(body.contains("apiKeyConfigured"), "Expected 'apiKeyConfigured' key in response body: $body")
        assertTrue(body.contains("apiKeyLength"), "Expected 'apiKeyLength' key in response body: $body")
    }

    @Test
    fun registerLoginAndMeReturnAuthenticatedUser() = testApplication {
        application {
            module()
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "person@example.com",
                  "password": "strong-password",
                  "displayName": "Person"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val authResponse = Json.decodeFromString<AuthResponse>(registerResponse.bodyAsText())
        assertTrue(authResponse.token.isNotBlank())
        assertEquals("person@example.com", authResponse.user.email)

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "person@example.com",
                  "password": "strong-password"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val meResponse = client.get("/auth/me") {
            header(HttpHeaders.Authorization, "Bearer ${authResponse.token}")
        }

        assertEquals(HttpStatusCode.OK, meResponse.status)
        assertTrue(meResponse.bodyAsText().contains("person@example.com"))
    }

    @Test
    fun forgotPasswordCanResetPasswordInDevelopmentMode() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "reset@example.com",
                  "password": "old-password",
                  "displayName": "Reset User"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val forgotResponse = client.post("/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "reset@example.com"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, forgotResponse.status)
        val forgotBody = Json.decodeFromString<ForgotPasswordResponse>(forgotResponse.bodyAsText())
        val resetCode = forgotBody.debugCode
        assertTrue(!resetCode.isNullOrBlank(), "Expected development reset code.")

        val resetResponse = client.post("/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "reset@example.com",
                  "code": "$resetCode",
                  "password": "new-password"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status)

        val oldLoginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "reset@example.com",
                  "password": "old-password"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, oldLoginResponse.status)

        val newLoginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "reset@example.com",
                  "password": "new-password"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, newLoginResponse.status)
    }

    @Test
    fun authenticatedWorkspaceAndPageSyncRoundTrip() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "sync@example.com",
                  "password": "sync-password",
                  "displayName": "Sync User"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val authResponse = Json.decodeFromString<AuthResponse>(registerResponse.bodyAsText())
        val authHeader = "Bearer ${authResponse.token}"

        val workspace = WorkspaceSyncDto(
            id = "workspace-sync",
            name = "Sync Workspace",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val upsertWorkspaceResponse = client.put("/api/v1/workspaces/${workspace.id}") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(workspace))
        }
        assertEquals(HttpStatusCode.OK, upsertWorkspaceResponse.status)

        val workspaceListResponse = client.get("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, workspaceListResponse.status)
        val workspaceList = Json.decodeFromString<WorkspaceListResponse>(workspaceListResponse.bodyAsText())
        assertEquals(listOf("workspace-sync"), workspaceList.workspaces.map { it.id })

        val page = PageSyncDto(
            id = "page-sync",
            workspaceId = workspace.id,
            title = "Synced Page",
            content = """{"version":1,"blocks":[]}""",
            sortOrder = 0,
            createdAt = 3L,
            updatedAt = 4L,
        )
        val upsertPageResponse = client.put("/api/v1/pages/${page.id}") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(page))
        }
        assertEquals(HttpStatusCode.OK, upsertPageResponse.status)

        val pageListResponse = client.get("/api/v1/pages?workspaceId=${workspace.id}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, pageListResponse.status)
        val pageList = Json.decodeFromString<PageListResponse>(pageListResponse.bodyAsText())
        assertEquals(listOf("page-sync"), pageList.pages.map { it.id })

        val deleteResponse = client.delete("/api/v1/pages/${page.id}") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val deletedPageListResponse = client.get("/api/v1/pages?workspaceId=${workspace.id}&includeDeleted=true") {
            header(HttpHeaders.Authorization, authHeader)
        }
        val deletedPageList = Json.decodeFromString<PageListResponse>(deletedPageListResponse.bodyAsText())
        assertTrue(deletedPageList.pages.first().deletedAt != null)

        val restoreResponse = client.post("/api/v1/pages/${page.id}/restore") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.NoContent, restoreResponse.status)
    }

    private fun inMemoryTestConfig(): AppConfig {
        return AppConfig(
            database = DatabaseConfig(
                jdbcUrl = null,
                username = null,
                password = null,
                maxPoolSize = 5,
            ),
            jwt = JwtConfig(
                issuer = "test",
                audience = "test",
                realm = "test",
                secret = "test-secret-that-is-long-enough-for-tests",
                expiresInMillis = 60_000L,
            ),
            email = EmailConfig(
                resendApiKey = null,
                from = null,
                replyTo = null,
                appName = "ChangeYourLife",
            ),
            glmApiKey = null,
            geminiApiKey = null,
            openRouterApiKey = null,
            openRouterModel = "test-model",
        )
    }
}
