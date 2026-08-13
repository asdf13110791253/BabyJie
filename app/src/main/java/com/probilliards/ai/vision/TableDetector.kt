// File: app/src/main/java/com/probilliards/ai/vision/TableDetector.kt
package com.probilliards.ai.vision

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * 球桌检测器
 * 使用OpenCV检测球桌四边形并进行透视矫正
 */
class TableDetector {
    
    companion object {
        private const val TAG = "TableDetector"
    }
    
    /**
     * 检测球桌四边形
     * @param bitmap 屏幕截图
     * @param selectionRect 用户选择的识别区域
     * @return 球桌四个顶点（MatOfPoint2f），如果未检测到返回null
     */
    fun detectTable(bitmap: Bitmap, selectionRect: Rect? = null): MatOfPoint2f? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // 如果指定了选择区域，只处理该区域
        val processMat = if (selectionRect != null) {
            Mat(mat, selectionRect)
        } else {
            mat
        }
        
        val grayMat = Mat()
        Imgproc.cvtColor(processMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 高斯模糊
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
        
        // Canny边缘检测
        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)
        
        // 膨胀操作，连接边缘
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edgesMat, edgesMat, kernel)
        
        // 查找轮廓
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edgesMat,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        // 找到最大的四边形轮廓
        var largestQuad: MatOfPoint2f? = null
        var largestArea = 0.0
        
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 10000) continue // 过滤太小的轮廓
            
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(
                MatOfPoint2f(*contour.toArray()),
                approx,
                0.02 * peri,
                true
            )
            
            // 检查是否为四边形
            if (approx.total() == 4L && area > largestArea) {
                // 检查是否为凸四边形
                if (Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                    largestArea = area
                    largestQuad = approx
                }
            }
        }
        
        if (largestQuad != null) {
            Log.d(TAG, "检测到球桌，面积: $largestArea")
        } else {
            Log.d(TAG, "未检测到球桌")
        }
        
        return largestQuad
    }
    
    /**
     * 透视矫正球桌
     * @param bitmap 原始图像
     * @param tableCorners 球桌四个顶点
     * @return 矫正后的球桌图像
     */
    fun warpPerspective(bitmap: Bitmap, tableCorners: MatOfPoint2f): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        
        // 目标尺寸（标准台球桌比例约为2:1）
        val width = 1000
        val height = 500
        
        val dstCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )
        
        val transform = Imgproc.getPerspectiveTransform(tableCorners, dstCorners)
        val warpedMat = Mat()
        Imgproc.warpPerspective(
            srcMat, 
            warpedMat, 
            transform, 
            Size(width.toDouble(), height.toDouble())
        )
        
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpedMat, resultBitmap)
        
        return resultBitmap
    }
    
    /**
     * 对顶点进行排序（左上、右上、右下、左下）
     */
    private fun orderCorners(corners: Array<Point>): Array<Point> {
        if (corners.size != 4) return corners
        
        // 计算中心点
        val centerX = corners.sumOf { it.x } / corners.size
        val centerY = corners.sumOf { it.y } / corners.size
        
        // 按角度排序
        return corners.sortedBy { corner ->
            Math.atan2(corner.y - centerY, corner.x - centerX)
        }.toTypedArray()
    }
}
