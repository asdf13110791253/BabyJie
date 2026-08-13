// File: app/src/main/java/com/probilliards/ai/service/FloatingWindowService.kt
package com.probilliards.ai.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.probilliards.ai.R
import com.probilliards.ai.overlay.ControlPanelView
import com.probilliards.ai.overlay.FloatingBallView
import com.probilliards.ai.overlay.GuideLineView
import com.probilliards.ai.overlay.SelectionRectView
import kotlinx.coroutines.*

/**
 * 悬浮窗服务
 * 管理所有悬浮窗视图
 */
class FloatingWindowService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1002
        
        var isRunning = false
        var instance: FloatingWindowService? = null
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var floatingBallView: FloatingBallView
    private lateinit var controlPanelView: ControlPanelView
    private lateinit var guideLineView: GuideLineView
    private lateinit var selectionRectView: SelectionRectView
    
    private lateinit var floatingBallParams: WindowManager.LayoutParams
    private lateinit var controlPanelParams: WindowManager.LayoutParams
    private lateinit var guideLineParams: WindowManager.LayoutParams
    private lateinit var selectionRectParams: WindowManager.LayoutParams
    
    private val handler = Handler(Looper.getMainLooper())
    private var frameProcessingJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        initOverlayViews()
        startFrameProcessing()
    }
    
    /**
     * 初始化所有悬浮窗视图
     */
    private fun initOverlayViews() {
        // 初始化悬浮球
        floatingBallView = FloatingBallView(this).apply {
            onBallClick = { toggleControlPanel() }
            onBallDrag = { x, y -> updateFloatingBallPosition(x, y) }
        }
        floatingBallParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }
        
        // 初始化控制面板
        controlPanelView = ControlPanelView(this).apply {
            onToggleChange = { feature, enabled ->
                handleFeatureToggle(feature, enabled)
            }
            onAdjustAreaClick = { showSelectionRect() }
            onOpenSettingsClick = { /* 打开设置页 */ }
            onCloseClick = { stopSelf() }
        }
        controlPanelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 500
        }
        
        // 初始化辅助线视图
        guideLineView = GuideLineView(this)
        guideLineParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        // 初始化选择区域视图
        selectionRectView = SelectionRectView(this).apply {
            onRectChanged = { rect ->
                saveSelectionRect(rect)
            }
            onConfirmClick = { hideSelectionRect() }
            onCancelClick = { hideSelectionRect() }
        }
        selectionRectParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        // 添加视图到窗口
        windowManager.addView(floatingBallView, floatingBallParams)
        windowManager.addView(guideLineView, guideLineParams)
        controlPanelView.visibility = View.GONE
        windowManager.addView(controlPanelView, controlPanelParams)
        selectionRectView.visibility = View.GONE
        windowManager.addView(selectionRectView, selectionRectParams)
    }
    
    /**
     * 切换控制面板显示
     */
    private fun toggleControlPanel() {
        if (controlPanelView.visibility == View.VISIBLE) {
            controlPanelView.visibility = View.GONE
        } else {
            controlPanelView.visibility = View.VISIBLE
            controlPanelParams.x = floatingBallParams.x
            controlPanelParams.y = floatingBallParams.y + 100
            windowManager.updateViewLayout(controlPanelView, controlPanelParams)
        }
    }
    
    /**
     * 更新悬浮球位置
     */
    private fun updateFloatingBallPosition(x: Int, y: Int) {
        floatingBallParams.x = x
        floatingBallParams.y = y
        windowManager.updateViewLayout(floatingBallView, floatingBallParams)
        
        if (controlPanelView.visibility == View.VISIBLE) {
            controlPanelParams.x = x
            controlPanelParams.y = y + 100
            windowManager.updateViewLayout(controlPanelView, controlPanelParams)
        }
    }
    
    /**
     * 显示选择区域
     */
    private fun showSelectionRect() {
        controlPanelView.visibility = View.GONE
        selectionRectView.visibility = View.VISIBLE
    }
    
    /**
     * 隐藏选择区域
     */
    private fun hideSelectionRect() {
        selectionRectView.visibility = View.GONE
    }
    
    /**
     * 保存选择区域
     */
    private fun saveSelectionRect(rect: android.graphics.Rect) {
        val prefs = getSharedPreferences("probilliards_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("selection_left", rect.left)
            putInt("selection_top", rect.top)
            putInt("selection_right", rect.right)
            putInt("selection_bottom", rect.bottom)
            apply()
        }
    }
    
    /**
     * 处理功能开关
     */
    private fun handleFeatureToggle(feature: String, enabled: Boolean) {
        guideLineView.setFeatureEnabled(feature, enabled)
    }
    
    /**
     * 开始帧处理
     */
    private fun startFrameProcessing() {
        frameProcessingJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRunning) {
                ScreenCaptureService.screenBitmap?.let { bitmap ->
                    processFrame(bitmap)
                }
                delay(50) // 20 FPS处理
            }
        }
    }
    
    /**
     * 处理帧
     */
    private suspend fun processFrame(bitmap: android.graphics.Bitmap) {
        withContext(Dispatchers.Main) {
            guideLineView.updateFrame(bitmap)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_overlay_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_overlay_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        isRunning = false
        instance = null
        frameProcessingJob?.cancel()
        
        // 移除所有悬浮窗
        if (::floatingBallView.isInitialized) {
            windowManager.removeView(floatingBallView)
        }
        if (::controlPanelView.isInitialized) {
            windowManager.removeView(controlPanelView)
        }
        if (::guideLineView.isInitialized) {
            windowManager.removeView(guideLineView)
        }
        if (::selectionRectView.isInitialized) {
            windowManager.removeView(selectionRectView)
        }
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
