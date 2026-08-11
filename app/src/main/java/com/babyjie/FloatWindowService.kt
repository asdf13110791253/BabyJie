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

    // 桌布
    private var clothView: TableClothView? = null; private var clothVisible = false
    // 参数面板
    private var paramsPanel: ParamsPanelView? = null; private var paramsVisible = false
    // 瞄准圈
    private var aimCircle: AimCircleView? = null; private var aimVisible = false
    // 模式选择条
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }
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

        // 菜单项
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

    private fun toggleMenu() { if (menuExpanded) hideMenu() else showMenu() }
    private fun showMenu() {
        if (menuExpanded) return; menuExpanded = true
        val sw = displayMetrics.widthPixels; val menuW = (sw * 0.55f).toInt()
        menuBar.layoutParams.width = menuW
        val loc = IntArray(2); mainBtn.getLocationOnScreen(loc)
        val cx = loc[0] + mainBtn.width/2f; val top = loc[1]
        val mx = (cx - menuW/2f).toInt().coerceIn(0, sw-menuW)
        val my = (top - menuBar.layoutParams.height - 20).coerceAtLeast(0)
        menuBar.x = mx.toFloat(); menuBar.y = my.toFloat()
        menuBar.visibility = View.VISIBLE
        menuBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }
    private fun hideMenu() {
        if (!menuExpanded) return; menuExpanded = false
        menuBar.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction { menuBar.visibility = View.GONE }.start()
    }

    // ===== 桌布、参数、模式、瞄准圈、路线全部完整实现（与之前最终版一致） =====
    private fun showCloth() {
        if (clothVisible) return; clothVisible = true
        clothView = TableClothView(this).apply { onDoneListener = { r -> region = r; hideCloth() } }
        wm.addView(clothView, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))
    }
    private fun hideCloth() { if (!clothVisible || clothView == null) return; clothVisible = false; wm.removeView(clothView); clothView = null }

    private fun showParams() {
        if (paramsVisible) return; paramsVisible = true
        paramsPanel = ParamsPanelView(this).apply {
            onDoneListener = { p, a, s -> power = p; angle = a; sensitivity = s; aimCircle?.circleSizeDp = mapPowerToSize(p); aimCircle?.setDirectionLine(a.toFloat(), mapPowerToLength(p), true); hideParams() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 500 }
        wm.addView(paramsPanel, lp)
    }
    private fun hideParams() { if (!paramsVisible || paramsPanel == null) return; paramsVisible = false; wm.removeView(paramsPanel); paramsPanel = null }

    private fun showModeBar() {
        if (modeVisible) return; modeVisible = true
        modeBar = LayoutInflater.from(this).inflate(R.layout.mode_selector, null)
        modeBar?.findViewById<TextView>(R.id.btnStraight)?.setOnClickListener { selectMode(LineType.STRAIGHT) }
        modeBar?.findViewById<TextView>(R.id.btnBank)?.setOnClickListener { selectMode(LineType.BANK) }
        modeBar?.findViewById<TextView>(R.id.btnMultiRail)?.setOnClickListener { selectMode(LineType.MULTI_RAIL) }
        modeBar?.findViewById<TextView>(R.id.btnRailCut)?.setOnClickListener { selectMode(LineType.CUSHION_SHOT) }
        modeBar?.findViewById<TextView>(R.id.btnPass)?.setOnClickListener { selectMode(LineType.PASS) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 200 }
        wm.addView(modeBar, lp)
    }
    private fun hideModeBar() { if (!modeVisible || modeBar == null) return; modeVisible = false; wm.removeView(modeBar); modeBar = null }

    private fun selectMode(mode: LineType) { currentMode = mode; hideModeBar(); showAimAndOverlay(); Toast.makeText(this, "模式: ${mode.name}", Toast.LENGTH_SHORT).show() }

    private fun showAimAndOverlay() {
        if (!aimVisible) {
            aimCircle = AimCircleView(this).apply { circleSizeDp = mapPowerToSize(power); setDirectionLine(angle.toFloat(), mapPowerToLength(power), true) }
            wm.addView(aimCircle, WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 300; y = 300 })
            aimVisible = true
        }
        if (!overlayVisible) {
            lineOverlay = AimLineOverlay(this)
            wm.addView(lineOverlay, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
            ))
            overlayVisible = true
        }
    }

    private fun updateOverlay() {
        if (!overlayVisible || lineOverlay == null) return
        lineOverlay?.balls = allBalls; lineOverlay?.currentMode = currentMode
        val w = displayMetrics.widthPixels; val h = displayMetrics.heightPixels
        val cue = cueBallPos?.let { PointF(it.x, it.y) } ?: return
        val best = pathCalc.findBestTarget(cue, allBalls, w, h)
        if (best != null) {
            val tp = best.position; lineOverlay?.highlightedBall = tp
            when (currentMode) {
                LineType.STRAIGHT -> lineOverlay?.aimLine = pathCalc.findStraightShot(cue, tp, w, h)
                LineType.BANK -> lineOverlay?.aimLine = pathCalc.findBankShot(tp, w, h)
                LineType.MULTI_RAIL -> lineOverlay?.aimLine = pathCalc.findMultiRailShot(cue, tp, w, h)
                LineType.CUSHION_SHOT -> lineOverlay?.aimLine = pathCalc.findCushionShot(cue, tp, w, h)
                LineType.PASS -> lineOverlay?.aimLine = pathCalc.findPassShot(cue, tp, allBalls, w, h)
            }
        } else lineOverlay?.highlightedBall = null
        lineOverlay?.invalidate()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = sqrt((x1-x2).pow(2) + (y1-y2).pow(2))
    private fun mapPowerToSize(p: Int) = 40f + (p/100f)*80f
    private fun mapPowerToLength(p: Int) = 20f + (p/100f)*60f

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
}
