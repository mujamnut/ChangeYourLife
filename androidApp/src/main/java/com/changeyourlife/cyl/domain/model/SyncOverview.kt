package com.changeyourlife.cyl.domain.model

data class SyncOverview(
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastErrorMessage: String? = null,
    val lastCompletedAt: Long = 0L,
) {
    val hasPending: Boolean
        get() = pendingCount > 0

    val hasConflict: Boolean
        get() = conflictCount > 0

    val hasError: Boolean
        get() = !lastErrorMessage.isNullOrBlank()

    val isClean: Boolean
        get() = !isSyncing && !hasPending && !hasConflict && !hasError
}
