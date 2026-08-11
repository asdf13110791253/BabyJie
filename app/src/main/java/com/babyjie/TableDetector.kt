package com.babyjie

import android.graphics.Bitmap
import android.graphics.Color

class TableDetector {

    // 绿色比例阈值
    private val greenThreshold = 0.12f
    // 边缘密度阈值（排除纯色草地）
    private val edgeDensityThreshold = 0.05f

    /**
     * 检测画面中是否存在台球桌
     * 基于绿色占比 + 边缘纹理密度，防止误判纯色草地
     */
    fun detectTable(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var greenPixels = 0
        var edgePixels = 0
        val totalSampled = (width / 4) * (height / 4)

        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (isGreen(r, g, b)) greenPixels++

                // 简易边缘检测：比较右侧和下侧像素的差异
                if (x < width - 4 && y < height - 4) {
                    val pixelRight = bitmap.getPixel(x + 4, y)
                    val pixelDown = bitmap.getPixel(x, y + 4)
                    val diffRight = colorDiff(pixel, pixelRight)
                    val diffDown = colorDiff(pixel, pixelDown)
                    if (diffRight > 40 || diffDown > 40) edgePixels++
                }
            }
        }

        val greenRatio = greenPixels.toFloat() / totalSampled
        val edgeRatio = edgePixels.toFloat() / totalSampled
        return greenRatio > greenThreshold && edgeRatio > edgeDensityThreshold
    }

    /**
     * 判断一个像素是否为绿色（G 分量显著高于 R、B）
     */
    private fun isGreen(r: Int, g: Int, b: Int): Boolean =
        g > r + 20 && g > b + 20 && g > 80

    /**
     * 计算两个像素的颜色差异（RGB 差值绝对值之和）
     */
    private fun colorDiff(c1: Int, c2: Int): Int =
        Math.abs(Color.red(c1) - Color.red(c2)) +
        Math.abs(Color.green(c1) - Color.green(c2)) +
        Math.abs(Color.blue(c1) - Color.blue(c2))
}
