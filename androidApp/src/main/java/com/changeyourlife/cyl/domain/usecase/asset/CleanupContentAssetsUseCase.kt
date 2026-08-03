package com.changeyourlife.cyl.domain.usecase.asset

import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class CleanupContentAssetsUseCase @Inject constructor(
    private val repository: ContentAssetRepository,
    private val localStore: ContentAssetLocalStore,
) {
    suspend operator fun invoke(deletedBefore: Long): ContentAssetCleanupResult {
        var deletedRecords = 0
        var failedRecords = 0
        repository.getCleanupCandidates(deletedBefore).forEach { asset ->
            try {
                val localDeleted = asset.localPath
                    ?.let { path -> localStore.delete(path) }
                    ?: true
                if (localDeleted) {
                    repository.deleteRecord(asset.id)
                    deletedRecords += 1
                } else {
                    failedRecords += 1
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failedRecords += 1
            }
        }
        return ContentAssetCleanupResult(
            deletedRecords = deletedRecords,
            failedRecords = failedRecords,
        )
    }
}

data class ContentAssetCleanupResult(
    val deletedRecords: Int,
    val failedRecords: Int,
)
