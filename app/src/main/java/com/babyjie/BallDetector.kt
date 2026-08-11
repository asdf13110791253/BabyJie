package com.babyjie

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF

data class BallPosition(val x: Float, val y: Float, val confidence: Float, val color: Int = Color.WHITE)

class BallDetector {

    fun detectAllBalls(bitmap: Bitmap, region: RectF? = null): List<BallPosition> {
        val balls = mutableListOf<BallPosition>()
        val width = bitmap.width; val height = bitmap.height
        val left = ((region?.left ?: 0f) / width).coerceIn(0f, 1f) * width
        val top = ((region?.top ?: 0f) / height).coerceIn(0f, 1f) * height
        val right = ((region?.right ?: width.toFloat()) / width).coerceIn(0f, 1f) * width
        val bottom = ((region?.bottom ?: height.toFloat()) / height).coerceIn(0f, 1f) * height
        val step = 8; val minDist = 15

        for (y in top.toInt() until bottom.toInt() step step) {
            for (x in left.toInt() until right.toInt() step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3f
                if (brightness > 80) {
                    if (balls.none { Math.hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < minDist }) {
                        balls.add(BallPosition(x.toFloat(), y.toFloat(), brightness / 255f, classifyBall(r, g, b)))
                    }
                }
            }
        }
        return balls.sortedByDescending { it.confidence }.take(16)
    }

    private fun classifyBall(r: Int, g: Int, b: Int): Int {
        if (r < 50 && g < 50 && b < 50) return Color.BLACK      // 黑八
        if (r > 180 && g > 180 && b > 180) return Color.WHITE   // 白球
        return Color.rgb(r, g, b)                               // 其他球保留原色
    }

    fun detectCueBall(bitmap: Bitmap, region: RectF? = null): BallPosition? {
        return detectAllBalls(bitmap, region).firstOrNull { it.color == Color.WHITE }
    }
}
