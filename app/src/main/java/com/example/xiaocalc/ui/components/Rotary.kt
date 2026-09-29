package com.example.xiaocalc.ui.components

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.xiaocalc.design.Motion
import java.io.File
import kotlinx.coroutines.launch

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
 * 一档滚动多少像素。
 *
 * 真机两轮校准：44 → 16 仍被反馈"太快"，降到 8。
 * 内核一次转动常产生多个事件，实际位移是这里的数倍。
 */
const val PIXELS_PER_DETENT = 8f

/** 单次事件上限：快速旋转时内核会批量累加，不设限会跳很远 */
const val MAX_PIXELS_PER_EVENT = 90f

/**
 * 表冠滚动的补间时长。
 *
 * 表冠事件是**离散**的（一档一个 `ACTION_SCROLL`），直接 `scrollBy` 会一格一格地跳。
 * 交给 `animateScrollBy` 之后，Compose 的 MutatorMutex 会自动取消上一条动画、
 * 从当前位置续接新目标，于是连续转动呈现为一条带减速的平滑跟随——即 M3 的手感。
 */
const val ROTARY_ANIM_MS = 220

/** 纯函数：把原始 AXIS_SCROLL 换算成滚动像素。抽出来是为了可单测 */
fun rotaryPixels(delta: Float): Float =
    (delta / ROTARY_DETENT * PIXELS_PER_DETENT)
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

/** 让一个使用 [ScrollState] 的滚动容器响应表冠（带 M3 减速补间） */
@Composable
fun RotaryScrollHandler(page: Any, scrollState: ScrollState) {
    val scope = rememberCoroutineScope()
    RotaryHandler(page) { pixels ->
        scope.launch {
            scrollState.animateScrollBy(
                value = pixels,
                animationSpec = tween(
                    durationMillis = ROTARY_ANIM_MS,
                    easing = Motion.EmphasizedDecelerate,
                ),
            )
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
