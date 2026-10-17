package com.example.xiaocalc.adaptive

import kotlin.math.abs
import kotlin.math.sqrt

/** Design units: the short screen side is 384du, centre at (192, 192). */
object Disc {
    const val DESIGN = 384f
    const val CENTER = DESIGN / 2f
    const val RADIUS = DESIGN / 2f

    private const val R2 = RADIUS * RADIUS

    fun chord(y: Float): Float {
        val dy = y - CENTER
        val squared = R2 - dy * dy
        return if (squared <= 0f) 0f else 2f * sqrt(squared)
    }

    fun chordInBand(yTop: Float, yBottom: Float): Float {
        val top = minOf(yTop, yBottom)
        val bottom = maxOf(yTop, yBottom)
        val farthest = maxOf(abs(top - CENTER), abs(bottom - CENTER))
        return chord(CENTER + farthest)
    }

    fun inscribedSquare(radius: Float = RADIUS): Float = radius * sqrt(2f)
}
