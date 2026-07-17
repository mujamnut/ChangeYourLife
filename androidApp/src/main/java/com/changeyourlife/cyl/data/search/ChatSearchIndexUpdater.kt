package com.changeyourlife.cyl.data.search

interface ChatSearchIndexUpdater {
    suspend fun rebuildChatScope(scopeId: String)

    suspend fun rebuildChatSession(sessionId: String)
}
