// File: app/src/main/java/com/probilliards/ai/service/ScreenCaptureService.kt
package com.probilliards.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        
        var isRunning = false
        var screenBitmap: Bitmap? = null
        var instance: ScreenCaptureService? = null
    }
    
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var captureJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        handler = Handler(Looper.getMainLooper())
        
        try {
            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        } catch (e: Exception) {
            Log.e(TAG, "获取屏幕参数失败: ${e.message}")
        }
        
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("resultData", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("resultData")
        }
        
        if (resultCode != -1 && resultData != null) {
            try {
                setupMediaProjection(resultCode, resultData)
            } catch (e: Exception) {
                Log.e(TAG, "MediaProjection设置失败: ${e.message}")
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        if (mediaProjection == null) {
            stopSelf()
            return
        }
        
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ProBilliardsCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler
        )
        
        startCaptureLoop()
    }
    
    private fun startCaptureLoop() {
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning) {
                try {
                    val image = imageReader.acquireLatestImage() ?: continue
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth
                    
                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    screenBitmap = croppedBitmap
                    
                    image.close()
                    delay(33)
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败: ${e.message}")
                    delay(100)
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕采集", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProBilliards AI")
            .setContentText("屏幕采集中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        isRunning = false
        instance = null
        captureJob?.cancel()
        try {
            if (::virtualDisplay.isInitialized) virtualDisplay.release()
            if (::imageReader.isInitialized) imageReader.close()
            if (::mediaProjection.isInitialized) mediaProjection.stop()
        } catch (e: Exception) {
            Log.e(TAG, "资源释放失败: ${e.message}")
        }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
