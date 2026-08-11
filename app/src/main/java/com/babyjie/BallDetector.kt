package com.babyjie

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF

data class BallPosition(val x: Float, val y: Float, val confidence: Float)

class BallDetector {
    fun detectCueBall(bitmap: Bitmap, region: RectF? = null): BallPosition? {
        val width = bitmap.width
        val height = bitmap.height

        val left = ((region?.left ?: 0f) / width).coerceIn(0f, 1f) * width
        val top = ((region?.top ?: 0f) / height).coerceIn(0f, 1f) * height
        val right = ((region?.right ?: width.toFloat()) / width).coerceIn(0f, 1f) * width
        val bottom = ((region?.bottom ?: height.toFloat()) / height).coerceIn(0f, 1f) * height

        var bestX = 0f
        var bestY = 0f
        var bestScore = 0f
        val step = 6

        for (y in top.toInt() until bottom.toInt() step step) {
            for (x in left.toInt() until right.toInt() step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3f
                val neutrality = 1f - (Math.abs(r - g) + Math.abs(g - b) + Math.abs(b - r)) / 765f

                if (brightness > 180 && neutrality > 0.7f) {
                    val score = brightness / 255f * neutrality
                    if (score > bestScore) {
                        bestScore = score
                        bestX = x.toFloat()
                        bestY = y.toFloat()
                    }
                }
            }
        }
        return if (bestScore > 0.5f) BallPosition(bestX, bestY, bestScore) else null
    }
}
