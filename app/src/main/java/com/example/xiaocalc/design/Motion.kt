package com.example.xiaocalc.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object Motion {
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    val StandardAccelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    const val DURATION_SHORT2 = 100
    const val DURATION_SHORT3 = 150
    const val DURATION_SHORT4 = 200
    const val DURATION_MEDIUM1 = 250
    const val DURATION_MEDIUM2 = 300
    const val DURATION_LONG2 = 500

    fun <T> enter(
        durationMillis: Int = DURATION_MEDIUM2,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, EmphasizedDecelerate)

    fun <T> exit(
        durationMillis: Int = DURATION_SHORT4,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, EmphasizedAccelerate)

    fun <T> standard(
        durationMillis: Int = DURATION_SHORT4,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, StandardDecelerate)

    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun offset(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
