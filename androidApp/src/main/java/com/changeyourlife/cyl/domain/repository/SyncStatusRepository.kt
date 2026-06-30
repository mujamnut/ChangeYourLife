package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.SyncOverview
import kotlinx.coroutines.flow.Flow

interface SyncStatusRepository {
    fun observeOverview(): Flow<SyncOverview>

    fun retryNow()
}
