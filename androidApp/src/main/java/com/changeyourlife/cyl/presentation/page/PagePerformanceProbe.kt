package com.changeyourlife.cyl.presentation.page

import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import com.changeyourlife.cyl.BuildConfig
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val PagePerformanceTag = "CYLPagePerf"
private const val FrameWindowNanos = 1_000_000_000L
private const val JankFrameMs = 20f
private const val SevereJankFrameMs = 34f

internal object PagePerformanceProbe {
    private val recompositions = ConcurrentHashMap<String, AtomicInteger>()

    // Opt in with: adb shell setprop log.tag.CYLPagePerf VERBOSE, then restart the app.
    // A continuously registered Choreographer callback must not run in normal debug builds.
    val isEnabled: Boolean = BuildConfig.DEBUG &&
        Log.isLoggable(PagePerformanceTag, Log.VERBOSE)

    fun recordRecomposition(label: String) {
        if (!isEnabled) return
        recompositions.getOrPut(label) { AtomicInteger() }.incrementAndGet()
    }

    fun drainRecompositions(): String {
        if (!isEnabled) return ""
        return recompositions.entries
            .mapNotNull { entry ->
                val count = entry.value.getAndSet(0)
                if (count > 0) "${entry.key}=$count" else null
            }
            .sorted()
            .joinToString(", ")
    }
}

@Composable
internal fun PageFramePerformanceProbe(
    label: String,
    detailProvider: () -> String,
) {
    if (!PagePerformanceProbe.isEnabled) return

    val latestDetailProvider = rememberUpdatedState(detailProvider)
    DisposableEffect(label) {
        val choreographer = Choreographer.getInstance()
        var lastFrameNanos = 0L
        var windowStartNanos = 0L
        var frameCount = 0
        var jankCount = 0
        var severeJankCount = 0
        var maxFrameMs = 0f

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (windowStartNanos == 0L) {
                    windowStartNanos = frameTimeNanos
                }
                if (lastFrameNanos != 0L) {
                    val frameMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                    frameCount += 1
                    if (frameMs > JankFrameMs) jankCount += 1
                    if (frameMs > SevereJankFrameMs) severeJankCount += 1
                    if (frameMs > maxFrameMs) maxFrameMs = frameMs
                }
                lastFrameNanos = frameTimeNanos

                if (frameTimeNanos - windowStartNanos >= FrameWindowNanos) {
                    val recompositionSummary = PagePerformanceProbe.drainRecompositions()
                    val detail = latestDetailProvider.value()
                    Log.d(
                        PagePerformanceTag,
                        buildString {
                            append(label)
                            append(" frames=")
                            append(frameCount)
                            append(" jank=")
                            append(jankCount)
                            append(" severe=")
                            append(severeJankCount)
                            append(" maxMs=")
                            append(String.format(Locale.US, "%.1f", maxFrameMs))
                            if (detail.isNotBlank()) {
                                append(" ")
                                append(detail)
                            }
                            if (recompositionSummary.isNotBlank()) {
                                append(" recompositions={")
                                append(recompositionSummary)
                                append("}")
                            }
                        },
                    )
                    windowStartNanos = frameTimeNanos
                    frameCount = 0
                    jankCount = 0
                    severeJankCount = 0
                    maxFrameMs = 0f
                }

                choreographer.postFrameCallback(this)
            }
        }

        choreographer.postFrameCallback(frameCallback)
        onDispose {
            choreographer.removeFrameCallback(frameCallback)
        }
    }
}

@Composable
internal fun RecompositionProbe(label: String) {
    if (!PagePerformanceProbe.isEnabled) return
    SideEffect {
        PagePerformanceProbe.recordRecomposition(label)
    }
}
