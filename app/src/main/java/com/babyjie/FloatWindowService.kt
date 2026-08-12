package com.babyjie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatWindowService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var menuExpanded = false
    private var initX = 0
    private var initY = 0
    private var downX = 0f
    private var downY = 0f
    private var hasMoved = false

    private var displayMetrics = DisplayMetrics()

    private val floatParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }
        startForegroundService()
        wm.defaultDisplay.getMetrics(displayMetrics)

        try {
            createFloatView()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startForegroundService() {
        val channelId = "float_window_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("台球辅助运行中")
            .setContentText("悬浮窗已开启")
            .setSmallIcon(R.drawable.ic_launcher_temp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)
        menuBar = floatRoot.findViewById(R.id.menuBar)

        floatParams.gravity = Gravity.TOP or Gravity.START
        floatParams.x = 100
        floatParams.y = 400
        wm.addView(floatRoot, floatParams)

        mainBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = floatParams.x
                    initY = floatParams.y
                    downX = event.rawX
                    downY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    // 移动超过 8 像素才算拖动，防止手抖误触
                    if (Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()) > 8f) {
                        hasMoved = true
                        floatParams.x = initX + deltaX.toInt()
                        floatParams.y = initY + deltaY.toInt()
                        wm.updateViewLayout(floatRoot, floatParams)
                        // 拖动时自动收起菜单
                        if (menuExpanded) {
                            hideMenu()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // 没有移动，视为点击，弹出/收起菜单
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }

        // 菜单项点击事件（目前为提示，等资源齐全后可恢复完整功能）
        floatRoot.findViewById<TextView>(R.id.menuItem1)?.setOnClickListener {
            Toast.makeText(this, "AI分析功能即将开放", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem2)?.setOnClickListener {
            Toast.makeText(this, "台球参数功能即将开放", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem3)?.setOnClickListener {
            Toast.makeText(this, "桌布调整功能即将开放", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
    }

    private fun toggleMenu() {
        if (menuExpanded) hideMenu() else showMenu()
    }

    private fun showMenu() {
        if (menuExpanded) return
        menuExpanded = true
        val sw = displayMetrics.widthPixels
        val menuW = (sw * 0.55f).toInt()
        menuBar.layoutParams.width = menuW
        val loc = IntArray(2)
        mainBtn.getLocationOnScreen(loc)
        val cx = loc[0] + mainBtn.width / 2f
        val top = loc[1]
        val mx = (cx - menuW / 2f).toInt().coerceIn(0, sw - menuW)
        val my = (top - menuBar.layoutParams.height - 20).coerceAtLeast(0)
        menuBar.x = mx.toFloat()
        menuBar.y = my.toFloat()
        menuBar.visibility = View.VISIBLE
        menuBar.alpha = 0f
        menuBar.scaleX = 0.8f
        menuBar.scaleY = 0.8f
        menuBar.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun hideMenu() {
        if (!menuExpanded) return
        menuExpanded = false
        menuBar.animate()
            .alpha(0f).scaleX(0.8f).scaleY(0.8f)
            .setDuration(150)
            .withEndAction { menuBar.visibility = View.GONE }
            .start()
    }

    override fun onDestroy() {
        if (::floatRoot.isInitialized) wm.removeView(floatRoot)
        stopForeground(true)
        super.onDestroy()
    }
}
