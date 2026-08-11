package com.babyjie

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 白球位置检测器
 * 在指定区域内搜索高亮度、颜色接近中性的像素，返回最似白球的坐标
 */
class BallDetector {

    /**
     * @param bitmap 当前画面帧
     * @param region 可选，限定搜索区域（来自桌布调整的矩形），如果为 null 则全图搜索
     * @return 白球位置（屏幕坐标），如果没找到返回 null
     */
    fun detectCueBall(bitmap: Bitmap, region: RectF? = null): BallPosition? {
        val width = bitmap.width
        val height = bitmap.height

        // 如果传入了区域，限制搜索范围；否则全图
        val left = ((region?.left ?: 0f) / width).coerceIn(0f, 1f) * width
        val top = ((region?.top ?: 0f) / height).coerceIn(0f, 1f) * height
        val right = ((region?.right ?: width.toFloat()) / width).coerceIn(0f, 1f) * width
        val bottom = ((region?.bottom ?: height.toFloat()) / height).coerceIn(0f, 1f) * height

        var bestX = 0f
        var bestY = 0f
        var bestScore = 0f
        val step = 6 // 采样步长，兼顾性能

        for (y in top.toInt() until bottom.toInt() step step) {
            for (x in left.toInt() until right.toInt() step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val brightness = (r + g + b) / 3f
                // 颜色中性度：RGB 差异越小越像白色
                val neutrality = 1f - (Math.abs(r - g) + Math.abs(g - b) + Math.abs(b - r)) / 765f

                // 白球特征：高亮度 + 中性色
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

        // 分数阈值，防止误检
        return if (bestScore > 0.5f) BallPosition(bestX, bestY, bestScore) else null
    }
}

/**
 * 白球位置数据类
 * @param x 屏幕 X 坐标（像素）
 * @param y 屏幕 Y 坐标（像素）
 * @param confidence 置信度 0~1
 */
data class BallPosition(val x: Float, val y: Float, val confidence: Float)
