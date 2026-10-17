package com.xiaoxlh.xiaocalc

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xiaoxlh.xiaocalc.adaptive.WatchFrame
import com.xiaoxlh.xiaocalc.calc.CalculatorState
import com.xiaoxlh.xiaocalc.calc.CurrencyState
import com.xiaoxlh.xiaocalc.calc.EquationState
import com.xiaoxlh.xiaocalc.calc.GraphState
import com.xiaoxlh.xiaocalc.calc.SharedPreferencesStores
import com.xiaoxlh.xiaocalc.calc.Stores
import com.xiaoxlh.xiaocalc.calc.UnitConverterState
import com.xiaoxlh.xiaocalc.design.Motion
import com.xiaoxlh.xiaocalc.design.XiaoCalcTheme
import com.xiaoxlh.xiaocalc.ui.AppPage
import com.xiaoxlh.xiaocalc.ui.CalculatorScreen
import com.xiaoxlh.xiaocalc.data.FrankfurterRateSource
import com.xiaoxlh.xiaocalc.ui.CurrencyScreen
import com.xiaoxlh.xiaocalc.ui.DiagnosticsScreen
import com.xiaoxlh.xiaocalc.ui.EquationScreen
import com.xiaoxlh.xiaocalc.ui.GraphScreen
import com.xiaoxlh.xiaocalc.ui.HistoryScreen
import com.xiaoxlh.xiaocalc.ui.SettingsScreen
import com.xiaoxlh.xiaocalc.ui.UnitsScreen
import com.xiaoxlh.xiaocalc.ui.WelcomeScreen
import com.xiaoxlh.xiaocalc.ui.components.LocalRotaryHub
import com.xiaoxlh.xiaocalc.ui.components.RotaryHub
import com.xiaoxlh.xiaocalc.ui.components.RotaryProbe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val ROTARY_TAG = "XiaoCalcRotary"

class MainActivity : ComponentActivity() {
    private val rotaryHub = RotaryHub()

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isScrollSource()) {
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
                .takeIf { it != 0f }
                ?: ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (delta != 0f) {
                rotaryHub.dispatch(delta)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun MotionEvent.isScrollSource(): Boolean =
        isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) ||
            isFromSource(InputDevice.SOURCE_MOUSE) ||
            isFromSource(InputDevice.SOURCE_TRACKBALL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val stores = SharedPreferencesStores(this)
        if (BuildConfig.DEBUG) {
            RotaryProbe.init(this)
            RotaryProbe.reset()
        }
        setContent {
            CompositionLocalProvider(LocalRotaryHub provides rotaryHub) {
                XiaoCalcApp(
                    stores = stores,
                    welcomeCompleted = remember { stores.isWelcomeCompleted() },
                )
            }
        }
    }
}

@Composable
private fun XiaoCalcApp(stores: Stores, welcomeCompleted: Boolean) {
    val controller = remember(stores) { CalculatorState(stores, stores) }

    val converter = remember { UnitConverterState() }

    val equation = remember { EquationState() }

    val graph = remember { GraphState() }

    val rateSource = remember { FrankfurterRateSource() }
    val currency = remember(stores) { CurrencyState(stores) }

    var page by rememberSaveable {
        mutableStateOf(if (welcomeCompleted) AppPage.CALCULATOR else AppPage.WELCOME)
    }

    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf(clock.format(Date())) }
    LaunchedEffect(clock) {
        while (true) {
            val now = Date()
            time = clock.format(now)
            delay(60_000L - now.time % 60_000L)
        }
    }

    BackHandler(enabled = page != AppPage.CALCULATOR) {
        page = AppPage.CALCULATOR
    }

    val rotaryHub = LocalRotaryHub.current
    SideEffect { rotaryHub.currentPage = page }

    XiaoCalcTheme(themeColor = controller.settings.themeColor) {
        WatchFrame {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != AppPage.CALCULATOR
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(Motion.enter(Motion.DURATION_MEDIUM2)) {
                            it * direction
                        } +
                            fadeIn(tween(Motion.DURATION_SHORT4, easing = Motion.EmphasizedDecelerate)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = Motion.enter(Motion.DURATION_MEDIUM1),
                            )
                        ).togetherWith(
                        slideOutHorizontally(Motion.exit(Motion.DURATION_SHORT4)) {
                            -it * direction
                        } +
                            fadeOut(tween(Motion.DURATION_SHORT3, easing = Motion.EmphasizedAccelerate)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = Motion.exit(Motion.DURATION_SHORT4),
                            ),
                    )
                },
                modifier = Modifier.clipToBounds(),
                label = "page",
            ) { target ->
                when (target) {
                    AppPage.WELCOME -> WelcomeScreen(
                        time = time,
                        onComplete = {
                            stores.markWelcomeCompleted()
                            page = AppPage.CALCULATOR
                        },
                    )
                    AppPage.CALCULATOR -> CalculatorScreen(
                        controller = controller,
                        time = time,
                        onNavigate = { page = it },
                    )
                    AppPage.HISTORY -> HistoryScreen(
                        controller = controller,
                        time = time,
                        onNavigate = { page = it },
                    )
                    AppPage.SETTINGS -> SettingsScreen(
                        controller = controller,
                        time = time,
                        onNavigate = { page = it },
                    )
                    AppPage.DIAGNOSTICS -> DiagnosticsScreen(
                        time = time,
                        hapticsEnabled = controller.settings.haptics,
                        onNavigate = { page = it },
                    )
                    AppPage.UNITS -> UnitsScreen(
                        converter = converter,
                        settings = controller.settings,
                        time = time,
                        hapticsEnabled = controller.settings.haptics,
                        onNavigate = { page = it },
                    )
                    AppPage.EQUATION -> EquationScreen(
                        state = equation,
                        settings = controller.settings,
                        time = time,
                        hapticsEnabled = controller.settings.haptics,
                        onNavigate = { page = it },
                    )
                    AppPage.GRAPH -> GraphScreen(
                        state = graph,
                        settings = controller.settings,
                        time = time,
                        hapticsEnabled = controller.settings.haptics,
                        onNavigate = { page = it },
                    )
                    AppPage.CURRENCY -> CurrencyScreen(
                        state = currency,
                        source = rateSource,
                        settings = controller.settings,
                        time = time,
                        hapticsEnabled = controller.settings.haptics,
                        onNavigate = { page = it },
                        onAuthorize = {
                            controller.updateSettings { it.copy(allowNetwork = true) }
                        },
                    )
                }
            }
        }
    }
}
