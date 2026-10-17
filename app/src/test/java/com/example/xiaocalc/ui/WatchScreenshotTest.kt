package com.example.xiaocalc.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.ui.components.SliderRow
import androidx.compose.foundation.layout.Box
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
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.CurrencyState
import com.example.xiaocalc.calc.EquationMode
import com.example.xiaocalc.calc.EquationState
import com.example.xiaocalc.calc.GraphState
import com.example.xiaocalc.calc.HistoryEntry
import com.example.xiaocalc.calc.InMemoryHistoryStore
import com.example.xiaocalc.calc.InMemoryRateStore
import com.example.xiaocalc.calc.InMemorySettingsStore
import com.example.xiaocalc.calc.RateCodec
import com.example.xiaocalc.calc.RateTable
import com.example.xiaocalc.calc.ThemeColor
import com.example.xiaocalc.calc.UnitCategory
import com.example.xiaocalc.calc.Units
import com.example.xiaocalc.calc.UnitConverterState
import com.example.xiaocalc.data.RateSource
import com.example.xiaocalc.design.XiaoCalcTheme
import com.example.xiaocalc.ui.components.PickerOverlay
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

    private fun snapshot(
        name: String,
        device: DeviceConfig = roundWatch,
        theme: ThemeColor = ThemeColor.RED,
        content: @Composable () -> Unit,
    ) {
        paparazzi.unsafeUpdateConfig(deviceConfig = device)
        paparazzi.snapshot(name = name) {
            XiaoCalcTheme(themeColor = theme) {
                WatchFrame(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }

    // ------------------------------------------------------------------ 主题

    /**
     * 非默认主题：确认强调色真的贯穿到了 `=` 键与运算符键，
     * 而结构色（页面胶囊的蓝）**保持不变**——两者分工不该被一起换掉。
     */
    @Test
    fun calculator_theme_green() {
        snapshot("calculator_theme_green", theme = ThemeColor.GREEN) {
            CalculatorScreen(
                controller = state(result = "12345×678"),
                time = "10:24",
                onNavigate = {},
            )
        }
    }

    @Test
    fun settings_theme_purple() {
        snapshot("settings_theme_purple", theme = ThemeColor.PURPLE) {
            SettingsScreen(controller = state(), time = "10:24", onNavigate = {})
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
        // 刻意让一个开关处于选中态：只拍"全关"的话，选中样式（颜色、滑块位置）
        // 就没被任何快照覆盖，改坏了也看不出来
        val controller = state()
        controller.updateSettings { it.copy(keepRoot = true) }
        snapshot("settings") {
            SettingsScreen(controller = controller, time = "10:24", onNavigate = {})
        }
    }

    // ------------------------------------------------------------------ 单位换算

    /** 换算页是纵向分段最多的页面（顶栏/显示区两行/键盘/操作行），最需要肉眼复核 */
    @Test
    fun units_length() {
        val converter = UnitConverterState()
        converter.typeDigits("12.5")
        snapshot("units_length") {
            UnitsScreen(
                converter = converter,
                settings = CalcSettings(),
                time = "10:24",
                hapticsEnabled = false,
                onNavigate = {},
            )
        }
    }

    /** 温度是唯一的仿射换算（带偏移），单独拍一张 */
    @Test
    fun units_temperature() {
        val converter = UnitConverterState()
        converter.selectCategory(UnitCategory.TEMPERATURE)
        converter.typeDigits("-40")
        snapshot("units_temperature") {
            UnitsScreen(
                converter = converter,
                settings = CalcSettings(),
                time = "10:24",
                hapticsEnabled = false,
                onNavigate = {},
            )
        }
    }

    /** 选择器面板：验证它完整落在圆内、四角不被裁掉 */
    @Test
    fun units_picker() {
        snapshot("units_picker") {
            PickerOverlay(
                title = "单位类别",
                options = UnitCategory.entries,
                selected = UnitCategory.LENGTH,
                label = { it.label },
                onSelect = {},
                onDismiss = {},
                scrollState = rememberScrollState(),
            )
        }
    }

    /**
     * 单位列表：标题右侧那颗「长度 ⌄」是钻进类别列表的入口。
     *
     * 标题行从单个 Text 改成了 Row，标题一长就会把胶囊挤出面板；
     * 这张图守着那个改动的结果。
     */
    @Test
    fun units_picker_units() {
        snapshot("units_picker_units") {
            UnitPicker(
                title = "起算单位",
                options = Units.catalog.getValue(UnitCategory.LENGTH),
                selected = Units.catalog.getValue(UnitCategory.LENGTH).first(),
                onSelect = {},
                onDismiss = {},
                scrollState = rememberScrollState(),
                categoryAction = { CategoryDrilldownChip(label = "长度", onClick = {}) },
            )
        }
    }

    private fun UnitConverterState.typeDigits(text: String) {
        for (c in text) {
            when {
                c in '0'..'9' -> onKey(CalcKey.Digit(c))
                c == '.' -> onKey(CalcKey.Dot)
                c == '-' -> onKey(CalcKey.Minus)
            }
        }
    }

    // ------------------------------------------------------------------ 方程

    private fun equationState(text: String, mode: EquationMode = EquationMode.SINGLE): EquationState {
        val state = EquationState()
        state.selectMode(mode)
        if (mode == EquationMode.SYSTEM) {
            // 二元模式是两个独立输入框，测试里按分号拆开填
            text.split(';').forEachIndexed { index, part -> state.setText(index, part) }
        } else {
            state.setText(0, text)
        }
        return state
    }

    private fun equationSnapshot(name: String, state: EquationState) = snapshot(name) {
        EquationScreen(
            state = state,
            settings = CalcSettings(),
            time = "10:24",
            hapticsEnabled = false,
            onNavigate = {},
        )
    }

    @Test
    fun equation_quadratic() {
        equationSnapshot("equation_quadratic", equationState("x^2-3x+2=0"))
    }

    @Test
    fun equation_radical() {
        // 精确根式是这一页的核心价值，必须单独拍一张确认它真的显示成 √2
        equationSnapshot("equation_radical", equationState("x^2-2x-1=0"))
    }

    @Test
    fun equation_complex() {
        equationSnapshot("equation_complex", equationState("x^2+2x+5=0"))
    }

    @Test
    fun equation_system() {
        equationSnapshot("equation_system", equationState("x+y=3;2x-y=0", EquationMode.SYSTEM))
    }

    // ------------------------------------------------------------------ 图像

    private fun graphState(text: String, graphing: Boolean = false): GraphState {
        val state = GraphState()
        state.insert(text)
        if (graphing) state.enterGraph()
        return state
    }

    private fun graphSnapshot(name: String, state: GraphState) = snapshot(name) {
        GraphScreen(
            state = state,
            settings = CalcSettings(),
            time = "10:24",
            hapticsEnabled = false,
            onNavigate = {},
        )
    }

    @Test
    fun graph_input() {
        graphSnapshot("graph_input", graphState("x^2-2"))
    }

    @Test
    fun graph_parabola() {
        graphSnapshot("graph_parabola", graphState("x^2-2", graphing = true))
    }

    @Test
    fun graph_cubic() {
        graphSnapshot("graph_cubic", graphState("x^3-3x", graphing = true))
    }

    /**
     * 非多项式：验证数值路线画出来的曲线（零点与极值都靠二分求得）。
     *
     * 用弧度制：角度制下 `[-10,10]` 只有 10°，`sin(x°) ≈ x·π/180` 本就近似线性，
     * 画出来接近一条直线——那是**正确**的（真实图形计算器同样如此），
     * 只是不适合用来验证波形绘制。角度制的情形由范围行的 `deg` 标记提示。
     */
    @Test
    fun graph_sine() {
        paparazzi.unsafeUpdateConfig(deviceConfig = roundWatch)
        paparazzi.snapshot(name = "graph_sine") {
            XiaoCalcTheme {
                WatchFrame(modifier = Modifier.fillMaxSize()) {
                    GraphScreen(
                        state = graphState("sin(x)", graphing = true),
                        settings = CalcSettings(angleUnit = AngleUnit.RAD),
                        time = "10:24",
                        hapticsEnabled = false,
                        onNavigate = {},
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ 货币

    /** 快照不联网：数据源固定返回 null，页面只会用到注入的缓存汇率 */
    private class OfflineRateSource : RateSource {
        override fun fetch(base: String, quotes: List<String>): RateTable? = null
    }

    private fun currencyState(withCache: Boolean = true): CurrencyState {
        val cached = if (withCache) {
            RateCodec.parse(
                """[{"date":"2026-10-16","base":"CNY","quote":"USD","rate":0.14961},
                    {"date":"2026-10-16","base":"CNY","quote":"JPY","rate":23.655}]""",
            )
        } else {
            null
        }
        val state = CurrencyState(InMemoryRateStore(cached))
        for (c in "100") state.onKey(CalcKey.Digit(c))
        return state
    }

        /**
     * 小数位数滑杆。它挂在设置页较靠下的位置，整页快照拍不到，
     * 因此单独拍一张——这是全应用唯一的连续输入控件，值得有自己的金标。
     */
    @Test
    fun settings_decimals_slider() {
        snapshot("settings_decimals_slider") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = scaled(24f)),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                SliderRow(
                    title = "小数位数",
                    valueLabel = "16",
                    value = CalcSettings.FULL.toFloat(),
                    steps = CalcSettings.FULL - 1,
                    valueRange = 0f..CalcSettings.FULL.toFloat(),
                    onValueChange = {},
                    description = "16 位有效数字",
                )
              }
            }
        }
    }
    // ------------------------------------------------------------------ 工具面板

    /**
     * 工具面板：四个大图块替代原先六项的小字菜单。
     *
     * 关掉错峰入场再拍：动画的 alpha 在渲染时刻还是 0，开着会拍到一块空面板。
     * 这里要守住的是**排版**——四格是否落在圆内、文字是否被截断。
     */
    @Test
    fun tools_panel() {
        snapshot("tools_panel") {
            CalculatorScreen(
                controller = state(),
                time = "10:24",
                onNavigate = {},
                initialToolsOpen = true,
            )
        }
    }
private fun currencySnapshot(name: String, state: CurrencyState) = snapshot(name) {
        CurrencyScreen(
            state = state,
            source = OfflineRateSource(),
            settings = CalcSettings(),
            time = "10:24",
            hapticsEnabled = false,
            onNavigate = {},
            onAuthorize = {},
        )
    }

    @Test
    fun currency_with_cache() {
        currencySnapshot("currency_cached", currencyState())
    }

    @Test
    fun currency_no_cache() {
        currencySnapshot("currency_empty", currencyState(withCache = false))
    }

    /** 授权面板：文案必须完整可读，不能被圆形屏幕裁掉 */
    @Test
    fun currency_consent() {
        snapshot("currency_consent") {
            ConsentPanel(onDecline = {}, onAccept = {})
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
