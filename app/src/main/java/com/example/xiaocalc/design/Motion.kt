package com.example.xiaocalc.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Material 3 动效令牌（motion tokens）。
 *
 * 曲线取自 M3 官方规格：
 *  - Emphasized      —— 大范围、进入式的位移（页面切换、键盘翻页）
 *  - Standard        —— 常规属性变化（颜色、透明度）
 *  - Spring          —— 可交互物件的按压回弹
 *
 * 时长按 M3 的 short/medium/long 档位取值；手表屏幕小、视线停留时间短，
 * 因此整体取值偏短，保证"按下即有反馈"。
 */
object Motion {
    /** M3 emphasized decelerate：进入 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** M3 emphasized accelerate：退出 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** M3 standard decelerate */
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /** M3 standard accelerate */
    val StandardAccelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    const val DURATION_SHORT2 = 100
    const val DURATION_SHORT3 = 150
    const val DURATION_SHORT4 = 200
    const val DURATION_MEDIUM1 = 250
    const val DURATION_MEDIUM2 = 300
    const val DURATION_LONG2 = 500

    /** 进入动画：强调减速 */
    fun <T> enter(
        durationMillis: Int = DURATION_MEDIUM2,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, EmphasizedDecelerate)

    /** 退出动画：强调加速 */
    fun <T> exit(
        durationMillis: Int = DURATION_SHORT4,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, EmphasizedAccelerate)

    /** 属性变化：标准减速 */
    fun <T> standard(
        durationMillis: Int = DURATION_SHORT4,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, StandardDecelerate)

    /** 可交互元素（按键、开关）的回弹 */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** 位移类动画：低回弹，避免手表上产生"晃动"感 */
    fun offset(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
