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

fun Modifier.overlayBlur(active: Boolean, enabled: Boolean): Modifier =
    if (active && enabled) this.blur(radius = 24.dp) else this

/** Clip content to the round screen (no-op on square displays). */
fun Modifier.discClip(round: Boolean): Modifier = if (round) clip(DiscShape) else this

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
