package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyRequest
import com.changeyourlife.cyl.domain.model.LocalContentAssetCopyResult

interface ContentAssetLocalStore {
    suspend fun copyIntoAssetStorage(
        request: LocalContentAssetCopyRequest,
    ): LocalContentAssetCopyResult

    suspend fun delete(localPath: String): Boolean

    suspend fun isAvailable(localPath: String): Boolean
}
