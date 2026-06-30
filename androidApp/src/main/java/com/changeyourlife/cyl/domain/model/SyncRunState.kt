package com.changeyourlife.cyl.domain.model

data class SyncRunState(
    val isSyncing: Boolean = false,
    val activeOperationName: String = "",
    val lastErrorMessage: String? = null,
    val lastCompletedAt: Long = 0L,
)
