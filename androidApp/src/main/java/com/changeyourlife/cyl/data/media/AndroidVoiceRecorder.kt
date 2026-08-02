package com.changeyourlife.cyl.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.changeyourlife.cyl.domain.model.RecordedVoiceNote
import com.changeyourlife.cyl.domain.model.VoiceRecorderError
import com.changeyourlife.cyl.domain.model.VoiceRecorderResult
import com.changeyourlife.cyl.domain.model.VoiceNoteLimits
import com.changeyourlife.cyl.domain.repository.VoiceRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidVoiceRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VoiceRecorder {
    private val lock = Any()
    private val voiceDirectory: File by lazy {
        File(context.filesDir, VoiceDirectoryName).apply { mkdirs() }
    }

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingId: String? = null
    private var startedAtElapsedMs: Long = 0L

    override suspend fun start(): VoiceRecorderResult<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (recorder != null) {
                return@synchronized VoiceRecorderResult.Failure(VoiceRecorderError.AlreadyRecording)
            }

            val id = UUID.randomUUID().toString()
            val output = File(voiceDirectory, "$id.m4a")
            val nextRecorder = createRecorder()
            runCatching {
                nextRecorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioSamplingRate(AudioSampleRateHz)
                    setAudioEncodingBitRate(AudioBitRate)
                    setMaxDuration(VoiceNoteLimits.MaximumDurationMs.toInt())
                    setMaxFileSize(VoiceNoteLimits.MaximumFileBytes)
                    setOutputFile(output.absolutePath)
                    prepare()
                    start()
                }
            }.fold(
                onSuccess = {
                    recorder = nextRecorder
                    recordingFile = output
                    recordingId = id
                    startedAtElapsedMs = SystemClock.elapsedRealtime()
                    VoiceRecorderResult.Success(Unit)
                },
                onFailure = {
                    runCatching { nextRecorder.release() }
                    output.delete()
                    clearState()
                    VoiceRecorderResult.Failure(VoiceRecorderError.StartFailed)
                },
            )
        }
    }

    override suspend fun stop(): VoiceRecorderResult<RecordedVoiceNote> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val activeRecorder = recorder
                ?: return@synchronized VoiceRecorderResult.Failure(VoiceRecorderError.RecordingFailed)
            val output = recordingFile
                ?: return@synchronized failAndRelease(VoiceRecorderError.RecordingFailed)
            val id = recordingId
                ?: return@synchronized failAndRelease(VoiceRecorderError.RecordingFailed)
            val durationMs = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)

            val stopped = runCatching { activeRecorder.stop() }.isSuccess
            runCatching { activeRecorder.release() }
            clearState()

            when {
                !stopped || !output.isFile || output.length() <= 0L -> {
                    output.delete()
                    VoiceRecorderResult.Failure(VoiceRecorderError.RecordingFailed)
                }
                durationMs < VoiceNoteLimits.MinimumDurationMs -> {
                    output.delete()
                    VoiceRecorderResult.Failure(VoiceRecorderError.RecordingTooShort)
                }
                durationMs > VoiceNoteLimits.MaximumDurationMs ||
                    output.length() > VoiceNoteLimits.MaximumFileBytes -> {
                    output.delete()
                    VoiceRecorderResult.Failure(VoiceRecorderError.LimitExceeded)
                }
                else -> VoiceRecorderResult.Success(
                    RecordedVoiceNote(
                        id = id,
                        localPath = output.absolutePath,
                        name = "Voice note.m4a",
                        mimeType = VoiceNoteLimits.MimeType,
                        sizeBytes = output.length(),
                        durationMs = durationMs,
                        sha256 = output.sha256(),
                    ),
                )
            }
        }
    }

    override suspend fun cancel() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            releaseActiveRecorder(deleteFile = true)
        }
    }

    override suspend fun delete(localPath: String) = withContext(Dispatchers.IO) {
        val target = runCatching { File(localPath).canonicalFile }.getOrNull() ?: return@withContext
        val root = runCatching { voiceDirectory.canonicalFile }.getOrNull() ?: return@withContext
        if (target.path.startsWith(root.path + File.separator)) {
            target.delete()
        }
    }

    override fun currentAmplitude(): Int = synchronized(lock) {
        runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0).coerceAtLeast(0)
    }

    override fun release() {
        synchronized(lock) {
            releaseActiveRecorder(deleteFile = true)
        }
    }

    private fun failAndRelease(error: VoiceRecorderError): VoiceRecorderResult.Failure {
        releaseActiveRecorder(deleteFile = true)
        return VoiceRecorderResult.Failure(error)
    }

    private fun releaseActiveRecorder(deleteFile: Boolean) {
        recorder?.let { activeRecorder ->
            runCatching { activeRecorder.reset() }
            runCatching { activeRecorder.release() }
        }
        if (deleteFile) recordingFile?.delete()
        clearState()
    }

    private fun clearState() {
        recorder = null
        recordingFile = null
        recordingId = null
        startedAtElapsedMs = 0L
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }

    companion object {
        private const val VoiceDirectoryName = "voice_notes"
        private const val AudioSampleRateHz = 44_100
        private const val AudioBitRate = 96_000
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DefaultChecksumBufferBytes)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

private const val DefaultChecksumBufferBytes = 64 * 1024
