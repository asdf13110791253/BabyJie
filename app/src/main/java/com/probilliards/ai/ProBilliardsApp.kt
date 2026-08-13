// File: app/src/main/java/com/probilliards/ai/ProBilliardsApp.kt
package com.probilliards.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProBilliardsApp : Application() {
    
    companion object {
        lateinit var instance: ProBilliardsApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        // OpenCV 初始化已移除
    }
}
