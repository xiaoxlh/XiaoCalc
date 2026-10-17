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
import androidx.compose.ui.graphics.Color
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
    "XiaoCalc 默认离线运行，不收集、不上传任何用户信息。\n\n" +
        "本应用仅有一项联网功能：货币汇率。该功能须经您在货币页明确授权后启用，" +
        "请求仅包含货币代码，可随时在设置中撤销。\n\n" +
        "授权后，当本地缓存并非当日汇率时，打开货币页会自动更新一次；同一日内不重复请求。\n\n" +
        "请遵守所在地法律法规，勿将本应用用于精密计算或其他要求高可靠性的场景；" +
        "由此产生的后果由使用者自行承担。\n\n" +
        "继续使用即表示您已阅读并同意上述条款。"

private const val STEP_COUNT = 2

val LocalEntranceAnimation = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun WelcomeScreen(
    time: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,

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

@Composable
private fun entrance(delayMillis: Int): Float {
    val animate = LocalEntranceAnimation.current

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
            hint = if (atBottom) null else "滑动至底部以继续",
            onClick = onNext,
        )
    }
}

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
