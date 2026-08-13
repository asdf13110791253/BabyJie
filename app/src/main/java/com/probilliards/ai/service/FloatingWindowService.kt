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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.probilliards.ai.R
import com.probilliards.ai.overlay.ControlPanelView
import com.probilliards.ai.overlay.FloatingBallView
import com.probilliards.ai.overlay.GuideLineView
import com.probilliards.ai.overlay.SelectionRectView
import kotlinx.coroutines.*

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
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        initOverlayViews()
    }
    
    private fun initOverlayViews() {
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
        
        controlPanelView = ControlPanelView(this).apply {
            onToggleChange = { feature, enabled -> guideLineView.setFeatureEnabled(feature, enabled) }
            onAdjustAreaClick = { showSelectionRect() }
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
        
        guideLineView = GuideLineView(this)
        guideLineParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        
        selectionRectView = SelectionRectView(this).apply {
            onRectChanged = { rect -> saveSelectionRect(rect) }
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
        ).apply { gravity = Gravity.TOP or Gravity.START }
        
        windowManager.addView(floatingBallView, floatingBallParams)
        windowManager.addView(guideLineView, guideLineParams)
        controlPanelView.visibility = View.GONE
        windowManager.addView(controlPanelView, controlPanelParams)
        selectionRectView.visibility = View.GONE
        windowManager.addView(selectionRectView, selectionRectParams)
    }
    
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
    
    private fun updateFloatingBallPosition(x: Int, y: Int) {
        floatingBallParams.x = x
        floatingBallParams.y = y
        windowManager.updateViewLayout(floatingBallView, floatingBallParams)
    }
    
    private fun showSelectionRect() {
        controlPanelView.visibility = View.GONE
        selectionRectView.visibility = View.VISIBLE
    }
    
    private fun hideSelectionRect() {
        selectionRectView.visibility = View.GONE
    }
    
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
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProBilliards AI")
            .setContentText("悬浮辅助运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build())
        return START_STICKY
    }
    
    override fun onDestroy() {
        isRunning = false
        instance = null
        windowManager.removeView(floatingBallView)
        windowManager.removeView(controlPanelView)
        windowManager.removeView(guideLineView)
        windowManager.removeView(selectionRectView)
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
