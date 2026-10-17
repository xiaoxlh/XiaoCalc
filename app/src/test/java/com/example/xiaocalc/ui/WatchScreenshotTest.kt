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
import com.example.xiaocalc.calc.FunctionAnalysis
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

class WatchScreenshotTest {
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

    @Test
    fun welcome_intro() {
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
        val controller = state()
        controller.updateSettings { it.copy(keepRoot = true) }
        snapshot("settings") {
            SettingsScreen(controller = controller, time = "10:24", onNavigate = {})
        }
    }

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

    private fun equationState(text: String, mode: EquationMode = EquationMode.SINGLE): EquationState {
        val state = EquationState()
        state.selectMode(mode)
        if (mode == EquationMode.SYSTEM) {
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

    private fun graphState(vararg texts: String, angleUnit: AngleUnit = AngleUnit.DEG): GraphState {
        val state = GraphState()
        texts.forEachIndexed { index, text ->
            if (index > 0) state.addFunction()
            state.setExpression(text)
        }
        val samples = state.functions
            .filter { it.isDrawable }
            .flatMap { FunctionAnalysis.sample(it.expression, state.xMin, state.xMax, angleUnit) }
        val range = FunctionAnalysis.viewRange(samples)
        state.fitY(range.first, range.second)
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
    fun graph_editor() {
        graphSnapshot("graph_editor", graphState())
    }

    @Test
    fun graph_parabola() {
        graphSnapshot("graph_parabola", graphState("x^2-2"))
    }

    @Test
    fun graph_cubic() {
        graphSnapshot("graph_cubic", graphState("x^3-3x"))
    }

    @Test
    fun graph_multi() {
        graphSnapshot("graph_multi", graphState("x^2-2", "0.5x+1", "-x^2+4"))
    }

    @Test
    fun graph_sine() {
        paparazzi.unsafeUpdateConfig(deviceConfig = roundWatch)
        paparazzi.snapshot(name = "graph_sine") {
            XiaoCalcTheme {
                WatchFrame(modifier = Modifier.fillMaxSize()) {
                    GraphScreen(
                        state = graphState("sin(x)", angleUnit = AngleUnit.RAD),
                        settings = CalcSettings(angleUnit = AngleUnit.RAD),
                        time = "10:24",
                        hapticsEnabled = false,
                        onNavigate = {},
                    )
                }
            }
        }
    }

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

    @Test
    fun currency_consent() {
        snapshot("currency_consent") {
            ConsentPanel(onDecline = {}, onAccept = {})
        }
    }

    @Test
    fun animate_keypad_page_switch() {
        val controller = state()
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                XiaoCalcTheme {
                    WatchFrame(modifier = Modifier.fillMaxSize()) {
                        LaunchedEffect(Unit) { controller.onKey(CalcKey.PageFunctions) }
                        CalculatorScreen(controller = controller, time = "10:24", onNavigate = {})
                    }
                }
            }
        }
        paparazzi.gif(view, "keypad_page_switch", 0L, 420L, 20)
    }
}
