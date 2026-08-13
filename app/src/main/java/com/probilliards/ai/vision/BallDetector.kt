package com.probilliards.ai.vision

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

data class Ball(
    val position: PointF,
    val radius: Float,
    val color: BallColor,
    val isWhite: Boolean,
    val confidence: Float = 1.0f
)

enum class BallColor {
    WHITE, RED, YELLOW, BLACK, OTHER
}

class BallDetector {
    
    fun detectBalls(bitmap: Bitmap): List<Ball> {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val hsvMat = Mat()
            Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
            val balls = mutableListOf<Ball>()
            val lowerWhite = Scalar(0.0, 0.0, 200.0)
            val upperWhite = Scalar(180.0, 30.0, 255.0)
            val maskWhite = Mat()
            Core.inRange(hsvMat, lowerWhite, upperWhite, maskWhite)
            extractBalls(maskWhite, BallColor.WHITE, true)?.let { balls.addAll(it) }
            balls
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractBalls(mask: Mat, color: BallColor, isWhite: Boolean): List<Ball>? {
        return try {
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val balls = mutableListOf<Ball>()
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < 100 || area > 5000) continue
                val center = Point()
                val radius = FloatArray(1)
                Imgproc.minEnclosingCircle(MatOfPoint2f(*contour.toArray()), center, radius)
                balls.add(Ball(
                    position = PointF(center.x.toFloat(), center.y.toFloat()),
                    radius = radius[0],
                    color = color,
                    isWhite = isWhite
                ))
            }
            balls
        } catch (e: Exception) {
            null
        }
    }
}
