// File: app/src/main/java/com/probilliards/ai/ProBilliardsApp.kt
package com.probilliards.ai

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * ProBilliards AI 应用入口
 * 使用Hilt进行依赖注入
 * 初始化OpenCV库
 */
@HiltAndroidApp
class ProBilliardsApp : Application() {
    
    companion object {
        private const val TAG = "ProBilliardsApp"
        
        lateinit var instance: ProBilliardsApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化OpenCV
        initOpenCV()
    }
    
    /**
     * 初始化OpenCV库
     * 先尝试调试模式初始化，失败后尝试本地初始化
     */
    private fun initOpenCV() {
        try {
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV调试模式初始化失败，尝试本地初始化")
                if (!org.opencv.android.OpenCVLoader.initLocal()) {
                    Log.e(TAG, "OpenCV本地初始化也失败")
                } else {
                    Log.d(TAG, "OpenCV本地初始化成功")
                }
            } else {
                Log.d(TAG, "OpenCV初始化成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV初始化异常: ${e.message}")
        }
    }
}
