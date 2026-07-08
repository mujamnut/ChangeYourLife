package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.AppConfig
import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.changeyourlife.cyl.backend.config.EmailConfig
import com.changeyourlife.cyl.backend.config.JwtConfig
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.model.auth.AuthResponse
import com.changeyourlife.cyl.backend.model.auth.ForgotPasswordResponse
import com.changeyourlife.cyl.backend.model.sync.AiActionLogListResponse
import com.changeyourlife.cyl.backend.model.sync.AiActionLogSyncDto
import com.changeyourlife.cyl.backend.model.sync.ChatMessageListResponse
import com.changeyourlife.cyl.backend.model.sync.ChatMessageSyncDto
import com.changeyourlife.cyl.backend.model.sync.ChatSessionListResponse
import com.changeyourlife.cyl.backend.model.sync.ChatSessionSyncDto
import com.changeyourlife.cyl.backend.model.sync.PageBlockCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageBlockPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageElementPositionPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageListResponse
import com.changeyourlife.cyl.backend.model.sync.PagePropertyCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PagePropertyValuePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageSyncDto
import com.changeyourlife.cyl.backend.model.sync.PageTableCellValuePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableColumnCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableColumnPatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTablePatchRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableRowCreateRequest
import com.changeyourlife.cyl.backend.model.sync.PageTableRowPatchRequest
import com.changeyourlife.cyl.backend.model.sync.WorkspaceListResponse
import com.changeyourlife.cyl.backend.model.sync.WorkspaceSyncDto
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
            module(appConfig = inMemoryTestConfig())
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun aiStatusReturnsSandboxModeByDefault() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
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
    fun chatActionsReturnsActionSchemaContract() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }
        val authHeader = registerAndReturnAuthHeader(
            email = "ai-contract@example.com",
            password = "strong-password",
        )

        val response = client.post("/ai/chat-actions") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "messages": [
                    {
                      "role": "user",
                      "content": "hello"
                    }
                  ],
                  "pages": []
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ChatWithActionsResponse>(response.bodyAsText())
        assertEquals("CYL_ACTION_SCHEMA", body.schemaName)
        assertEquals(1, body.schemaVersion)
        assertTrue(body.reply.isNotBlank())
        assertEquals(emptyList(), body.actions)
        assertEquals(emptyList(), body.validationIssues)
    }

    @Test
    fun registerLoginAndMeReturnAuthenticatedUser() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
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

    @Test
    fun authenticatedAiActionLogSyncRoundTripIsUserScoped() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val firstUserAuth = registerAndReturnAuthHeader(
            email = "ai-log-owner-a@example.com",
            password = "sync-password",
        )
        val secondUserAuth = registerAndReturnAuthHeader(
            email = "ai-log-owner-b@example.com",
            password = "sync-password",
        )

        val actionLog = AiActionLogSyncDto(
            auditId = "audit-sync-1",
            requestMessageId = "request-1",
            responseMessageId = "response-1",
            sessionId = "session-1",
            workspaceId = "workspace-sync",
            mode = "Edit",
            provider = "openrouter",
            model = "openai/gpt-oss-20b:free",
            schemaName = "CYL_ACTION_SCHEMA",
            schemaVersion = 2,
            proposedActionsJson = """[{"type":"ADD_TABLE_ROW"}]""",
            executedActionsJson = """[{"type":"ADD_TABLE_ROW"}]""",
            validationIssuesJson = "[]",
            executionMessagesJson = """["Done"]""",
            undoCommandsJson = """[{"commandType":"DeleteBlock"}]""",
            undoState = "Available",
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val upsertResponse = client.put("/api/v1/ai-action-logs/${actionLog.auditId}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(actionLog))
        }
        assertEquals(HttpStatusCode.OK, upsertResponse.status)

        val ownerListResponse = client.get("/api/v1/ai-action-logs?workspaceId=${actionLog.workspaceId}") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        assertEquals(HttpStatusCode.OK, ownerListResponse.status)
        val ownerList = Json.decodeFromString<AiActionLogListResponse>(ownerListResponse.bodyAsText())
        assertEquals(listOf(actionLog.auditId), ownerList.actionLogs.map { it.auditId })
        assertEquals("Available", ownerList.actionLogs.single().undoState)

        val otherUserListResponse = client.get("/api/v1/ai-action-logs?workspaceId=${actionLog.workspaceId}") {
            header(HttpHeaders.Authorization, secondUserAuth)
        }
        assertEquals(HttpStatusCode.OK, otherUserListResponse.status)
        val otherUserList = Json.decodeFromString<AiActionLogListResponse>(otherUserListResponse.bodyAsText())
        assertTrue(otherUserList.actionLogs.isEmpty())

        val updatedLog = actionLog.copy(undoState = "Applied", updatedAt = 2000L)
        val updateResponse = client.put("/api/v1/ai-action-logs/${actionLog.auditId}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(updatedLog))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        val updatedListResponse = client.get("/api/v1/ai-action-logs?workspaceId=${actionLog.workspaceId}&updatedAfter=1500") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        val updatedList = Json.decodeFromString<AiActionLogListResponse>(updatedListResponse.bodyAsText())
        assertEquals(listOf("Applied"), updatedList.actionLogs.map { it.undoState })
    }

    @Test
    fun authenticatedChatHistorySyncRoundTripIsUserScoped() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val firstUserAuth = registerAndReturnAuthHeader(
            email = "chat-sync-owner-a@example.com",
            password = "sync-password",
        )
        val secondUserAuth = registerAndReturnAuthHeader(
            email = "chat-sync-owner-b@example.com",
            password = "sync-password",
        )

        val session = ChatSessionSyncDto(
            id = "session-sync-1",
            scopeId = "home:workspace-sync",
            title = "Budget chat",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val upsertSessionResponse = client.put("/api/v1/chat-sessions/${session.id}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(session))
        }
        assertEquals(HttpStatusCode.OK, upsertSessionResponse.status)

        val message = ChatMessageSyncDto(
            id = "message-sync-1",
            sessionId = session.id,
            scopeId = session.scopeId,
            role = "user",
            content = "buat budget bulan 7",
            pageLinksJson = "[]",
            actionMetadataJson = "",
            createdAt = 1100L,
            updatedAt = 1100L,
        )
        val upsertMessageResponse = client.put("/api/v1/chat-messages/${message.id}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(message))
        }
        assertEquals(HttpStatusCode.OK, upsertMessageResponse.status)

        val ownerSessionListResponse = client.get("/api/v1/chat-sessions?scopeId=${session.scopeId}") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        assertEquals(HttpStatusCode.OK, ownerSessionListResponse.status)
        val ownerSessions = Json.decodeFromString<ChatSessionListResponse>(ownerSessionListResponse.bodyAsText())
        assertEquals(listOf(session.id), ownerSessions.sessions.map { it.id })

        val ownerMessageListResponse = client.get("/api/v1/chat-sessions/${session.id}/messages") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        assertEquals(HttpStatusCode.OK, ownerMessageListResponse.status)
        val ownerMessages = Json.decodeFromString<ChatMessageListResponse>(ownerMessageListResponse.bodyAsText())
        assertEquals(listOf("buat budget bulan 7"), ownerMessages.messages.map { it.content })

        val otherUserSessionListResponse = client.get("/api/v1/chat-sessions?scopeId=${session.scopeId}") {
            header(HttpHeaders.Authorization, secondUserAuth)
        }
        assertEquals(HttpStatusCode.OK, otherUserSessionListResponse.status)
        val otherUserSessions = Json.decodeFromString<ChatSessionListResponse>(otherUserSessionListResponse.bodyAsText())
        assertTrue(otherUserSessions.sessions.isEmpty())

        val otherUserCannotUpsertMessage = client.put("/api/v1/chat-messages/message-sync-2") {
            header(HttpHeaders.Authorization, secondUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(message.copy(id = "message-sync-2")))
        }
        assertEquals(HttpStatusCode.Forbidden, otherUserCannotUpsertMessage.status)

        val deletedSession = session.copy(deletedAt = 2000L, updatedAt = 2000L)
        val deleteSessionResponse = client.put("/api/v1/chat-sessions/${session.id}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(deletedSession))
        }
        assertEquals(HttpStatusCode.OK, deleteSessionResponse.status)

        val updatedSessionListResponse = client.get("/api/v1/chat-sessions?scopeId=${session.scopeId}&updatedAfter=1500") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        val updatedSessions = Json.decodeFromString<ChatSessionListResponse>(updatedSessionListResponse.bodyAsText())
        assertEquals(listOf(2000L), updatedSessions.sessions.map { it.deletedAt })
    }

    @Test
    fun pageGranularPatchRoutesUpdateContentJson() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val authHeader = registerAndReturnAuthHeader(
            email = "granular-sync@example.com",
            password = "sync-password",
        )
        val workspace = WorkspaceSyncDto(
            id = "workspace-granular",
            name = "Granular Workspace",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/workspaces/${workspace.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(workspace))
            }.status,
        )

        val page = PageSyncDto(
            id = "page-granular",
            workspaceId = workspace.id,
            title = "Granular Page",
            content = """
                {
                  "version": 1,
                  "properties": [
                    { "id": "property-mood", "name": "Mood", "type": "Text", "value": "Old" }
                  ],
                  "blocks": [
                    { "id": "block-note", "type": "Text", "text": "Before" },
                    {
                      "id": "block-table",
                      "type": "DatabaseTable",
                      "table": {
                        "title": "Budget",
                        "columns": [
                          { "id": "column-amount", "name": "Amount", "type": "Number" }
                        ],
                        "rows": [
                          { "id": "row-food", "cells": { "column-amount": "4" } }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sortOrder = 0,
            createdAt = 2L,
            updatedAt = 2L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/pages/${page.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(page))
            }.status,
        )

        val blockResponse = client.patch("/api/v1/pages/${page.id}/blocks/block-note") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageBlockPatchRequest(
                        text = "After",
                        richTextSpans = buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("start", 0)
                                    put("end", 5)
                                    put("bold", true)
                                },
                            )
                        },
                        mediaAttachments = buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("id", "media-1")
                                    put("uri", "content://file/1")
                                    put("name", "receipt.png")
                                    put("mimeType", "image/png")
                                    put("sizeBytes", 123L)
                                },
                            )
                        },
                        isChecked = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, blockResponse.status)
        val blockPage = Json.decodeFromString<PageSyncDto>(blockResponse.bodyAsText())
        assertTrue(blockPage.content.contains("\"text\":\"After\""), blockPage.content)
        assertTrue(blockPage.content.contains("\"richTextSpans\""), blockPage.content)
        assertTrue(blockPage.content.contains("\"mediaAttachments\""), blockPage.content)
        assertTrue(blockPage.content.contains("\"isChecked\":true"), blockPage.content)

        val propertyResponse = client.patch("/api/v1/pages/${page.id}/properties") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PagePropertyValuePatchRequest(
                        propertyName = "Mood",
                        value = "Good",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, propertyResponse.status)
        val propertyPage = Json.decodeFromString<PageSyncDto>(propertyResponse.bodyAsText())
        assertTrue(propertyPage.content.contains("\"value\":\"Good\""), propertyPage.content)

        val cellResponse = client.patch("/api/v1/pages/${page.id}/table-cells") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableCellValuePatchRequest(
                        rowId = "row-food",
                        columnId = "column-amount",
                        value = "5",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, cellResponse.status)
        val cellPage = Json.decodeFromString<PageSyncDto>(cellResponse.bodyAsText())
        assertTrue(cellPage.content.contains("\"column-amount\":\"5\""), cellPage.content)
    }

    @Test
    fun pageGranularTableAndColumnPatchRoutesUpdateContentJson() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val authHeader = registerAndReturnAuthHeader(
            email = "granular-table-config@example.com",
            password = "sync-password",
        )
        val workspace = WorkspaceSyncDto(
            id = "workspace-table-config",
            name = "Table Config Workspace",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/workspaces/${workspace.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(workspace))
            }.status,
        )

        val page = PageSyncDto(
            id = "page-table-config",
            workspaceId = workspace.id,
            title = "Table Config Page",
            content = """
                {
                  "version": 1,
                  "properties": [],
                  "blocks": [
                    {
                      "id": "block-table",
                      "type": "DatabaseTable",
                      "table": {
                        "title": "Budget",
                        "view": "Table",
                        "columns": [
                          { "id": "column-name", "name": "Name", "type": "Text" },
                          { "id": "column-date", "name": "Date", "type": "Date" }
                        ],
                        "rows": [
                          { "id": "row-food", "cells": { "column-name": "Food", "column-date": "2026-06-30" } }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sortOrder = 0,
            createdAt = 2L,
            updatedAt = 2L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/pages/${page.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(page))
            }.status,
        )

        val tableResponse = client.patch("/api/v1/pages/${page.id}/tables/block-table") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTablePatchRequest(
                        title = "Expenses",
                        view = "Calendar",
                        calendarDateColumnId = "column-date",
                        sortColumnId = "column-name",
                        sortDirection = "Descending",
                        filterColumnId = "column-name",
                        filterQuery = "Food",
                        groupByColumnId = "column-date",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, tableResponse.status)
        val tableContent = Json.decodeFromString<PageSyncDto>(tableResponse.bodyAsText()).content
        assertTrue(tableContent.contains("\"title\":\"Expenses\""), tableContent)
        assertTrue(tableContent.contains("\"view\":\"Calendar\""), tableContent)
        assertTrue(tableContent.contains("\"calendarDateColumnId\":\"column-date\""), tableContent)
        assertTrue(tableContent.contains("\"direction\":\"Descending\""), tableContent)
        assertTrue(tableContent.contains("\"query\":\"Food\""), tableContent)
        assertTrue(tableContent.contains("\"groupByColumnId\":\"column-date\""), tableContent)

        val columnResponse = client.patch("/api/v1/pages/${page.id}/tables/block-table/columns/column-date") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableColumnPatchRequest(
                        name = "Deadline",
                        dateFormat = "MonthDayYear",
                        timeFormat = "TwelveHour",
                        dateReminder = "AtTimeOfEvent",
                        timezoneLabel = "GMT+8",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, columnResponse.status)
        val columnContent = Json.decodeFromString<PageSyncDto>(columnResponse.bodyAsText()).content
        assertTrue(columnContent.contains("\"name\":\"Deadline\""), columnContent)
        assertTrue(columnContent.contains("\"dateFormat\":\"MonthDayYear\""), columnContent)
        assertTrue(columnContent.contains("\"timeFormat\":\"TwelveHour\""), columnContent)
        assertTrue(columnContent.contains("\"dateReminder\":\"AtTimeOfEvent\""), columnContent)
        assertTrue(columnContent.contains("\"timezoneLabel\":\"GMT+8\""), columnContent)
    }

    @Test
    fun pageGranularCreateDeleteAndReorderRoutesUpdateContentJson() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val authHeader = registerAndReturnAuthHeader(
            email = "granular-mutations@example.com",
            password = "sync-password",
        )
        val workspace = WorkspaceSyncDto(
            id = "workspace-mutations",
            name = "Mutation Workspace",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/workspaces/${workspace.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(workspace))
            }.status,
        )

        val page = PageSyncDto(
            id = "page-mutations",
            workspaceId = workspace.id,
            title = "Mutation Page",
            content = """
                {
                  "version": 1,
                  "properties": [
                    { "id": "property-existing", "name": "Existing", "type": "Text", "value": "A" }
                  ],
                  "blocks": [
                    { "id": "block-first", "type": "Text", "text": "First" },
                    {
                      "id": "block-table",
                      "type": "DatabaseTable",
                      "table": {
                        "title": "Budget",
                        "columns": [
                          { "id": "column-name", "name": "Name", "type": "Text" }
                        ],
                        "rows": [
                          { "id": "row-first", "cells": { "column-name": "Food" } }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sortOrder = 0,
            createdAt = 2L,
            updatedAt = 2L,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/pages/${page.id}") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(page))
            }.status,
        )

        val addBlockResponse = client.post("/api/v1/pages/${page.id}/blocks") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageBlockCreateRequest(
                        blockId = "block-second",
                        type = "Text",
                        text = "Second",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, addBlockResponse.status)
        assertTrue(addBlockResponse.bodyAsText().contains("block-second"))

        val moveBlockResponse = client.patch("/api/v1/pages/${page.id}/blocks/block-second/position") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PageElementPositionPatchRequest(targetIndex = 0)))
        }
        assertEquals(HttpStatusCode.OK, moveBlockResponse.status)
        val movedBlockContent = Json.decodeFromString<PageSyncDto>(moveBlockResponse.bodyAsText()).content
        assertTrue(movedBlockContent.indexOf("block-second") < movedBlockContent.indexOf("block-first"), movedBlockContent)

        val addPropertyResponse = client.post("/api/v1/pages/${page.id}/properties") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PagePropertyCreateRequest(
                        propertyId = "property-created",
                        name = "Created",
                        value = "B",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, addPropertyResponse.status)
        assertTrue(addPropertyResponse.bodyAsText().contains("property-created"))

        val movePropertyResponse = client.patch("/api/v1/pages/${page.id}/properties/property-created/position") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PageElementPositionPatchRequest(targetIndex = 0)))
        }
        assertEquals(HttpStatusCode.OK, movePropertyResponse.status)
        val movedPropertyContent = Json.decodeFromString<PageSyncDto>(movePropertyResponse.bodyAsText()).content
        assertTrue(
            movedPropertyContent.indexOf("property-created") < movedPropertyContent.indexOf("property-existing"),
            movedPropertyContent,
        )

        val addColumnResponse = client.post("/api/v1/pages/${page.id}/tables/block-table/columns") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableColumnCreateRequest(
                        columnId = "column-amount",
                        name = "Amount",
                        type = "Number",
                        cellValues = mapOf("row-first" to "4"),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, addColumnResponse.status)
        val addedColumnContent = Json.decodeFromString<PageSyncDto>(addColumnResponse.bodyAsText()).content
        assertTrue(addedColumnContent.contains("column-amount"), addedColumnContent)
        assertTrue(addedColumnContent.contains("\"column-amount\":\"4\""), addedColumnContent)

        val addNotesColumnResponse = client.post("/api/v1/pages/${page.id}/tables/block-table/columns") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableColumnCreateRequest(
                        columnId = "column-notes",
                        name = "Notes",
                        type = "Text",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, addNotesColumnResponse.status)

        val moveColumnResponse = client.patch("/api/v1/pages/${page.id}/tables/block-table/columns/column-amount/position") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PageElementPositionPatchRequest(targetIndex = 2)))
        }
        assertEquals(HttpStatusCode.OK, moveColumnResponse.status)
        val movedColumnContent = Json.decodeFromString<PageSyncDto>(moveColumnResponse.bodyAsText()).content
        assertTrue(movedColumnContent.indexOf("column-name") < movedColumnContent.indexOf("column-notes"), movedColumnContent)
        assertTrue(movedColumnContent.indexOf("column-notes") < movedColumnContent.indexOf("column-amount"), movedColumnContent)

        val addRowResponse = client.post("/api/v1/pages/${page.id}/tables/block-table/rows") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableRowCreateRequest(
                        rowId = "row-second",
                        cells = mapOf("column-name" to "Fuel", "column-amount" to "5"),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, addRowResponse.status)
        assertTrue(addRowResponse.bodyAsText().contains("row-second"))

        val updateRowPageResponse = client.patch("/api/v1/pages/${page.id}/tables/block-table/rows/row-second") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    PageTableRowPatchRequest(
                        blocks = buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("id", "row-block-note")
                                    put("type", "Text")
                                    put("text", "Row notes")
                                },
                            )
                        },
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, updateRowPageResponse.status)
        val rowPageContent = Json.decodeFromString<PageSyncDto>(updateRowPageResponse.bodyAsText()).content
        assertTrue(rowPageContent.contains("row-block-note"), rowPageContent)
        assertTrue(rowPageContent.contains("\"text\":\"Row notes\""), rowPageContent)

        val moveRowResponse = client.patch("/api/v1/pages/${page.id}/tables/block-table/rows/row-second/position") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PageElementPositionPatchRequest(targetIndex = 0)))
        }
        assertEquals(HttpStatusCode.OK, moveRowResponse.status)
        val movedRowContent = Json.decodeFromString<PageSyncDto>(moveRowResponse.bodyAsText()).content
        assertTrue(movedRowContent.indexOf("row-second") < movedRowContent.indexOf("row-first"), movedRowContent)

        assertEquals(
            HttpStatusCode.OK,
            client.delete("/api/v1/pages/${page.id}/tables/block-table/rows/row-second") {
                header(HttpHeaders.Authorization, authHeader)
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.delete("/api/v1/pages/${page.id}/tables/block-table/columns/column-amount") {
                header(HttpHeaders.Authorization, authHeader)
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.delete("/api/v1/pages/${page.id}/properties/property-created") {
                header(HttpHeaders.Authorization, authHeader)
            }.status,
        )
        val deleteBlockResponse = client.delete("/api/v1/pages/${page.id}/blocks/block-second") {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, deleteBlockResponse.status)
        val finalContent = Json.decodeFromString<PageSyncDto>(deleteBlockResponse.bodyAsText()).content
        assertTrue(!finalContent.contains("block-second"), finalContent)
        assertTrue(!finalContent.contains("property-created"), finalContent)
        assertTrue(!finalContent.contains("column-amount"), finalContent)
        assertTrue(!finalContent.contains("row-second"), finalContent)
    }

    @Test
    fun syncRoutesKeepWorkspaceAndPageOwnershipIsolated() = testApplication {
        application {
            module(appConfig = inMemoryTestConfig())
        }

        val firstUserAuth = registerAndReturnAuthHeader(
            email = "sync-owner-a@example.com",
            password = "sync-password",
        )
        val secondUserAuth = registerAndReturnAuthHeader(
            email = "sync-owner-b@example.com",
            password = "sync-password",
        )

        val sharedClientWorkspaceId = "local-default-workspace"
        val firstWorkspace = WorkspaceSyncDto(
            id = sharedClientWorkspaceId,
            name = "First User Workspace",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val secondWorkspace = WorkspaceSyncDto(
            id = sharedClientWorkspaceId,
            name = "Second User Workspace",
            createdAt = 2L,
            updatedAt = 2L,
        )

        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/workspaces/$sharedClientWorkspaceId") {
                header(HttpHeaders.Authorization, firstUserAuth)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(firstWorkspace))
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.put("/api/v1/workspaces/$sharedClientWorkspaceId") {
                header(HttpHeaders.Authorization, secondUserAuth)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(secondWorkspace))
            }.status,
        )

        val firstPage = PageSyncDto(
            id = "first-user-page",
            workspaceId = sharedClientWorkspaceId,
            title = "Private Page",
            content = """{"version":1,"blocks":[]}""",
            sortOrder = 0,
            createdAt = 3L,
            updatedAt = 3L,
        )
        val firstPageResponse = client.put("/api/v1/pages/${firstPage.id}") {
            header(HttpHeaders.Authorization, firstUserAuth)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(firstPage))
        }
        assertEquals(HttpStatusCode.OK, firstPageResponse.status)

        val secondUserCannotReadFirstPage = client.get("/api/v1/pages/${firstPage.id}") {
            header(HttpHeaders.Authorization, secondUserAuth)
        }
        assertEquals(HttpStatusCode.NotFound, secondUserCannotReadFirstPage.status)

        val firstUserPagesResponse = client.get("/api/v1/pages?workspaceId=$sharedClientWorkspaceId") {
            header(HttpHeaders.Authorization, firstUserAuth)
        }
        val secondUserPagesResponse = client.get("/api/v1/pages?workspaceId=$sharedClientWorkspaceId") {
            header(HttpHeaders.Authorization, secondUserAuth)
        }
        val firstUserPages = Json.decodeFromString<PageListResponse>(firstUserPagesResponse.bodyAsText())
        val secondUserPages = Json.decodeFromString<PageListResponse>(secondUserPagesResponse.bodyAsText())

        assertEquals(listOf("first-user-page"), firstUserPages.pages.map { it.id })
        assertTrue(secondUserPages.pages.isEmpty())
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.registerAndReturnAuthHeader(
        email: String,
        password: String,
    ): String {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "email": "$email",
                  "password": "$password",
                  "displayName": "Sync Owner"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val authResponse = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        return "Bearer ${authResponse.token}"
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
            openAiApiKey = null,
            openAiModel = "test-openai-model",
            openAiVisionModels = listOf("test-openai-vision-model"),
            glmApiKey = null,
            geminiApiKey = null,
            openRouterApiKey = null,
            openRouterModel = "test-model",
            openRouterVisionModels = listOf("test-vision-model"),
        )
    }
}
