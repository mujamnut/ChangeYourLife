package com.changeyourlife.cyl.data.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.changeyourlife.cyl.aicontract.ChatAttachmentErrorCode
import com.changeyourlife.cyl.domain.model.ChatAttachment
import com.changeyourlife.cyl.domain.model.ChatAudioPlaybackState
import com.changeyourlife.cyl.domain.repository.ChatAudioPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Media3ChatAudioPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : ChatAudioPlayer {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ChatAudioPlaybackState())

    override val state: StateFlow<ChatAudioPlaybackState> = mutableState.asStateFlow()

    private var player: ExoPlayer? = null
    private var activeAttachment: ChatAttachment? = null
    private var progressJob: Job? = null

    override fun toggle(attachment: ChatAttachment) = onMain {
        val activePlayer = ensurePlayer()
        if (activeAttachment?.id == attachment.id) {
            if (activePlayer.isPlaying) {
                activePlayer.pause()
            } else {
                if (activePlayer.playbackState == Player.STATE_ENDED) activePlayer.seekTo(0L)
                activePlayer.play()
            }
            return@onMain
        }

        val localPath = attachment.localPath?.takeIf(String::isNotBlank)
        val file = localPath?.let(::File)?.takeIf(File::isFile)
        if (file == null) {
            mutableState.value = ChatAudioPlaybackState(
                attachmentId = attachment.id,
                durationMs = attachment.durationMs ?: 0L,
                errorCode = ChatAttachmentErrorCode.PlaybackFailed.wireValue,
            )
            return@onMain
        }

        activeAttachment = attachment
        mutableState.value = ChatAudioPlaybackState(
            attachmentId = attachment.id,
            durationMs = attachment.durationMs ?: 0L,
        )
        activePlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        activePlayer.prepare()
        activePlayer.playWhenReady = true
    }

    override fun pause() = onMain { player?.pause() }

    override fun stop() = onMain {
        player?.stop()
        progressJob?.cancel()
        progressJob = null
        activeAttachment = null
        mutableState.value = ChatAudioPlaybackState()
    }

    override fun release() = onMain {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
        activeAttachment = null
        mutableState.value = ChatAudioPlaybackState()
    }

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(appContext)
        .build()
        .also { created ->
            created.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateFromPlayer(created, isPlaying)
                        if (isPlaying) startProgressUpdates(created) else progressJob?.cancel()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateFromPlayer(created, created.isPlaying)
                        if (playbackState == Player.STATE_ENDED) {
                            mutableState.value = mutableState.value.copy(
                                isPlaying = false,
                                positionMs = resolvedDuration(created),
                            )
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        progressJob?.cancel()
                        mutableState.value = mutableState.value.copy(
                            isPlaying = false,
                            errorCode = ChatAttachmentErrorCode.PlaybackFailed.wireValue,
                        )
                    }
                },
            )
            player = created
        }

    private fun startProgressUpdates(activePlayer: Player) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && activePlayer.isPlaying) {
                updateFromPlayer(activePlayer, isPlaying = true)
                delay(100L)
            }
        }
    }

    private fun updateFromPlayer(activePlayer: Player, isPlaying: Boolean) {
        val attachment = activeAttachment ?: return
        mutableState.value = ChatAudioPlaybackState(
            attachmentId = attachment.id,
            isPlaying = isPlaying,
            positionMs = activePlayer.currentPosition.coerceAtLeast(0L),
            durationMs = resolvedDuration(activePlayer),
        )
    }

    private fun resolvedDuration(activePlayer: Player): Long {
        val playerDuration = activePlayer.duration
        return if (playerDuration > 0L) playerDuration else activeAttachment?.durationMs ?: 0L
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
