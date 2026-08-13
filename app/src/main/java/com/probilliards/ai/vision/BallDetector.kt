// File: app/src/main/java/com/probilliards/ai/vision/BallDetector.kt
package com.probilliards.ai.vision

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 台球数据类
 */
data class Ball(
    val position: PointF,
    val radius: Float,
    val color: BallColor,
    val isWhite: Boolean
)

/**
 * 台球颜色枚举
 */
enum class BallColor {
    WHITE, RED, YELLOW, BLACK, OTHER
}

/**
 * 球体检测器
 * 使用OpenCV检测台球
 */
class BallDetector {
    
    companion object {
        private const val TAG = "BallDetector"
    }
    
    /**
     * 检测台球
     * @param bitmap 屏幕截图
     * @return 检测到的台球列表
     */
    fun detectBalls(bitmap: Bitmap): List<Ball> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 转换到HSV色彩空间
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        
        val balls = mutableListOf<Ball>()
        
        // 检测白球
        detectWhiteBalls(hsvMat)?.let { balls.addAll(it) }
        
        // 检测红色球
        detectRedBalls(hsvMat)?.let { balls.addAll(it) }
        
        // 检测黄色球
        detectYellowBalls(hsvMat)?.let { balls.addAll(it) }
        
        Log.d(TAG, "检测到 ${balls.size} 个球")
        
        return balls
    }
    
    /**
     * 检测白球
     */
    private fun detectWhiteBalls(hsvMat: Mat): List<Ball>? {
        // 白色球在HSV中的范围
        val lowerWhite = Scalar(0.0, 0.0, 200.0)
        val upperWhite = Scalar(180.0, 30.0, 255.0)
        
        val mask = Mat()
        Core.inRange(hsvMat, lowerWhite, upperWhite, mask)
        
        // 形态学操作去除噪点
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        
        return extractBallsFromMask(mask, BallColor.WHITE, isWhite = true)
    }
    
    /**
     * 检测红色球
     */
    private fun detectRedBalls(hsvMat: Mat): List<Ball>? {
        // 红色在HSV中有两个范围
        val lowerRed1 = Scalar(0.0, 100.0, 100.0)
        val upperRed1 = Scalar(10.0, 255.0, 255.0)
        val lowerRed2 = Scalar(160.0, 100.0, 100.0)
        val upperRed2 = Scalar(180.0, 255.0, 255.0)
        
        val maskRed1 = Mat()
        val maskRed2 = Mat()
        Core.inRange(hsvMat, lowerRed1, upperRed1, maskRed1)
        Core.inRange(hsvMat, lowerRed2, upperRed2, maskRed2)
        
        val maskRed = Mat()
        Core.add(maskRed1, maskRed2, maskRed)
        
        return extractBallsFromMask(maskRed, BallColor.RED, isWhite = false)
    }
    
    /**
     * 检测黄色球
     */
    private fun detectYellowBalls(hsvMat: Mat): List<Ball>? {
        // 黄色范围
        val lowerYellow = Scalar(20.0, 100.0, 100.0)
        val upperYellow = Scalar(30.0, 255.0, 255.0)
        
        val maskYellow = Mat()
        Core.inRange(hsvMat, lowerYellow, upperYellow, maskYellow)
        
        return extractBallsFromMask(maskYellow, BallColor.YELLOW, isWhite = false)
    }
    
    /**
     * 从掩码中提取球体
     */
    private fun extractBallsFromMask(
        mask: Mat, 
        color: BallColor,
        isWhite: Boolean
    ): List<Ball>? {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            mask, 
            contours, 
            hierarchy, 
            Imgproc.RETR_EXTERNAL, 
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        val balls = mutableListOf<Ball>()
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 100 || area > 5000) continue
            
            // 检查圆度
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = 4 * Math.PI * area / (perimeter * perimeter)
            if (circularity < 0.7) continue
            
            // 获取最小外接圆
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
        
        return balls
    }
}
