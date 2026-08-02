package com.changeyourlife.cyl.presentation.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.ChatAudioPlaybackState

@Composable
internal fun AiVoiceComposer(
    state: VoiceNoteUiState,
    playbackState: ChatAudioPlaybackState,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onTogglePlayback: () -> Unit,
    onDelete: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.phase) {
            VoiceComposerPhase.Starting,
            VoiceComposerPhase.Finishing,
            VoiceComposerPhase.RequestingPermission,
            -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = when (state.phase) {
                        VoiceComposerPhase.Finishing -> "Preparing voice note"
                        VoiceComposerPhase.RequestingPermission -> "Microphone permission"
                        else -> "Starting recorder"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VoiceComposerPhase.Recording -> {
                IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Cancel recording")
                }
                Text(
                    text = state.elapsedMs.toVoiceDuration(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AiVoiceWaveform(
                    waveform = state.waveform,
                    progress = 1f,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                )
                IconButton(onClick = onStop, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop recording")
                }
            }
            VoiceComposerPhase.Recorded -> {
                val draft = state.draft
                val isPlaying = playbackState.attachmentId == draft?.id && playbackState.isPlaying
                IconButton(onClick = onTogglePlayback, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    )
                }
                AiVoiceWaveform(
                    waveform = draft?.waveform.orEmpty(),
                    progress = if (playbackState.attachmentId == draft?.id) {
                        playbackState.progressRatio()
                    } else {
                        0f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                )
                Text(
                    text = (draft?.durationMs ?: state.elapsedMs).toVoiceDuration(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete voice note")
                }
            }
            VoiceComposerPhase.PermissionDenied -> {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = "Microphone access is required",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = if (state.permissionPermanentlyDenied) onOpenPermissionSettings else onRetry,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (state.permissionPermanentlyDenied) {
                            Icons.Rounded.Settings
                        } else {
                            Icons.Rounded.Mic
                        },
                        contentDescription = if (state.permissionPermanentlyDenied) {
                            "Open microphone settings"
                        } else {
                            "Request microphone permission"
                        },
                    )
                }
            }
            VoiceComposerPhase.Failed -> {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = state.errorCode.toVoiceErrorLabel(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                IconButton(onClick = onRetry, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Record again")
                }
            }
            VoiceComposerPhase.Idle -> Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

@Composable
internal fun AiVoiceMessage(
    attachment: AiChatAttachment,
    playbackState: ChatAudioPlaybackState,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = playbackState.attachmentId == attachment.id
    val isPlaying = active && playbackState.isPlaying
    val uploadLabel = attachment.voiceUploadLabel()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlayback, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AiVoiceWaveform(
                waveform = attachment.waveform,
                progress = if (active) playbackState.progressRatio() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            )
            uploadLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attachment.status == "permanent_failure") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
        Text(
            text = (attachment.durationMs ?: 0L).toVoiceDuration(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiVoiceWaveform(
    waveform: List<Int>,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val samples = waveform.ifEmpty { DefaultWaveform }
    Canvas(modifier = modifier) {
        val count = samples.size.coerceAtMost(40)
        if (count <= 0) return@Canvas
        val step = size.width / count.toFloat()
        val activeUntil = (progress.coerceIn(0f, 1f) * count).toInt()
        samples.takeLast(count).forEachIndexed { index, sample ->
            val heightRatio = (sample.coerceIn(6, 100) / 100f).coerceAtLeast(0.12f)
            val barHeight = size.height * heightRatio
            val x = step * index + step / 2f
            drawLine(
                color = if (index < activeUntil) activeColor else inactiveColor,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = (step * 0.34f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun ChatAudioPlaybackState.progressRatio(): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun Long.toVoiceDuration(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun String?.toVoiceErrorLabel(): String = when (this) {
    "recording_too_short" -> "Hold a little longer and try again"
    "audio_limit_exceeded" -> "Voice note exceeded the 5 minute limit"
    else -> "Recording failed. Try again"
}

private fun AiChatAttachment.voiceUploadLabel(): String? = when (status) {
    "local_ready" -> "Saved on this device"
    "upload_queued" -> "Waiting to upload"
    "uploading" -> "Uploading ${progressPercent.coerceIn(0, 99)}%"
    "pending_upload" -> "Finishing upload"
    "retryable_failure" -> "Upload paused - retrying"
    "permanent_failure" -> "Upload failed"
    else -> null
}

private val DefaultWaveform = listOf(18, 32, 50, 28, 68, 42, 76, 34, 58, 24, 46, 20)
