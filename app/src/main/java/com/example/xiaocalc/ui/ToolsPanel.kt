package com.example.xiaocalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.rememberKeyHaptics

/**
 * 工具面板：一块方形的四格。
 *
 * ## 为什么不继续用右上角的菜单
 *
 * 菜单里塞到六项之后，每一项只剩一行小字，手指在 480px 的表盘上要点准很难，
 * 而且"哪个是刚加的、哪个最常用"完全看不出来。功能变多之后，
 * 菜单应当只剩真正低频的两项（历史、设置），常用工具换成**大图块**：
 * 目标大、字大、一眼扫完。
 *
 * ## 动效
 *
 * 四格依次错开 45ms 弹入，而不是整块面板一起淡入——
 * 错峰让视线有落点，也顺带说明"这是四个并列的入口"而不是一张图。
 * 时长走 [Motion.enter]，与全应用的其它进入动效同一套曲线。
 */
@Composable
fun ToolsPanel(
    onDismiss: () -> Unit,
    onEquation: () -> Unit,
    onGraph: () -> Unit,
    onUnits: () -> Unit,
    onCurrency: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 是否播放错峰入场。截图测试传 false：
     * [animateFloatAsState] 在首次合成时直接取目标值，因此关掉后图块立即可见，
     * 不会出现"渲染时 alpha 还是 0、整块面板是空的"这种假失败。
     */
    animated: Boolean = true,
) {
    // 入场动画只播一次：面板每次被重新合成时重播，所以用 remember 而非 rememberSaveable
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Surface(
            shape = RoundedCornerShape(scaled(34f)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = scaled(3f),
        ) {
            Column(
                modifier = Modifier.padding(scaled(14f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(scaled(TILE_GAP_DU))) {
                    ToolTile("解方程", "一元 · 二元", 0, shown, animated, onEquation)
                    ToolTile("函数图像", "曲线与关键点", 1, shown, animated, onGraph)
                }
                Spacer(Modifier.height(scaled(TILE_GAP_DU)))
                Row(horizontalArrangement = Arrangement.spacedBy(scaled(TILE_GAP_DU))) {
                    ToolTile("单位换算", "长度 质量 温度", 2, shown, animated, onUnits)
                    ToolTile("货币换算", "汇率 · 需授权", 3, shown, animated, onCurrency)
                }
            }
        }
    }
}

/**
 * 一个工具图块。
 *
 * [order] 决定它在错峰动画里的先后；[shown] 由面板统一切换，
 * 使四格的动画共享同一个起点、只在延迟上错开。
 */
@Composable
private fun ToolTile(
    title: String,
    subtitle: String,
    order: Int,
    shown: Boolean,
    animated: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberKeyHaptics(true)
    val progress by animateFloatAsState(
        targetValue = if (!animated || shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.DURATION_MEDIUM1,
            delayMillis = order * STAGGER_MS,
            easing = Motion.EmphasizedDecelerate,
        ),
        label = "toolTile$order",
    )
    Box(
        modifier = Modifier
            .width(scaled(TILE_WIDTH_DU))
            .height(scaled(TILE_HEIGHT_DU))
            .graphicsLayer {
                alpha = progress
                // 从 0.86 放大到 1：比纯淡入更有"落位"感，又不至于像弹窗
                val s = 0.86f + 0.14f * progress
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(scaled(26f)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = {
                    haptics(false)
                    onClick()
                },
            )
            .padding(horizontal = scaled(12f), vertical = scaled(10f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(scaled(3f)))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(13f),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 图块尺寸。四格 + 间距的总宽约 300du，仍在圆屏中段的弦长之内 */
private const val TILE_WIDTH_DU = 132f
private const val TILE_HEIGHT_DU = 88f
private const val TILE_GAP_DU = 10f

/** 错峰间隔：四格全部出现约需 135ms，比统一淡入只慢一点，但可读性更好 */
private const val STAGGER_MS = 45
