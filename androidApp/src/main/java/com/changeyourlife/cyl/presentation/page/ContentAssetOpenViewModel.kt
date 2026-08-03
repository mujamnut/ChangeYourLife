package com.changeyourlife.cyl.presentation.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.usecase.asset.ContentAssetOpenResult
import com.changeyourlife.cyl.domain.usecase.asset.ResolveContentAssetOpenTargetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class ContentAssetOpenViewModel @Inject constructor(
    private val resolveContentAsset: ResolveContentAssetOpenTargetUseCase,
) : ViewModel() {
    fun open(
        context: Context,
        attachment: PageMediaAttachment,
    ) {
        val applicationContext = context.applicationContext
        viewModelScope.launch {
            try {
                val target = attachment.assetId.takeIf(String::isNotBlank)?.let { assetId ->
                    resolveContentAsset(assetId)
                }
                when (target) {
                    is ContentAssetOpenResult.Local -> applicationContext.openUri(
                        uri = FileProvider.getUriForFile(
                            applicationContext,
                            "${applicationContext.packageName}.fileprovider",
                            File(target.path),
                        ),
                        mimeType = target.mimeType,
                        grantReadPermission = true,
                    )
                    is ContentAssetOpenResult.Remote -> applicationContext.openUri(
                        uri = Uri.parse(target.url),
                        mimeType = target.mimeType,
                        grantReadPermission = false,
                    )
                    is ContentAssetOpenResult.Failure -> applicationContext.showOpenFailure()
                    null -> {
                        val legacyUri = runCatching { Uri.parse(attachment.uri) }.getOrNull()
                        if (legacyUri == null || legacyUri.scheme.isNullOrBlank()) {
                            applicationContext.showOpenFailure()
                        } else {
                            applicationContext.openUri(
                                uri = legacyUri,
                                mimeType = attachment.mimeType,
                                grantReadPermission = legacyUri.scheme == "content",
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                applicationContext.showOpenFailure()
            }
        }
    }
}

@Composable
internal fun rememberContentAssetOpener(): (PageMediaAttachment) -> Unit {
    val context = LocalContext.current
    val viewModel: ContentAssetOpenViewModel = hiltViewModel()
    return remember(context, viewModel) {
        { attachment -> viewModel.open(context, attachment) }
    }
}

private fun Context.openUri(
    uri: Uri,
    mimeType: String,
    grantReadPermission: Boolean,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (grantReadPermission) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showOpenFailure()
    } catch (_: SecurityException) {
        showOpenFailure()
    }
}

private fun Context.showOpenFailure() {
    Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show()
}
