package com.changeyourlife.cyl.backend.storage

import com.changeyourlife.cyl.backend.domain.PrivateAssetMetadata
import com.changeyourlife.cyl.backend.domain.PrivateAssetDigest
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadHandle
import com.changeyourlife.cyl.backend.domain.PrivateAssetReadRequest
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorage
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageError
import com.changeyourlife.cyl.backend.domain.PrivateAssetStorageResult
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadIntent
import com.changeyourlife.cyl.backend.domain.PrivateAssetUploadRequest

class UnavailablePrivateAssetStorage : PrivateAssetStorage {
    override suspend fun createUploadIntent(
        request: PrivateAssetUploadRequest,
    ): PrivateAssetStorageResult<PrivateAssetUploadIntent> = unavailable()

    override suspend fun head(storageKey: String): PrivateAssetStorageResult<PrivateAssetMetadata> = unavailable()

    override suspend fun calculateDigest(
        storageKey: String,
        maxBytes: Long,
    ): PrivateAssetStorageResult<PrivateAssetDigest> = unavailable()

    override suspend fun createReadHandle(
        request: PrivateAssetReadRequest,
    ): PrivateAssetStorageResult<PrivateAssetReadHandle> = unavailable()

    override suspend fun delete(storageKey: String): PrivateAssetStorageResult<Unit> = unavailable()

    private fun unavailable(): PrivateAssetStorageResult.Failure = PrivateAssetStorageResult.Failure(
        error = PrivateAssetStorageError.Unavailable,
        developerMessage = "Private asset storage is not configured.",
    )
}
