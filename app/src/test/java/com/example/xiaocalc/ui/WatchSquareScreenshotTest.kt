package com.example.xiaocalc.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenRound
import com.example.xiaocalc.adaptive.WatchFrame
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.InMemoryHistoryStore
import com.example.xiaocalc.calc.InMemorySettingsStore
import com.example.xiaocalc.design.XiaoCalcTheme
import org.junit.Rule
import org.junit.Test

/**
 * 方形 / 非圆屏分支的截图复核。
 *
 * 必须单独成一个类：Paparazzi 的屏幕外形在 `@get:Rule` 创建时就固定了，
 * 对圆形规则调用 `unsafeUpdateConfig(screenRound = NOTROUND)` 只会改变
 * `Configuration.isScreenRound`（即走方形布局），**渲染出来的遮罩仍是圆的**，
 * 结果就是"方形布局被圆形遮罩裁掉"的假象。
 */
class WatchSquareScreenshotTest {

    private val squareWatch = DeviceConfig.WEAR_OS_SQUARE.copy(
        screenWidth = 480,
        screenHeight = 480,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        screenRound = ScreenRound.NOTROUND,
        softButtons = false,
    )

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = squareWatch,
        theme = "android:style/Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private fun state(result: String? = null): CalculatorState {
        val state = CalculatorState(InMemorySettingsStore(), InMemoryHistoryStore()) { 1L }
        if (result != null) {
            for (c in result) {
                when (c) {
                    in '0'..'9' -> state.onKey(CalcKey.Digit(c))
                    '×' -> state.onKey(CalcKey.Times)
                    else -> Unit
                }
            }
            state.onKey(CalcKey.Equals)
        }
        return state
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            XiaoCalcTheme {
                WatchFrame(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun calculator_square() {
        snapshot("calculator_square") {
            CalculatorScreen(controller = state("12345×678"), time = "10:24", onNavigate = {})
        }
    }

    @Test
    fun settings_square() {
        snapshot("settings_square") {
            SettingsScreen(controller = state(), time = "10:24", onNavigate = {})
        }
    }

    @Test
    fun diagnostics_square() {
        snapshot("diagnostics_square") {
            DiagnosticsScreen(time = "10:24", hapticsEnabled = false, onNavigate = {})
        }
    }
}
