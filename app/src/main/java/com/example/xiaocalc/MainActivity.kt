package com.example.xiaocalc

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
import com.example.xiaocalc.adaptive.WatchFrame
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.SharedPreferencesStores
import com.example.xiaocalc.calc.Stores
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.design.XiaoCalcTheme
import com.example.xiaocalc.ui.AppPage
import com.example.xiaocalc.ui.CalculatorScreen
import com.example.xiaocalc.ui.DiagnosticsScreen
import com.example.xiaocalc.ui.HistoryScreen
import com.example.xiaocalc.ui.SettingsScreen
import com.example.xiaocalc.ui.WelcomeScreen
import com.example.xiaocalc.ui.components.LocalRotaryHub
import com.example.xiaocalc.ui.components.RotaryHub
import com.example.xiaocalc.ui.components.RotaryProbe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** 表冠/旋转输入诊断日志的 tag */
private const val ROTARY_TAG = "XiaoCalcRotary"

class MainActivity : ComponentActivity() {

    /** 表冠事件中枢：Activity 层接住旋转事件后转发给当前页面 */
    private val rotaryHub = RotaryHub()

    /**
     * 表冠 / 旋转表圈。
     *
     * 在 **Activity 层**接住而不是用 Compose 的 `onRotaryScrollEvent`：
     * 后者要求节点先获得焦点，在本机（小米手表 OS）实测收不到任何事件；
     * 而 `dispatchGenericMotionEvent` 收到的原始事件是齐全的。
     *
     * 实测形态：`source=0x400000 (ROTARY_ENCODER) action=8 (SCROLL) AXIS_SCROLL≈0.0131/档`。
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isScrollSource()) {
            // AXIS_SCROLL 是旋转编码器/滚轮的规范轴；部分设备只给 AXIS_VSCROLL
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

    /** 旋转表冠、鼠标滚轮、轨迹球都当作"滚动输入" */
    private fun MotionEvent.isScrollSource(): Boolean =
        isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) ||
            isFromSource(InputDevice.SOURCE_MOUSE) ||
            isFromSource(InputDevice.SOURCE_TRACKBALL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全面屏沉浸：内容延伸到边缘，划出时才临时显示系统栏。
        // 这里**不**加 FLAG_KEEP_SCREEN_ON——手表上长亮会显著耗电并带来烧屏风险。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val stores = SharedPreferencesStores(this)

        // 表冠探针（DEBUG 才写文件）
        if (BuildConfig.DEBUG) {
            RotaryProbe.init(this)
            RotaryProbe.reset()
        }

        setContent {
            XiaoCalcTheme {
                CompositionLocalProvider(LocalRotaryHub provides rotaryHub) {
                    XiaoCalcApp(
                        stores = stores,
                        welcomeCompleted = remember { stores.isWelcomeCompleted() },
                    )
                }
            }
        }
    }
}

@Composable
private fun XiaoCalcApp(stores: Stores, welcomeCompleted: Boolean) {    // 计算状态 + 设置/历史仓库：跨重组保持，仓库负责落盘
    val controller = remember(stores) { CalculatorState(stores, stores) }

    var page by rememberSaveable {
        mutableStateOf(if (welcomeCompleted) AppPage.CALCULATOR else AppPage.WELCOME)
    }

    // 时钟：对齐到下一个整分再刷新，避免每秒重组整棵树
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf(clock.format(Date())) }
    LaunchedEffect(clock) {
        while (true) {
            val now = Date()
            time = clock.format(now)
            delay(60_000L - now.time % 60_000L)
        }
    }

    // 返回：子页面逐级退回计算器；计算器页内由页面自行处理（菜单优先关闭）
    BackHandler(enabled = page != AppPage.CALCULATOR) {
        page = AppPage.CALCULATOR
    }

    // 把"当前页面"同步给表冠中枢：只有当前页面注册的处理器会响应。
    // 用 SideEffect 而不是组合期直接写，是为了在每次成功组合后再同步，避免动画中途错位。
    val rotaryHub = LocalRotaryHub.current
    SideEffect { rotaryHub.currentPage = page }

    WatchFrame {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // 进入子页面＝前进（自右侧滑入）；回到计算器＝后退（自左侧滑入）。
                // 位移取**整屏宽度**：只用零头会让两张页面在过渡中途叠在一起、看起来像乱码。
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
            // 裁剪滑出的旧页面，避免它在内容盒之外留下残影
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
            }
        }
    }
}
