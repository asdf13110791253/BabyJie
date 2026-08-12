package com.babyjie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlin.math.hypot

class FloatWindowService : Service() {

    companion object {
        const val CHANNEL_ID = "float_window_channel"
        const val NOTIFICATION_ID = 1001
        private const val TOUCH_SLOP = 10f
    }

    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var menuExpanded = false
    private var initX = 0
    private var initY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var hasMoved = false

    private val displayMetrics = DisplayMetrics()

    private val floatParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
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
            Toast.makeText(this, "悬浮窗创建失败", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startForegroundService() {
        val channelId = "float_window_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("台球辅助运行中")
            .setContentText("点击悬浮按钮展开菜单")
            .setSmallIcon(R.drawable.ic_launcher_temp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)
        menuBar = floatRoot.findViewById(R.id.menuBar)

        wm.addView(floatRoot, floatParams)

        mainBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = floatParams.x; initY = floatParams.y
                    downRawX = event.rawX; downRawY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
                    if (!hasMoved && hypot(dx, dy) > TOUCH_SLOP) {
                        hasMoved = true
                        if (menuExpanded) hideMenu()
                    }
                    if (hasMoved) {
                        floatParams.x = initX + dx.toInt()
                        floatParams.y = initY + dy.toInt()
                        wm.updateViewLayout(floatRoot, floatParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainBtn.performClick()
                    if (!hasMoved) {
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }

        // 菜单项点击（暂时用 Toast 表示，功能正常后可替换为真实功能）
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

        // 获取按钮在屏幕上的位置
        val btnLoc = IntArray(2)
        mainBtn.getLocationOnScreen(btnLoc)
        val btnCenterX = btnLoc[0] + mainBtn.width / 2f
        val btnTop = btnLoc[1]

        // 菜单宽度设为屏幕的 55%
        val menuW = (displayMetrics.widthPixels * 0.55f).toInt()
        // 菜单高度固定为 28dp 转换后的像素值
        val menuH = (28 * resources.displayMetrics.density).toInt()

        // 菜单水平居中于按钮
        val x = (btnCenterX - menuW / 2f).toInt().coerceIn(0, displayMetrics.widthPixels - menuW)
        // 菜单垂直位于按钮上方，留出 20px 间距
        val y = (btnTop - menuH - 20).coerceAtLeast(0)

        menuBar.layoutParams.width = menuW
        menuBar.x = x.toFloat()
        menuBar.y = y.toFloat()
        menuBar.visibility = View.VISIBLE
        menuExpanded = true
    }

    private fun hideMenu() {
        if (!menuExpanded) return
        menuBar.visibility = View.GONE
        menuExpanded = false
    }

    override fun onDestroy() {
        if (::floatRoot.isInitialized) wm.removeView(floatRoot)
        stopForeground(true)
        super.onDestroy()
    }
}
