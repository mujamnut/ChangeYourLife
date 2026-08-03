package com.changeyourlife.cyl.data.asset

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.changeyourlife.cyl.domain.usecase.asset.ContentAssetUploadWorkResult
import com.changeyourlife.cyl.domain.usecase.asset.UploadContentAssetUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class ContentAssetUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val assetId = inputData.getString(ContentAssetUploadWork.InputAssetId)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ContentAssetUploadWorkerEntryPoint::class.java,
        )
        return try {
            when (val result = entryPoint.uploadContentAssetUseCase()(assetId)) {
                ContentAssetUploadWorkResult.Completed,
                ContentAssetUploadWorkResult.NoWork -> Result.success()
                is ContentAssetUploadWorkResult.Retry -> Result.retry()
                is ContentAssetUploadWorkResult.PermanentFailure -> Result.failure(
                    workDataOf(ContentAssetUploadWork.OutputErrorCode to result.errorCode),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContentAssetUploadWorkerEntryPoint {
        fun uploadContentAssetUseCase(): UploadContentAssetUseCase
    }
}

internal object ContentAssetUploadWork {
    const val InputAssetId = "assetId"
    const val OutputErrorCode = "errorCode"
    const val WorkNamePrefix = "cyl-content-asset-upload:"
    const val WorkTag = "CYL_CONTENT_ASSET_UPLOAD"
}
