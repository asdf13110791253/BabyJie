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

    // 菜单展开/收起控制
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
    private var isAiActive = false  // AI 分析是否激活（用户点击后）

    // 数据存储
    private var currentDetectionRegion: RectF? = null
    private var isTableDetected = false
    private var ballPosition: BallPosition? = null

    // 当前参数：力度、角度、灵敏度
    private var powerValue = 50
    private var angleValue = 45
    private var sensitivityValue = 5

    private val handler = Handler(Looper.getMainLooper())

    // 悬浮窗布局参数
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

        // 注册检测回调，来自 ScreenCaptureService 的每一帧分析结果
        ScreenCaptureService.detectionCallback = object : ScreenCaptureService.DetectionCallback {
            override fun onTableDetected(isTablePresent: Boolean, ballPosition: BallPosition?) {
                this@FloatWindowService.isTableDetected = isTablePresent
                this@FloatWindowService.ballPosition = ballPosition

                // 只在 AI 功能激活时处理瞄准圈
                if (isAiActive) {
                    handler.post {
                        updateAimCircleByDetection()
                    }
                }
            }
        }

        createFloatView()
    }

    // ---------- 初始化悬浮按钮和菜单 ----------
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
                    if (Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()) > 10f) {
                        isDragging = true
                    }
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

        // 菜单项 1：AI 分析
        floatRootView.findViewById<TextView>(R.id.menuItem1).setOnClickListener {
            toggleAi()
            hideMenu()
        }
        // 菜单项 2：台球参数
        floatRootView.findViewById<TextView>(R.id.menuItem2).setOnClickListener {
            if (isParamsVisible) hideParamsPanel() else showParamsPanel()
            hideMenu()
        }
        // 菜单项 3：桌布调整
        floatRootView.findViewById<TextView>(R.id.menuItem3).setOnClickListener {
            if (isClothVisible) hideTableCloth() else showTableCloth()
            hideMenu()
        }
    }

    // ---------- 菜单动画 ----------
    private fun toggleMenu() {
        if (isExpanded) hideMenu() else showMenu()
    }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    // ---------- 台球参数面板 ----------
    private fun showParamsPanel() {
        if (isParamsVisible) return
        isParamsVisible = true

        paramsPanelView = ParamsPanelView(this).apply {
            onDoneListener = { power, angle, sensitivity ->
                powerValue = power
                angleValue = angle
                sensitivityValue = sensitivity
                // 更新瞄准圈大小（如果正在显示）
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
            // 首次显示时，根据当前检测结果更新一次位置
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    /**
     * 根据当前检测结果自动调整瞄准圈的位置和可见性
     */
    private fun updateAimCircleByDetection() {
        if (!isAimVisible || aimCircleView == null) return

        if (isTableDetected) {
            // 如果检测到台球桌，瞄准圈保持可见
            if (ballPosition != null) {
                // 有白球，吸附到白球中心
                aimCircleView?.moveToScreenPosition(ballPosition!!.x, ballPosition!!.y)
            } else if (currentDetectionRegion != null) {
                // 没有白球但有识别区域，移动到区域中心
                val centerX = currentDetectionRegion!!.centerX()
                val centerY = currentDetectionRegion!!.centerY()
                aimCircleView?.moveToScreenPosition(centerX, centerY)
            }
            // 确保可见（可能之前被隐藏了）
            if (aimCircleView?.visibility != View.VISIBLE) {
                aimCircleView?.visibility = View.VISIBLE
            }
        } else {
            // 没有台球桌，自动隐藏瞄准圈（或保持不动，根据需求调整）
            // aimCircleView?.visibility = View.GONE
            // 这里选择保持显示，让用户手动拖动
        }
    }

    /**
     * 将力度值映射为瞄准圈直径（dp）
     */
    private fun mapPowerToSize(power: Int): Float {
        // 力度 0~100，映射到直径 40~120 dp
        return 40f + (power / 100f) * 80f
    }

    // ---------- 生命周期清理 ----------
    override fun onDestroy() {
        if (isClothVisible && tableClothView != null) windowManager.removeView(tableClothView)
        if (isParamsVisible && paramsPanelView != null) windowManager.removeView(paramsPanelView)
        if (isAimVisible && aimCircleView != null) windowManager.removeView(aimCircleView)
        if (::floatRootView.isInitialized) windowManager.removeView(floatRootView)
        ScreenCaptureService.detectionCallback = null
        super.onDestroy()
    }
}
