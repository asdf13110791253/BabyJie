// File: app/src/main/java/com/probilliards/ai/vision/TableDetector.kt
package com.probilliards.ai.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class TableDetector {
    
    fun detectTable(bitmap: Bitmap, selectionRect: Rect? = null): MatOfPoint2f? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            val processMat = if (selectionRect != null) Mat(mat, selectionRect) else mat
            
            val grayMat = Mat()
            Imgproc.cvtColor(processMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
            
            val edgesMat = Mat()
            Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            var largestQuad: MatOfPoint2f? = null
            var largestArea = 0.0
            
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < 10000) continue
                
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
                
                if (approx.total() == 4L && area > largestArea) {
                    largestArea = area
                    largestQuad = approx
                }
            }
            
            largestQuad
        } catch (e: Exception) {
            null
        }
    }
}
