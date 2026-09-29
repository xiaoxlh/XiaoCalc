package com.example.xiaocalc.ui.components

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

/**
 * 旋转表冠 / 表圈适配。
 *
 * ## 为什么不用 Compose 的 `onRotaryScrollEvent`
 *
 * 那个 API 要求节点**先获得焦点**，而触摸不会带来焦点。在本机（小米手表 OS，非 Wear OS）
 * 实测：`focusRequester + focusable + requestFocus` 这套写法**完全收不到事件**。
 *
 * 但同一台设备上 `Activity.dispatchGenericMotionEvent` 收到的原始事件是齐全的
 * （文件探针实测），所以改为**在 Activity 层接住、再转发进 Compose**——
 * 这条路不依赖 Compose 焦点系统，行为由实测确定而非文档推测。
 *
 * 实测原始事件形态：
 * ```
 * source=0x400000 (SOURCE_ROTARY_ENCODER)  action=8 (ACTION_SCROLL)  AXIS_SCROLL≈0.0131/档
 * ```
 *
 * ## 量纲换算
 *
 * `AXIS_SCROLL` 数值很小：内核 `REL_WHEEL` 的 ±1 档被 InputReader 换算成约 **0.0131**。
 * 直接当像素用（或只乘个位数系数）会让转一档只移动 0.0x 像素，表现就是"转了没反应"。
 *
 * ## 按页面注册（而不是抢单槽位）
 *
 * 早期版本用一个"当前拥有者"槽位，后注册者覆盖。但 `AnimatedContent` 在页面切换期间
 * 会**同时组合新旧两个页面**，谁最后注册取决于组合顺序——真机上表现为
 * "历史页能滚、设置页不能滚"。现在改为按页面键注册，只有 [RotaryHub.currentPage]
 * 对应的处理器会执行，与组合顺序无关。
 */

/** 实测：一档 ≈ 0.0131（AXIS_SCROLL 量纲） */
const val ROTARY_DETENT = 0.0131f

/**
 * 表冠旋转方向 → 滚动方向的映射。
 *
 * 真机实测正转（内核 `REL_WHEEL` 为正）需要**向上**滚动列表，
 * 即 `ScrollState` 的值减小，因此取 -1。
 * 单独抽成常量是因为"方向"和"速度"是两件事，混在一个数里会互相干扰。
 */
const val ROTARY_DIRECTION = -1f

/**
 * 一档滚动多少像素。
 *
 * 目标累加之后这个值**就等于实际位移**，可直接按手感校准。
 * 实测 20 偏快、等效 2.8 偏慢，取 10。
 */
const val PIXELS_PER_DETENT = 10f

/** 单次事件最多折算多少档。快速旋转时单次事件可达 20+ 档（实测峰值 27），须封顶 */
const val MAX_DETENTS_PER_EVENT = 6f

/**
 * 单次事件上限。
 *
 * 用"档数"而不是绝对像素来表达，是为了让它随 [PIXELS_PER_DETENT] 一起缩放——
 * 否则调完速度还得回头重算上限，两者会脱节。
 */
const val MAX_PIXELS_PER_EVENT = PIXELS_PER_DETENT * MAX_DETENTS_PER_EVENT

/**
 * 跟随的时间常数（毫秒）：显示值以指数方式逼近目标，越小越跟手。
 *
 * 不用逐事件 `animateTo` 的原因：那会在每个事件重启一次缓动曲线，
 * 速度不连续，转起来一顿一顿的。指数逼近没有"重启"这个概念，
 * 每个事件只是把目标推远一点，显示值始终沿同一条曲线追上去。
 */
const val ROTARY_TAU_MS = 80f

/** 纯函数：把原始 AXIS_SCROLL 换算成滚动像素。抽出来是为了可单测 */
fun rotaryPixels(delta: Float): Float =
    (ROTARY_DIRECTION * delta / ROTARY_DETENT * PIXELS_PER_DETENT)
        .coerceIn(-MAX_PIXELS_PER_EVENT, MAX_PIXELS_PER_EVENT)

/** 表冠在计算器页的去向 */
enum class RotaryTarget {
    /** 表达式行可横向滚动 */
    EXPRESSION,

    /** 结果行可横向滚动 */
    RESULT,

    /** 显示区没有可滚内容 → 切换键盘分页 */
    KEYPAD_PAGE,
}

/**
 * 纯函数：按当前"哪一行可滚动"决定表冠去向。
 *
 * 抽出来是为了可单测——"优先滚内容、没得滚才换页"这条规则不该埋在事件回调里。
 */
fun rotaryTarget(
    expressionScrollable: Boolean,
    resultScrollable: Boolean,
): RotaryTarget = when {
    expressionScrollable -> RotaryTarget.EXPRESSION
    resultScrollable -> RotaryTarget.RESULT
    else -> RotaryTarget.KEYPAD_PAGE
}

/**
 * 表冠事件中枢：按页面注册处理器，只有当前页面响应。
 */
class RotaryHub {

    private val handlers = mutableMapOf<Any, (Float) -> Unit>()

    /** 当前页面标识；由 App 层在每次成功组合后同步 */
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

/**
 * 由页面认领表冠事件。[page] 是该页面的标识（用 `AppPage`），
 * [handler] 收到的是**已换算成像素**的滚动量。
 */
@Composable
fun RotaryHandler(page: Any, handler: (Float) -> Unit) {
    val hub = LocalRotaryHub.current
    val current by rememberUpdatedState(handler)
    DisposableEffect(hub, page) {
        hub.register(page) { pixels -> current(pixels) }
        onDispose { hub.unregister(page) }
    }
}

/**
 * 让一个使用 [ScrollState] 的滚动容器响应表冠。
 *
 * ## 为什么不逐事件驱动滚动状态
 *
 * 早期版本每个事件直接 `scrollState.animateScrollBy(pixels)`。它有两个问题：
 *
 * 1. **位移会被丢弃**——动画未走完就来下一个事件时，Compose 取消旧动画并
 *    从当前位置重新设目标，剩下的位移就没了。实际速度只剩设定值的几分之一，
 *    而且随事件频率浮动，旋钮怎么调都不准。
 * 2. **不平滑**——每个事件重启一次缓动曲线，速度不连续，转起来一顿一顿。
 *
 * ## 现在的做法
 *
 * 表冠事件只推进 [target]（累加，不丢位移）；一个逐帧循环让显示值以
 * **指数方式**逼近目标。指数逼近没有"重启"概念，因此速度始终连续，
 * 且收敛速度与帧率无关。
 */
@Composable
fun RotaryScrollHandler(page: Any, scrollState: ScrollState) {
    var target by remember { mutableFloatStateOf(0f) }
    var display by remember { mutableFloatStateOf(0f) }

    RotaryHandler(page) { pixels ->
        val current = scrollState.value.toFloat()
        // display 始终跟随 scrollState，因此两者分开就说明手指滚动过，需要重新对齐；
        // 否则表冠会从陈旧的旧位置往回跳
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

/**
 * 表冠诊断探针。
 *
 * 不用 `Log.d`：这台设备是小米自家手表 OS，**应用日志会被系统静默过滤**
 * （系统里存在 `log.tag.*` 这类 MIUI 专有的按标签级别属性，实测 `Log.d` 完全不进 logcat）。
 * 因此写进应用私有目录，再用 `adb shell run-as` 读取。
 */
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
