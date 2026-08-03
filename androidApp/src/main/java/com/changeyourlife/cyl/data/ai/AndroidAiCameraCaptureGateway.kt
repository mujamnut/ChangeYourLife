package com.changeyourlife.cyl.data.ai

import android.content.Context
import androidx.core.content.FileProvider
import com.changeyourlife.cyl.domain.repository.AiCameraCaptureGateway
import com.changeyourlife.cyl.domain.repository.AiCameraCaptureTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAiCameraCaptureGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AiCameraCaptureGateway {
    override suspend fun createTarget(): AiCameraCaptureTarget? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, CameraDirectoryPath)
            check(directory.exists() || directory.mkdirs())
            check(directory.isDirectory)
            val file = File(directory, "camera-${UUID.randomUUID()}.jpg")
            check(file.createNewFile())
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            AiCameraCaptureTarget(uri = uri.toString(), localPath = file.absolutePath)
        }.getOrNull()
    }

    override suspend fun discard(target: AiCameraCaptureTarget) = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.filesDir, CameraDirectoryPath).canonicalFile
            val candidate = File(target.localPath).canonicalFile
            if (
                candidate.path.startsWith(root.path + File.separator) &&
                candidate.isFile
            ) {
                candidate.delete()
            }
        }
        Unit
    }
}

private const val CameraDirectoryPath = "content_assets/camera"
