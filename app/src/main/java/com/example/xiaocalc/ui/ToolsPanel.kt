package com.example.xiaocalc.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.rememberKeyHaptics

@Composable
fun ToolsGrid(
    zone: Zone,
    onEquation: () -> Unit,
    onGraph: () -> Unit,
    onUnits: () -> Unit,
    onCurrency: () -> Unit,
    modifier: Modifier = Modifier,

    animated: Boolean = true,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val gap = WatchLayout.KEY_GAP
    val tileWidth = (zone.maxWidth - gap) / 2f
    val tileHeight = (zone.height - gap) / 2f

    Box(
        modifier = modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(scaled(gap))) {
            Row(horizontalArrangement = Arrangement.spacedBy(scaled(gap))) {
                ToolTile("解方程", "一元 · 二元", tileWidth, tileHeight, 0, shown, animated, onEquation)
                ToolTile("函数图像", "曲线与关键点", tileWidth, tileHeight, 1, shown, animated, onGraph)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(scaled(gap))) {
                ToolTile("单位换算", "长度 质量 温度", tileWidth, tileHeight, 2, shown, animated, onUnits)
                ToolTile("货币换算", "汇率 · 需授权", tileWidth, tileHeight, 3, shown, animated, onCurrency)
            }
        }
    }
}

@Composable
private fun ToolTile(
    title: String,
    subtitle: String,
    widthDu: Float,
    heightDu: Float,
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
            .width(scaled(widthDu))
            .height(scaled(heightDu))
            .graphicsLayer {
                alpha = progress
                val s = 0.88f + 0.12f * progress
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(scaled(22f)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = {
                    haptics(false)
                    onClick()
                },
            )
            .padding(horizontal = scaled(8f), vertical = scaled(6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(22f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(scaled(2f)))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(12f),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val STAGGER_MS = 45
