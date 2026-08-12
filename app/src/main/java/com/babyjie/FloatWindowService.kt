package com.babyjie

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

class FloatWindowService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout

    private var initX = 0
    private var initY = 0
    private var initTX = 0f
    private var initTY = 0f
    private var dragging = false

    private val floatParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }

        try {
            createFloatView()
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗创建失败，请检查资源文件", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)

        floatParams.gravity = Gravity.TOP or Gravity.START
        floatParams.x = 100
        floatParams.y = 400
        wm.addView(floatRoot, floatParams)

        mainBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = floatParams.x; initY = floatParams.y
                    initTX = ev.rawX; initTY = ev.rawY; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initTX; val dy = ev.rawY - initTY
                    if (Math.sqrt((dx * dx + dy * dy).toDouble()) > 10f) dragging = true
                    if (dragging) {
                        floatParams.x = initX + dx.toInt(); floatParams.y = initY + dy.toInt()
                        wm.updateViewLayout(floatRoot, floatParams)
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) Toast.makeText(this, "悬浮窗已启动，可打开任意台球游戏", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        if (::floatRoot.isInitialized) wm.removeView(floatRoot)
        super.onDestroy()
    }
}
