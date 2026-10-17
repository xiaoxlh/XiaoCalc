package com.xiaoxlh.xiaocalc.ui.components

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import java.io.File
import kotlin.math.abs
import kotlin.math.exp

// rad per detent reported by the wear rotary axis
const val ROTARY_DETENT = 0.0131f

const val ROTARY_DIRECTION = -1f

const val PIXELS_PER_DETENT = 10f

const val MAX_DETENTS_PER_EVENT = 4f

const val MAX_PIXELS_PER_EVENT = PIXELS_PER_DETENT * MAX_DETENTS_PER_EVENT

const val ROTARY_TAU_MS = 80f

fun rotaryPixels(delta: Float): Float =
    (ROTARY_DIRECTION * delta / ROTARY_DETENT * PIXELS_PER_DETENT)
        .coerceIn(-MAX_PIXELS_PER_EVENT, MAX_PIXELS_PER_EVENT)

enum class RotaryTarget {
    EXPRESSION,

    RESULT,

    KEYPAD_PAGE,
}

fun rotaryTarget(
    expressionScrollable: Boolean,
    resultScrollable: Boolean,
): RotaryTarget = when {
    expressionScrollable -> RotaryTarget.EXPRESSION
    resultScrollable -> RotaryTarget.RESULT
    else -> RotaryTarget.KEYPAD_PAGE
}

class RotaryHub {
    private val handlers = mutableMapOf<Any, (Float) -> Unit>()

    var currentPage: Any? = null

    fun register(page: Any, handler: (Float) -> Unit) {
        handlers[page] = handler
        RotaryProbe.log("register $page")
    }

    fun unregister(page: Any) {
        handlers.remove(page)
        RotaryProbe.log("unregister $page")
    }

    fun dispatch(rawDelta: Float) {
        val pixels = rotaryPixels(rawDelta)
        if (pixels == 0f) return
        val page = currentPage
        val handler = page?.let { handlers[it] }
        RotaryProbe.log("dispatch page=$page pixels=$pixels handled=${handler != null}")
        handler?.invoke(pixels)
    }
}

val LocalRotaryHub = staticCompositionLocalOf { RotaryHub() }

@Composable
fun RotaryHandler(page: Any, handler: (Float) -> Unit) {
    val hub = LocalRotaryHub.current
    val current by rememberUpdatedState(handler)
    DisposableEffect(hub, page) {
        hub.register(page) { pixels -> current(pixels) }
        onDispose { hub.unregister(page) }
    }
}

@Composable
fun RotaryScrollHandler(page: Any, scrollState: ScrollState) {
    var target by remember { mutableFloatStateOf(0f) }
    var display by remember { mutableFloatStateOf(0f) }

    RotaryHandler(page) { pixels ->
        val current = scrollState.value.toFloat()
        if (abs(current - display) > 2f) {
            target = current
            display = current
        }
        target = (target + pixels).coerceIn(0f, scrollState.maxValue.toFloat())
    }

    LaunchedEffect(scrollState) {
        var lastFrame = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dtMillis = if (lastFrame == 0L) {
                16f
            } else {
                ((now - lastFrame) / 1_000_000f).coerceIn(1f, 64f)
            }
            lastFrame = now
            val diff = target - display
            if (abs(diff) < 0.5f) {
                if (display != target) {
                    display = target
                    scrollState.scrollTo(display.toInt())
                }
            } else {
                display += diff * (1f - exp(-dtMillis / ROTARY_TAU_MS))
                scrollState.scrollTo(display.toInt())
            }
        }
    }
}

object RotaryProbe {
    @Volatile
    private var file: File? = null

    fun init(context: Context) {
        file = runCatching { File(context.filesDir, "rotary-probe.log") }.getOrNull()
    }

    fun reset() {
        runCatching { file?.writeText("=== probe active ===\n") }
    }

    fun log(line: String) {
        val target = file ?: return
        runCatching { target.appendText(line + "\n") }
    }
}
