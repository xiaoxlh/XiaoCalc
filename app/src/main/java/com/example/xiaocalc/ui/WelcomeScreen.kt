package com.example.xiaocalc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.example.xiaocalc.R
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.PageTitle
import com.example.xiaocalc.ui.components.RotaryScrollHandler
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchScrollIndicator
import com.example.xiaocalc.ui.components.rememberKeyHaptics

private const val POLICY =
    "XiaoCalc 为离线应用，不联网、不收集您的任何信息。\n\n" +
        "使用时请遵守所在地法律法规，勿将本应用用于精密计算场景，\n" +
        "勿在禁止使用计算器的场合使用；由此造成的后果由您自行承担。\n\n" +
        "继续使用即表示您已阅读并同意以上内容。"

private const val STEP_COUNT = 2

/**
 * 入场动效开关。
 *
 * 为什么要这个：入场动效的初始 `alpha` 是 0，而截图测试只渲染一帧、动画尚未推进，
 * 拍出来就是**一片空白**——内容被藏在动画后面就无法验证。
 * 关掉后 [entrance] 直接返回终态，快照能拍到真实版面。
 *
 * 顺带它也是将来"关闭动画"无障碍设置的接入点（系统 animator scale 为 0 时应走同一分支）。
 */
val LocalEntranceAnimation = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * 首次运行引导（两步）。Material 3 重构要点：
 *
 * - **错落入场**：logo / 标题 / 副标题各自带延迟地淡入并轻微上浮，
 *   而不是整块同时出现（[entrance] 统一驱动，位移与透明度同源，为 GPU 友好）。
 * - **页点指示**：选中态由短横拉长，是 M3 对"第几页"的惯用表达。
 * - **政策阅读区边缘渐隐**：用 `BlendMode.DstIn` 做上下淡出，
 *   暗示"下面还有内容"，比硬切一条边更友好。
 * - **CTA 状态明确**：未读完时按钮为中性容器色并就地提示（不用 Toast——手表上会遮挡且一闪而过）。
 */
@Composable
fun WelcomeScreen(
    time: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    /** 起始步骤；供截图测试直接渲染政策页（Paparazzi 无法交互点按） */
    startStep: Int = 0,
) {
    val layout = LocalWatchMetrics.current.layout
    val haptics = rememberKeyHaptics(true)
    var step by remember { mutableIntStateOf(startStep) }
    var hintVisible by remember { mutableStateOf(false) }
    val policyScroll = rememberScrollState()
    var policyViewport by remember { mutableStateOf(IntSize.Zero) }
    val atBottom = policyScroll.maxValue == 0 || policyScroll.value >= policyScroll.maxValue

    Box(modifier.fillMaxSize()) {
        RotaryScrollHandler(AppPage.WELCOME, policyScroll)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(scaled(layout.header.top)))
            WatchHeader(zone = layout.header, center = { ClockText(time) })

            Spacer(Modifier.height(scaled(layout.content.top - layout.header.bottom)))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    // 整宽位移：只用零头会让两步内容在过渡中途叠在一起
                    (
                        slideInHorizontally(Motion.enter(Motion.DURATION_MEDIUM2)) {
                            it * direction
                        } + fadeIn(Motion.enter(Motion.DURATION_SHORT4))
                        ).togetherWith(
                        slideOutHorizontally(Motion.exit(Motion.DURATION_SHORT4)) {
                            -it * direction
                        } + fadeOut(Motion.exit(Motion.DURATION_SHORT3)),
                    )
                },
                label = "welcomeStep",
                modifier = Modifier
                    .width(scaled(layout.content.maxWidth))
                    .height(scaled(layout.content.height)),
            ) { current ->
                if (current == 0) {
                    IntroStep(
                        onNext = {
                            haptics(false)
                            step = 1
                        },
                        pageLabel = "1",
                    )
                } else {
                    PolicyStep(
                        atBottom = atBottom,
                        scrollState = policyScroll,
                        onScrolled = { policyViewport = it },
                        onNext = {
                            if (atBottom) {
                                haptics(true)
                                onComplete()
                            } else {
                                haptics(false)
                                hintVisible = true
                            }
                        },
                        pageLabel = "2",
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }

        if (step == 1) {
            WatchScrollIndicator(scroll = policyScroll, viewportPx = policyViewport.height.toFloat())
        }
    }
}

/**
 * 错落入场的驱动值：0 → 1。
 * 位移与透明度都从它派生，避免多个动画各跑各的。
 */
@Composable
private fun entrance(delayMillis: Int): Float {
    val animate = LocalEntranceAnimation.current
    // 关闭动效时直接以终态起步，否则首帧 alpha=0 会拍到空白
    var entered by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) { if (animate) entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.DURATION_LONG2,
            delayMillis = delayMillis,
            easing = Motion.EmphasizedDecelerate,
        ),
        label = "entrance",
    )
    return progress
}

/** 把入场进度作用到 `alpha` + 轻微上浮 + 缩放 */
private fun Modifier.entranceEffect(progress: Float, riseDu: Float = 18f, scaleFrom: Float = 1f): Modifier =
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * riseDu
        val s = scaleFrom + (1f - scaleFrom) * progress
        scaleX = s
        scaleY = s
    }

@Composable
private fun IntroStep(onNext: () -> Unit, pageLabel: String) {
    val logoProgress = entrance(delayMillis = 0)
    val titleProgress = entrance(delayMillis = 90)
    val taglineProgress = entrance(delayMillis = 170)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.xiaocalc_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(scaled(84f))
                    .entranceEffect(logoProgress, riseDu = 10f, scaleFrom = 0.86f),
            )
            Spacer(Modifier.height(scaled(10f)))
            Text(
                text = "XiaoCalc 小计算",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = scaledSp(30f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.entranceEffect(titleProgress),
            )
            Spacer(Modifier.height(scaled(4f)))
            Text(
                // 显式断行：内容区宽度受内接正方形约束，交给自动换行会把 "Wrist." 单独甩到一行
                text = "Start Calculating\non Your Wrist.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(17f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.entranceEffect(taglineProgress),
            )
        }
        StepFooter(
            pageLabel = pageLabel,
            enabled = true,
            hint = null,
            onClick = onNext,
        )
        Spacer(Modifier.height(scaled(6f)))
    }
}

@Composable
private fun PolicyStep(
    atBottom: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onScrolled: (IntSize) -> Unit,
    onNext: () -> Unit,
    pageLabel: String,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("用户政策")
        Spacer(Modifier.height(scaled(8f)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fadeEdges()
                // onSizeChanged 在 verticalScroll 外侧才量到"可视高"
                .onSizeChanged(onScrolled)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = POLICY,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(17f),
                lineHeight = scaledSp(26f),
                fontWeight = FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(scaled(8f)))
        StepFooter(
            pageLabel = pageLabel,
            enabled = atBottom,
            hint = if (atBottom) null else "滑动阅读到底部以继续",
            onClick = onNext,
        )
    }
}

/** 底部：页点 + 下一步按钮（横向排布以省纵向空间，政策区能多显示一行） */
@Composable
private fun StepFooter(
    pageLabel: String,
    enabled: Boolean,
    hint: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageDots(current = pageLabel.toIntOrNull()?.minus(1) ?: 0)
            Spacer(Modifier.width(scaled(14f)))
            WatchIconButton(
                onClick = onClick,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (enabled) "下一步" else "请先读完政策",
                touchSize = scaled(56f),
                glyphSize = scaled(26f),
                container = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT4)) +
                scaleIn(initialScale = 0.92f, animationSpec = Motion.enter(Motion.DURATION_SHORT3)),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT2)),
        ) {
            Text(
                text = hint.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = scaled(6f)),
            )
        }
    }
}

/** M3 页点：选中态拉长为短横，未选中为小圆点 */
@Composable
private fun PageDots(current: Int) {
    val density = LocalDensity.current
    val longWidth = scaled(18f)
    val dotWidth = scaled(7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scaled(6f)),
    ) {
        repeat(STEP_COUNT) { index ->
            val selected = index == current
            val width by animateFloatAsState(
                targetValue = with(density) { (if (selected) longWidth else dotWidth).toPx() },
                animationSpec = Motion.enter(Motion.DURATION_MEDIUM2),
                label = "dotWidth",
            )
            val color by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = Motion.standard(),
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(width = with(density) { width.toDp() }, height = dotWidth)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * 上下边缘渐隐，暗示滚动区还有内容。
 *
 * `CompositingStrategy.Offscreen` 不是可选项：`BlendMode.DstIn` 需要把内容先合成到
 * **独立图层**再按 alpha 相乘；直接在同一画布上做，结果是未定义的
 * （实测会在文字中间render出横向亮带）。
 */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent
        // 渐隐区取可视高的 14%——太短会看成一条硬边
        val fade = size.height * 0.14f
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fade / size.height to Color.Black,
                (size.height - fade) / size.height to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
