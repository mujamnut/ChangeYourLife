package com.changeyourlife.cyl.data.sync

import javax.inject.Inject

interface ChatSyncScheduler {
    fun pushSession(sessionId: String)
    fun pushMessage(messageId: String)
    fun pushPending()
}

class BackgroundChatSyncScheduler @Inject constructor(
    private val backgroundSyncQueue: BackgroundSyncQueue,
) : ChatSyncScheduler {
    override fun pushSession(sessionId: String) {
        backgroundSyncQueue.enqueue("pushChatSession") {
            pushChatSession(sessionId)
        }
    }

    override fun pushMessage(messageId: String) {
        backgroundSyncQueue.enqueue("pushChatMessage") {
            pushChatMessage(messageId)
        }
    }

    override fun pushPending() {
        backgroundSyncQueue.enqueue("pushPendingChat") {
            pushPendingChanges()
        }
    }
}
