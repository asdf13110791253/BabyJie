package com.babyjie

import android.app.Service
import android.content.Intent
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

class FloatWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatRootView: View
    private lateinit var mainButton: FrameLayout
    private lateinit var menuBar: LinearLayout

    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // 桌布调整
    private var tableClothView: TableClothView? = null
    private var isClothVisible = false

    // 参数面板
    private var paramsPanelView: ParamsPanelView? = null
    private var isParamsVisible = false

    // 瞄准圈
    private var aimCircleView: AimCircleView? = null
    private var isAimVisible = false
    private var isAiActive = false

    // 数据存储
    private var currentDetectionRegion: RectF? = null
    private var isTableDetected = false
    private var ballPosition: BallPosition? = null

    // 当前参数
    private var powerValue = 50
    private var angleValue = 45
    private var sensitivityValue = 5

    private val handler = Handler(Looper.getMainLooper())

    private val params by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        ScreenCaptureService.detectionCallback = object : ScreenCaptureService.DetectionCallback {
            override fun onTableDetected(isTablePresent: Boolean, ballPosition: BallPosition?) {
                this@FloatWindowService.isTableDetected = isTablePresent
                this@FloatWindowService.ballPosition = ballPosition

                if (isAiActive) {
                    handler.post {
                        updateAimCircleByDetection()
                    }
                }
            }
        }

        createFloatView()
    }

    private fun createFloatView() {
        floatRootView = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainButton = floatRootView.findViewById(R.id.floatMainBtn)
        menuBar = floatRootView.findViewById(R.id.menuBar)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 400
        windowManager.addView(floatRootView, params)

        // 主按钮拖动
        mainButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()) > 10f) isDragging = true
                    if (isDragging) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(floatRootView, params)
                        if (isExpanded) hideMenu()
                        if (isClothVisible) hideTableCloth()
                        if (isParamsVisible) hideParamsPanel()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleMenu()
                    true
                }
                else -> false
            }
        }

        // 菜单项
        floatRootView.findViewById<TextView>(R.id.menuItem1).setOnClickListener {
            toggleAi()
            hideMenu()
        }
        floatRootView.findViewById<TextView>(R.id.menuItem2).setOnClickListener {
            if (isParamsVisible) hideParamsPanel() else showParamsPanel()
            hideMenu()
        }
        floatRootView.findViewById<TextView>(R.id.menuItem3).setOnClickListener {
            if (isClothVisible) hideTableCloth() else showTableCloth()
            hideMenu()
        }
    }

    // ---------- 菜单动画 ----------
    private fun toggleMenu() { if (isExpanded) hideMenu() else showMenu() }

    private fun showMenu() {
        if (isExpanded) return
        isExpanded = true

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels

        val menuWidth = (screenWidth * 0.55f).toInt()
        val menuParams = menuBar.layoutParams
        menuParams.width = menuWidth
        menuBar.layoutParams = menuParams

        val btnLocation = IntArray(2)
        mainButton.getLocationOnScreen(btnLocation)
        val btnCenterX = btnLocation[0] + mainButton.width / 2f
        val btnTop = btnLocation[1]

        val menuX = (btnCenterX - menuWidth / 2f).toInt().coerceIn(0, screenWidth - menuWidth)
        val menuY = (btnTop - menuBar.layoutParams.height - 20).coerceAtLeast(0)

        menuBar.x = menuX.toFloat()
        menuBar.y = menuY.toFloat()

        menuBar.visibility = View.VISIBLE
        menuBar.alpha = 0f
        menuBar.scaleX = 0.8f
        menuBar.scaleY = 0.8f
        menuBar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun hideMenu() {
        if (!isExpanded) return
        isExpanded = false
        menuBar.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .withEndAction { menuBar.visibility = View.GONE }
            .start()
    }

    // ---------- 桌布调整 ----------
    private fun showTableCloth() {
        if (isClothVisible) return
        isClothVisible = true
        tableClothView = TableClothView(this).apply {
            onDoneListener = { region ->
                currentDetectionRegion = region
                hideTableCloth()
            }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(tableClothView, layoutParams)
    }

    private fun hideTableCloth() {
        if (!isClothVisible || tableClothView == null) return
        isClothVisible = false
        windowManager.removeView(tableClothView)
        tableClothView = null
    }

    // ---------- 参数面板 ----------
    private fun showParamsPanel() {
        if (isParamsVisible) return
        isParamsVisible = true
        paramsPanelView = ParamsPanelView(this).apply {
            onDoneListener = { power, angle, sensitivity ->
                powerValue = power
                angleValue = angle
                sensitivityValue = sensitivity
                aimCircleView?.circleSizeDp = mapPowerToSize(power)
                Toast.makeText(this@FloatWindowService, "参数已保存", Toast.LENGTH_SHORT).show()
                hideParamsPanel()
            }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 80
        layoutParams.y = 500
        windowManager.addView(paramsPanelView, layoutParams)
    }

    private fun hideParamsPanel() {
        if (!isParamsVisible || paramsPanelView == null) return
        isParamsVisible = false
        windowManager.removeView(paramsPanelView)
        paramsPanelView = null
    }

    // ---------- AI 瞄准圈 ----------
    private fun toggleAi() {
        if (isAimVisible) {
            hideAimCircle()
            isAiActive = false
        } else {
            showAimCircle()
            isAiActive = true
            updateAimCircleByDetection()
        }
    }

    private fun showAimCircle() {
        if (isAimVisible) return
        isAimVisible = true
        aimCircleView = AimCircleView(this).apply {
            circleSizeDp = mapPowerToSize(powerValue)
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 200
        layoutParams.y = 300
        windowManager.addView(aimCircleView, layoutParams)
    }

    private fun hideAimCircle() {
        if (!isAimVisible || aimCircleView == null) return
        isAimVisible = false
        windowManager.removeView(aimCircleView)
        aimCircleView = null
    }

    private fun updateAimCircleByDetection() {
        if (!isAimVisible || aimCircleView == null) return

        if (isTableDetected) {
            val currentBallPos = ballPosition
            if (currentBallPos != null) {
                aimCircleView?.moveToScreenPosition(currentBallPos.x, currentBallPos.y)
            } else if (currentDetectionRegion != null) {
                val centerX = currentDetectionRegion!!.centerX()
                val centerY = currentDetectionRegion!!.centerY()
                aimCircleView?.moveToScreenPosition(centerX, centerY)
            }
            aimCircleView?.visibility = View.VISIBLE
        }
    }

    private fun mapPowerToSize(power: Int): Float {
        return 40f + (power / 100f) * 80f
    }

    override fun onDestroy() {
        if (isClothVisible && tableClothView != null) windowManager.removeView(tableClothView)
        if (isParamsVisible && paramsPanelView != null) windowManager.removeView(paramsPanelView)
        if (isAimVisible && aimCircleView != null) windowManager.removeView(aimCircleView)
        if (::floatRootView.isInitialized) windowManager.removeView(floatRootView)
        ScreenCaptureService.detectionCallback = null
        super.onDestroy()
    }
}
