package com.changeyourlife.cyl.domain.model

data class PageSyncState(
    val status: PageSyncStatus = PageSyncStatus.Synced,
    val remoteUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
) {
    val hasConflict: Boolean
        get() = status == PageSyncStatus.Conflict

    val isPendingPush: Boolean
        get() = status == PageSyncStatus.PendingPush
}

enum class PageSyncStatus {
    Synced,
    PendingPush,
    Conflict,
}
