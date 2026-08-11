package com.babyjie

import android.app.Service
import android.content.Intent
import android.graphics.PointF
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlin.math.pow
import kotlin.math.sqrt

class FloatWindowService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var menuExpanded = false
    private var initX = 0; private var initY = 0
    private var initTX = 0f; private var initTY = 0f
    private var dragging = false

    private var clothView: TableClothView? = null; private var clothVisible = false
    private var paramsPanel: ParamsPanelView? = null; private var paramsVisible = false
    private var aimCircle: AimCircleView? = null; private var aimVisible = false
    private var modeBar: View? = null; private var modeVisible = false
    private var currentMode = LineType.STRAIGHT
    private var lineOverlay: AimLineOverlay? = null; private var overlayVisible = false
    private val pathCalc = PathCalculator()
    private var region: RectF? = null
    private var tableDetected = false
    private var cueBallPos: BallPosition? = null
    private var allBalls = mutableListOf<BallPosition>()
    private var power = 50; private var angle = 45; private var sensitivity = 5
    private val handler = Handler(Looper.getMainLooper())
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

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm.defaultDisplay.getMetrics(displayMetrics)
        ScreenCaptureService.detectionCallback = object : ScreenCaptureService.DetectionCallback {
            override fun onTableDetected(table: Boolean, ball: BallPosition?) {
                tableDetected = table
                if (ball != null) cueBallPos = ball
                allBalls = if (ball != null) mutableListOf(ball) else mutableListOf()
                handler.post { updateOverlay() }
            }
        }
        createFloatView()
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)
        menuBar = floatRoot.findViewById(R.id.menuBar)
        floatParams.gravity = Gravity.TOP or Gravity.START
        floatParams.x = 100; floatParams.y = 400
        wm.addView(floatRoot, floatParams)

        mainBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = floatParams.x; initY = floatParams.y
                    initTX = ev.rawX; initTY = ev.rawY; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initTX; val dy = ev.rawY - initTY
                    if (sqrt(dx*dx + dy*dy) > 10f) dragging = true
                    if (dragging) {
                        floatParams.x = initX + dx.toInt(); floatParams.y = initY + dy.toInt()
                        wm.updateViewLayout(floatRoot, floatParams)
                        if (menuExpanded) hideMenu()
                        if (clothVisible) hideCloth(); if (paramsVisible) hideParams()
                    }; true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) toggleMenu(); true }
                else -> false
            }
        }

        floatRoot.findViewById<TextView>(R.id.menuItem1).setOnClickListener {
            if (modeVisible) hideModeBar() else showModeBar(); hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem2).setOnClickListener {
            if (paramsVisible) hideParams() else showParams(); hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem3).setOnClickListener {
            if (clothVisible) hideCloth() else showCloth(); hideMenu()
        }
    }

    // 菜单动画
    private fun toggleMenu() { if (menuExpanded) hideMenu() else showMenu() }
    private fun showMenu() { /* ... 代码不变 ... */ }
    private fun hideMenu() { /* ... 代码不变 ... */ }

    // ========== 完整实现：桌布调整 ==========
    private fun showCloth() {
        if (clothVisible) return
        clothVisible = true
        clothView = TableClothView(this).apply {
            onDoneListener = { r ->
                region = r
                hideCloth()
            }
        }
        wm.addView(clothView, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))
    }

    private fun hideCloth() {
        if (!clothVisible || clothView == null) return
        clothVisible = false
        wm.removeView(clothView)
        clothView = null
    }

    // ========== 完整实现：台球参数面板 ==========
    private fun showParams() {
        if (paramsVisible) return
        paramsVisible = true
        paramsPanel = ParamsPanelView(this).apply {
            onDoneListener = { p, a, s ->
                power = p
                angle = a
                sensitivity = s
                aimCircle?.circleSizeDp = mapPowerToSize(p)
                aimCircle?.setDirectionLine(a.toFloat(), mapPowerToLength(p), true)
                hideParams()
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 500 }
        wm.addView(paramsPanel, lp)
    }

    private fun hideParams() {
        if (!paramsVisible || paramsPanel == null) return
        paramsVisible = false
        wm.removeView(paramsPanel)
        paramsPanel = null
    }

    // 模式选择条、瞄准圈等其余方法保持原样...
    // （需确保 showModeBar, selectMode, showAimAndOverlay, updateOverlay 等方法均已存在，且与之前完整版一致）

    override fun onDestroy() {
        if (clothVisible && clothView != null) wm.removeView(clothView)
        if (paramsVisible && paramsPanel != null) wm.removeView(paramsPanel)
        if (aimVisible && aimCircle != null) wm.removeView(aimCircle)
        if (overlayVisible && lineOverlay != null) wm.removeView(lineOverlay)
        if (modeVisible && modeBar != null) wm.removeView(modeBar)
        if (::floatRoot.isInitialized) wm.removeView(floatRoot)
        ScreenCaptureService.detectionCallback = null
        super.onDestroy()
    }

    // 其他辅助函数（dist, mapPowerToSize 等）保持不变
    private fun mapPowerToSize(p: Int) = 40f + (p/100f)*80f
    private fun mapPowerToLength(p: Int) = 20f + (p/100f)*60f
}
