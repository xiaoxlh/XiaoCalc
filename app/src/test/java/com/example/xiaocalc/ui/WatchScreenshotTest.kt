package com.example.xiaocalc.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenRound
import com.example.xiaocalc.adaptive.WatchFrame
import com.example.xiaocalc.calc.AngleUnit
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.HistoryEntry
import com.example.xiaocalc.calc.InMemoryHistoryStore
import com.example.xiaocalc.calc.InMemorySettingsStore
import com.example.xiaocalc.design.XiaoCalcTheme
import org.junit.Rule
import org.junit.Test

/**
 * 无设备 UI 复核：在 JVM 上用 layoutlib 真实渲染 Compose，产出 PNG。
 *
 * 存在的意义：本项目的核心难点是"内容必须落进圆形表盘"，
 * 而这件事**只能靠看**最终确认——单元测试能证明几何不等式成立，
 * 但证明不了 Compose 实际测量出来的文本宽度、以及 `isScreenRound` 是否被正确识别。
 *
 * 运行：`./gradlew :app:recordPaparazziDebug`
 * 产物：`app/src/test/snapshots/`
 */
class WatchScreenshotTest {

    /**
     * 圆形手表：与用户实机一致（480×480 @ 320dpi → 240dp 圆屏），
     * 以 Paparazzi 自带的手表配置为基准。
     */
    private val roundWatch = DeviceConfig.GALAXY_WATCH4_CLASSIC_LARGE.copy(
        screenWidth = 480,
        screenHeight = 480,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        screenRound = ScreenRound.ROUND,
        softButtons = false,
    )

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = roundWatch,
        theme = "android:style/Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private fun state(
        result: String? = null,
        history: List<HistoryEntry> = emptyList(),
        angleUnit: AngleUnit = AngleUnit.DEG,
    ): CalculatorState {
        val store = InMemorySettingsStore()
        val state = CalculatorState(store, InMemoryHistoryStore(history)) { 1_700_000_000_000L }
        state.updateSettings { it.copy(angleUnit = angleUnit) }
        if (result != null) {
            // 用真实按键序列产生结果，避免"测试里造假数据、真机上算不出来"
            for (c in result) {
                when (c) {
                    in '0'..'9' -> state.onKey(CalcKey.Digit(c))
                    '+' -> state.onKey(CalcKey.Plus)
                    '×' -> state.onKey(CalcKey.Times)
                    '÷' -> state.onKey(CalcKey.Divide)
                    '^' -> state.onKey(CalcKey.Power)
                    'π' -> state.onKey(CalcKey.Pi)
                    else -> Unit
                }
            }
            state.onKey(CalcKey.Equals)
        }
        return state
    }

    private fun snapshot(name: String, device: DeviceConfig = roundWatch, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = device)
        paparazzi.snapshot(name = name) {
            XiaoCalcTheme {
                WatchFrame(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }

    // ------------------------------------------------------------------ 计算器

    @Test
    fun calculator_idle() {
        snapshot("calculator_idle") {
            CalculatorScreen(controller = state(), time = "10:24", onNavigate = {})
        }
    }

    @Test
    fun calculator_result() {
        snapshot("calculator_result") {
            CalculatorScreen(
                controller = state(result = "12345×678"),
                time = "10:24",
                onNavigate = {},
            )
        }
    }

    @Test
    fun calculator_long_result() {
        snapshot("calculator_long_result") {
            CalculatorScreen(
                controller = state(result = "99999999×99999999"),
                time = "10:24",
                onNavigate = {},
            )
        }
    }

    @Test
    fun calculator_function_page() {
        val controller = state()
        controller.onKey(CalcKey.PageFunctions)
        snapshot("calculator_function_page") {
            CalculatorScreen(controller = controller, time = "10:24", onNavigate = {})
        }
    }

    @Test
    fun calculator_error_state() {
        val controller = state()
        for (c in "50÷0") {
            when (c) {
                in '0'..'9' -> controller.onKey(CalcKey.Digit(c))
                '÷' -> controller.onKey(CalcKey.Divide)
            }
        }
        controller.onKey(CalcKey.Equals)
        snapshot("calculator_error") {
            CalculatorScreen(controller = controller, time = "10:24", onNavigate = {})
        }
    }

    // ------------------------------------------------------------------ 子页面

    @Test
    fun welcome_intro() {
        // 关掉入场动效：否则首帧 alpha=0，快照只能拍到空白
        snapshot("welcome_intro") {
            CompositionLocalProvider(LocalEntranceAnimation provides false) {
                WelcomeScreen(time = "10:24", onComplete = {})
            }
        }
    }

    @Test
    fun welcome_policy() {
        snapshot("welcome_policy") {
            CompositionLocalProvider(LocalEntranceAnimation provides false) {
                WelcomeScreen(time = "10:24", onComplete = {}, startStep = 1)
            }
        }
    }

    @Test
    fun diagnostics() {
        snapshot("diagnostics") {
            DiagnosticsScreen(time = "10:24", hapticsEnabled = false, onNavigate = {})
        }
    }

    @Test
    fun history_list() {
        snapshot("history") {
            HistoryScreen(
                controller = state(
                    history = listOf(
                        HistoryEntry("12345×678", "8,369,910", 1L),
                        HistoryEntry("√8", "2√2", 2L),
                        HistoryEntry("sin(30)+2^10", "1,024.5", 3L),
                    ),
                ),
                time = "10:24",
                onNavigate = {},
            )
        }
    }

    @Test
    fun history_empty() {
        snapshot("history_empty") {
            HistoryScreen(controller = state(), time = "10:24", onNavigate = {})
        }
    }

    @Test
    fun settings() {
        snapshot("settings") {
            SettingsScreen(controller = state(), time = "10:24", onNavigate = {})
        }
    }

    // ------------------------------------------------------------------ 动效

    /**
     * 录制键盘翻页动画（AnimatedContent：位移 + 淡入淡出 + 缩放）。
     *
     * 静帧截图只能证明"布局对"，证明不了"动效真的在跑"——所以这里用 Paparazzi 的
     * gif 模式把 0–420ms 逐帧录下来。产物是 APNG；由 `tools/extract_animation_frames.py`
     * 抽帧拼成一张胶片图，便于直接肉眼核对中间帧。
     */
    @Test
    fun animate_keypad_page_switch() {
        val controller = state()
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                XiaoCalcTheme {
                    WatchFrame(modifier = Modifier.fillMaxSize()) {
                        // 首帧之后再切页，才能让 AnimatedContent 真正从主键盘过渡到函数页
                        LaunchedEffect(Unit) { controller.onKey(CalcKey.PageFunctions) }
                        CalculatorScreen(controller = controller, time = "10:24", onNavigate = {})
                    }
                }
            }
        }
        paparazzi.gif(view, "keypad_page_switch", 0L, 420L, 20)
    }
}
