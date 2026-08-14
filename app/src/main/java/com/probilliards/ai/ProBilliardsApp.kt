package com.probilliards.ai

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class ProBilliardsApp : Application() {
    
    companion object {
        private const val TAG = "ProBilliardsApp"
        lateinit var instance: ProBilliardsApp
            private set
        var isOpenCVInitialized = false
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    fun initOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        return try {
            val result = OpenCVLoader.initLocal()
            isOpenCVInitialized = result
            Log.d(TAG, "OpenCV 初始化: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV 初始化失败: ${e.message}")
            false
        }
    }
}
