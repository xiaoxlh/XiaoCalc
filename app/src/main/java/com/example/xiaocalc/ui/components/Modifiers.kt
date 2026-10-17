package com.example.xiaocalc.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

/**
 * 表盘圆形裁剪。
 *
 * `WatchFrame` 把内容放进短边正方形的中央；圆形屏上这个正方形就是表盘外接正方形，
 * 四个角落落在屏幕之外。常规页面靠 `Disc.chordInBand` 主动避开角落，所以不需要裁剪；
 * 但图形画布是通铺的——网格、曲线、底部抽屉都必须**真的**被圆切掉，
 * 否则会画到表盘外面去。
 *
 * 方形屏上不做裁剪：那时角落是真实可见的屏幕区域。
 */
fun Modifier.discClip(round: Boolean): Modifier = if (round) clip(DiscShape) else this

/** 内接于内容区的圆。内容区恒为正方形（见 `WatchFrame`），因此短边即直径。 */
private val DiscShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val diameter = minOf(size.width, size.height)
        val radius = diameter / 2f
        return Outline.Generic(
            Path().apply {
                addOval(
                    Rect(
                        offset = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                        size = Size(diameter, diameter),
                    ),
                )
            },
        )
    }
}
