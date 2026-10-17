package com.example.xiaocalc.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * 面板弹出时把身后的页面模糊掉。
 *
 * 遮罩本身是半透明的，页面文字仍会透出来，在 480px 的表盘上很容易和面板内容混在一起。
 * 加一层模糊之后，前景与背景的层次立刻分开。
 *
 * **不支持就静默退化**：`Modifier.blur` 在 API < 31 上没有底层实现，会被忽略，
 * 于是老设备看到的就是原来那层遮罩——不会崩，也不会出现"半个模糊"的怪状态。
 * 用户也可以在设置里主动关掉。
 */
fun Modifier.overlayBlur(active: Boolean, enabled: Boolean): Modifier =
    if (active && enabled) this.blur(radius = 24.dp) else this
