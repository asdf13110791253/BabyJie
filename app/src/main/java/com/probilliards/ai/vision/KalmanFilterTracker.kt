// File: app/src/main/java/com/probilliards/ai/vision/KalmanFilterTracker.kt
package com.probilliards.ai.vision

import android.graphics.PointF
import org.opencv.core.*
import org.opencv.video.KalmanFilter
import org.opencv.video.Video

/**
 * 卡尔曼滤波器追踪器
 * 用于平滑球的位置检测
 */
class KalmanFilterTracker {
    
    companion object {
        private const val STATE_SIZE = 4  // x, y, vx, vy
        private const val MEASUREMENT_SIZE = 2  // x, y
    }
    
    private val kalmanFilter = KalmanFilter(STATE_SIZE, MEASUREMENT_SIZE, 0, CvType.CV_32F)
    private val measurement = Mat(MEASUREMENT_SIZE, 1, CvType.CV_32F)
    private var isInitialized = false
    
    init {
        // 状态转移矩阵
        val transitionMatrix = Mat(STATE_SIZE, STATE_SIZE, CvType.CV_32F)
        transitionMatrix.put(0, 0, 
            1.0, 0.0, 1.0, 0.0,
            0.0, 1.0, 0.0, 1.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        )
        kalmanFilter.set_transitionMatrix(transitionMatrix)
        
        // 测量矩阵
        val measurementMatrix = Mat(MEASUREMENT_SIZE, STATE_SIZE, CvType.CV_32F)
        measurementMatrix.put(0, 0,
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0
        )
        kalmanFilter.set_measurementMatrix(measurementMatrix)
        
        // 过程噪声
        val processNoise = Mat(STATE_SIZE, STATE_SIZE, CvType.CV_32F)
        Core.setIdentity(processNoise, Scalar(0.01))
        kalmanFilter.set_processNoiseCov(processNoise)
        
        // 测量噪声
        val measurementNoise = Mat(MEASUREMENT_SIZE, MEASUREMENT_SIZE, CvType.CV_32F)
        Core.setIdentity(measurementNoise, Scalar(0.1))
        kalmanFilter.set_measurementNoiseCov(measurementNoise)
        
        // 后验误差协方差
        val errorCovPost = Mat(STATE_SIZE, STATE_SIZE, CvType.CV_32F)
        Core.setIdentity(errorCovPost, Scalar(0.1))
        kalmanFilter.set_errorCovPost(errorCovPost)
    }
    
    /**
     * 更新球的位置并进行平滑
     */
    fun update(position: PointF): PointF {
        if (!isInitialized) {
            // 初始化状态
            val state = Mat(STATE_SIZE, 1, CvType.CV_32F)
            state.put(0, 0, position.x.toDouble(), position.y.toDouble(), 0.0, 0.0)
            kalmanFilter.set_statePost(state)
            isInitialized = true
            return position
        }
        
        // 预测
        val prediction = kalmanFilter.predict()
        
        // 更新测量
        measurement.put(0, 0, position.x.toDouble(), position.y.toDouble())
        val corrected = kalmanFilter.correct(measurement)
        
        return PointF(
            corrected.get(0, 0)[0].toFloat(),
            corrected.get(1, 0)[0].toFloat()
        )
    }
    
    /**
     * 预测下一帧位置
     */
    fun predict(): PointF {
        if (!isInitialized) return PointF(0f, 0f)
        val prediction = kalmanFilter.predict()
        return PointF(
            prediction.get(0, 0)[0].toFloat(),
            prediction.get(1, 0)[0].toFloat()
        )
    }
    
    fun reset() {
        isInitialized = false
    }
}
