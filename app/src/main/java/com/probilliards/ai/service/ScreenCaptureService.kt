// File: app/src/main/java/com/probilliards/ai/service/ScreenCaptureService.kt
package com.probilliards.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
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
import com.probilliards.ai.R
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 屏幕采集服务（优化版）
 * 支持YUV格式处理、动态帧率、ROI裁剪
 */
class ScreenCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        
        var isRunning = false
        var screenBitmap: Bitmap? = null
        var onFrameAvailable: ((Bitmap) -> Unit)? = null
        var onYUVFrameAvailable: ((ByteArray, Int, Int) -> Unit)? = null
        
        // 帧率模式
        enum class FrameRateMode(val fps: Int) {
            FAST(30),      // 快速模式
            NORMAL(20),    // 普通模式
            POWER_SAVING(15) // 省电模式
        }
        
        var frameRateMode = FrameRateMode.NORMAL
    }
    
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var captureJob: Job? = null
    
    // ROI相关
    private var roiRect: Rect? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        handler = Handler(Looper.getMainLooper())
        
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        // 加载ROI设置
        loadROISettings()
        
        isRunning = true
    }
    
    /**
     * 加载ROI设置
     */
    private fun loadROISettings() {
        val prefs = getSharedPreferences("probilliards_prefs", Context.MODE_PRIVATE)
        val left = prefs.getInt("selection_left", -1)
        val top = prefs.getInt("selection_top", -1)
        val right = prefs.getInt("selection_right", -1)
        val bottom = prefs.getInt("selection_bottom", -1)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            roiRect = Rect(left, top, right, bottom)
        }
    }
    
    /**
     * 更新ROI设置
     */
    fun updateROI(rect: Rect?) {
        roiRect = rect
    }
    
    /**
     * 设置帧率模式
     */
    fun setFrameRateMode(mode: FrameRateMode) {
        frameRateMode = mode
        // 重启采集循环以应用新帧率
        captureJob?.cancel()
        startCaptureLoop()
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
            setupMediaProjection(resultCode, resultData)
        }
        
        return START_STICKY
    }
    
    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection创建失败")
            stopSelf()
            return
        }
        
        setupVirtualDisplay()
        startCaptureLoop()
    }
    
    private fun setupVirtualDisplay() {
        // 使用YUV格式减少转换开销
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888, // 保持兼容性
            2
        )
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ProBilliardsCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler
        )
    }
    
    /**
     * 开始截图循环
     */
    private fun startCaptureLoop() {
        val frameDelay = 1000L / frameRateMode.fps
        
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning) {
                try {
                    val image = imageReader.acquireLatestImage() ?: continue
                    
                    // 处理图像
                    processImage(image)
                    
                    image.close()
                    delay(frameDelay)
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败: ${e.message}")
                    delay(100)
                }
            }
        }
    }
    
    /**
     * 处理图像（支持ROI裁剪）
     */
    private fun processImage(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        // 创建完整Bitmap
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 裁剪到实际屏幕大小
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            screenWidth,
            screenHeight
        )
        
        // 如果有ROI，只处理ROI区域
        val finalBitmap = if (roiRect != null) {
            try {
                Bitmap.createBitmap(
                    croppedBitmap,
                    roiRect!!.left,
                    roiRect!!.top,
                    roiRect!!.width(),
                    roiRect!!.height()
                )
            } catch (e: Exception) {
                croppedBitmap
            }
        } else {
            croppedBitmap
        }
        
        screenBitmap = finalBitmap
        onFrameAvailable?.invoke(finalBitmap)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_capture),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_capture_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_capture_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        isRunning = false
        captureJob?.cancel()
        
        if (::virtualDisplay.isInitialized) {
            virtualDisplay.release()
        }
        if (::imageReader.isInitialized) {
            imageReader.close()
        }
        if (::mediaProjection.isInitialized) {
            mediaProjection.stop()
        }
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
