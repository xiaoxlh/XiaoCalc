package com.example.xiaocalc.adaptive

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 圆形表盘几何（设计坐标系，边长 [DESIGN] = 384）。
 *
 * 设计约定：把屏幕短边映射为 384 个"设计单位"（du），圆形屏时该正方形即表盘外接正方形，
 * 圆心位于 (192, 192)、半径 192du。所有需要贴边的内容都必须先向 [chordInBand] 询问
 * "这一段纵向区间里最窄处有多宽"，而不是靠 if (round) padding = 62f 之类的魔数内收。
 *
 * 本文件是纯 Kotlin（无 Android / Compose 依赖），可直接在 JVM 单元测试中验证。
 */
object Disc {

    const val DESIGN = 384f
    const val CENTER = DESIGN / 2f
    const val RADIUS = DESIGN / 2f

    private const val R2 = RADIUS * RADIUS

    /** 距表盘顶端 [y] 处的可用弦长（水平方向）。圆外返回 0。 */
    fun chord(y: Float): Float {
        val dy = y - CENTER
        val squared = R2 - dy * dy
        return if (squared <= 0f) 0f else 2f * sqrt(squared)
    }

    /**
     * 纵向区间 [[yTop], [yBottom]] 内处处可用的最窄弦长。
     *
     * 弦长关于圆心对称、且随 |dy| 单调递减，因此最窄处必然落在离圆心较远的那一端。
     */
    fun chordInBand(yTop: Float, yBottom: Float): Float {
        val top = minOf(yTop, yBottom)
        val bottom = maxOf(yTop, yBottom)
        val farthest = maxOf(abs(top - CENTER), abs(bottom - CENTER))
        return chord(CENTER + farthest)
    }

    /** 内接正方形边长 = R·√2，圆形屏上"整块矩形内容"的绝对上限。 */
    fun inscribedSquare(radius: Float = RADIUS): Float = radius * sqrt(2f)
}
