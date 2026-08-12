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
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

class FloatWindowService : Service() {

    companion object {
        const val CHANNEL_ID = "float_window_channel"
        const val NOTIFICATION_ID = 1001
        private const val TOUCH_SLOP = 10f        // 拖动阈值
        private const val CLICK_INTERVAL = 500L   // 点击防抖间隔
    }

    // ========== 基础组件 ==========
    private lateinit var wm: WindowManager
    private lateinit var floatRoot: View
    private lateinit var mainBtn: FrameLayout
    private lateinit var menuBar: LinearLayout

    // ========== 交互状态 ==========
    private var menuExpanded = false
    private var animating = false              // 动画锁
    private var initX = 0
    private var initY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var hasMoved = false
    private var lastClickTime = 0L            // 防抖

    private val displayMetrics = DisplayMetrics()

    // ========== 高级功能组件 ==========
    private var clothView: TableClothView? = null
    private var clothVisible = false
    private var paramsPanel: ParamsPanelView? = null
    private var paramsVisible = false
    private var aimCircle: AimCircleView? = null
    private var aimVisible = false
    private var modeBar: View? = null
    private var modeVisible = false
    private var currentMode = LineType.STRAIGHT
    private var lineOverlay: AimLineOverlay? = null
    private var overlayVisible = false
    private val pathCalc = PathCalculator()
    private var region: RectF? = null
    private var tableDetected = false
    private var cueBallPos: BallPosition? = null
    private var allBalls = mutableListOf<BallPosition>()
    private var power = 50
    private var angle = 45
    private var sensitivity = 5
    private val handler = Handler(Looper.getMainLooper())

    /** ✅ 悬浮窗参数（关键：使用 NOT_TOUCH_MODAL） */
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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_REDELIVER_INTENT

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }

        startForegroundService()
        wm.defaultDisplay.getMetrics(displayMetrics)

        // 注册屏幕旋转监听
        registerReceiver(configurationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))

        // 接收录屏检测结果
        ScreenCaptureService.detectionCallback = object : ScreenCaptureService.DetectionCallback {
            override fun onTableDetected(table: Boolean, ball: BallPosition?) {
                tableDetected = table
                if (ball != null) cueBallPos = ball
                allBalls = if (ball != null) mutableListOf(ball) else mutableListOf()
                handler.post { updateOverlay() }
            }
        }

        try {
            createFloatView()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "悬浮窗创建失败", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "台球辅助", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("台球辅助运行中")
            .setContentText("悬浮窗已开启")
            .setSmallIcon(R.drawable.ic_launcher_temp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createFloatView() {
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_window_layout, null)
        mainBtn = floatRoot.findViewById(R.id.floatMainBtn)
        menuBar = floatRoot.findViewById(R.id.menuBar)

        menuBar.isClickable = true
        menuBar.isFocusable = true

        safeAddView(floatRoot, floatParams)

        // 点击外部关闭菜单
        floatRoot.setOnClickListener { if (menuExpanded) hideMenu() }
        menuBar.setOnClickListener { } // 拦截内部点击

        mainBtn.setOnTouchListener { _, event -> handleTouchEvent(event) }
        initMenuItems()
    }

    /** ✅ 触摸事件终极版（防误触 + 防抖） */
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initX = floatParams.x; initY = floatParams.y
                downRawX = event.rawX; downRawY = event.rawY
                hasMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!hasMoved && hypot(dx, dy) > TOUCH_SLOP) {
                    hasMoved = true
                    if (menuExpanded) hideMenu()
                    if (clothVisible) hideCloth()
                    if (paramsVisible) hideParams()
                }
                if (hasMoved) {
                    floatParams.x = initX + dx.toInt()
                    floatParams.y = initY + dy.toInt()
                    safeUpdateView(floatRoot, floatParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                mainBtn.performClick()
                if (!hasMoved && !isFastClick()) {
                    toggleMenu()
                }
                return true
            }
        }
        return false
    }

    private fun isFastClick(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_INTERVAL) return true
        lastClickTime = now
        return false
    }

    /** ✅ 菜单项绑定：调用真实功能 */
    private fun initMenuItems() {
        floatRoot.findViewById<TextView>(R.id.menuItem1)?.setOnClickListener {
            if (modeVisible) hideModeBar() else showModeBar()
            hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem2)?.setOnClickListener {
            if (paramsVisible) hideParams() else showParams()
            hideMenu()
        }
        floatRoot.findViewById<TextView>(R.id.menuItem3)?.setOnClickListener {
            if (clothVisible) hideCloth() else showCloth()
            hideMenu()
        }
    }

    private fun toggleMenu() { if (menuExpanded) hideMenu() else showMenu() }

    /** ✅ 优化后菜单显示（固定高度 + 动画锁） */
    private fun showMenu() {
        if (menuExpanded || animating) return
        animating = true

        val loc = IntArray(2)
        mainBtn.getLocationOnScreen(loc)

        // 固定菜单高度（28dp），避免测量开销
        val menuH = (28 * resources.displayMetrics.density).toInt()
        val menuW = (displayMetrics.widthPixels * 0.55f).toInt()
        val cx = loc[0] + mainBtn.width / 2f
        val x = (cx - menuW / 2f).toInt().coerceIn(0, displayMetrics.widthPixels - menuW)
        val y = (loc[1] - menuH - 30).coerceAtLeast(0)

        menuBar.x = x.toFloat()
        menuBar.y = y.toFloat()
        menuBar.alpha = 0f
        menuBar.scaleX = 0.85f
        menuBar.scaleY = 0.85f
        menuBar.visibility = View.VISIBLE

        menuBar.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(180)
            .withEndAction {
                menuExpanded = true
                animating = false
            }
            .start()
    }

    private fun hideMenu() {
        if (!menuExpanded || animating) return
        animating = true

        menuBar.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(150)
            .withEndAction {
                menuBar.visibility = View.GONE
                menuExpanded = false
                animating = false
            }
            .start()
    }

    // ==================== 桌布调整 ====================
    private fun showCloth() {
        if (clothVisible) return; clothVisible = true
        clothView = TableClothView(this).apply { onDoneListener = { r -> region = r; hideCloth() } }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(clothView, lp)
    }

    private fun hideCloth() {
        if (!clothVisible || clothView == null) return; clothVisible = false
        safeRemoveView(clothView!!)
        clothView = null
    }

    // ==================== 参数面板 ====================
    private fun showParams() {
        if (paramsVisible) return; paramsVisible = true
        paramsPanel = ParamsPanelView(this).apply {
            onDoneListener = { p, a, s ->
                power = p; angle = a; sensitivity = s
                aimCircle?.circleSizeDp = mapPowerToSize(p)
                aimCircle?.setDirectionLine(a.toFloat(), mapPowerToLength(p), true)
                hideParams()
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 500 }
        wm.addView(paramsPanel, lp)
    }

    private fun hideParams() {
        if (!paramsVisible || paramsPanel == null) return; paramsVisible = false
        safeRemoveView(paramsPanel!!)
        paramsPanel = null
    }

    // ==================== 模式选择 ====================
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

    private fun hideModeBar() {
        if (!modeVisible || modeBar == null) return; modeVisible = false
        safeRemoveView(modeBar!!)
        modeBar = null
    }

    private fun selectMode(mode: LineType) { currentMode = mode; hideModeBar(); showAimAndOverlay(); Toast.makeText(this, "模式: ${mode.name}", Toast.LENGTH_SHORT).show() }

    // ==================== 瞄准圈 + 路线 ====================
    private fun showAimAndOverlay() {
        if (!aimVisible) {
            aimCircle = AimCircleView(this).apply {
                circleSizeDp = mapPowerToSize(power)
                setDirectionLine(angle.toFloat(), mapPowerToLength(power), true)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 300; y = 300 }
            wm.addView(aimCircle, lp)
            aimVisible = true
        }
        if (!overlayVisible) {
            lineOverlay = AimLineOverlay(this)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
            )
            wm.addView(lineOverlay, lp)
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

    private fun mapPowerToSize(p: Int) = 40f + (p / 100f) * 80f
    private fun mapPowerToLength(p: Int) = 20f + (p / 100f) * 60f

    // ==================== 安全操作 ====================
    private fun safeAddView(view: View, params: WindowManager.LayoutParams) { if (::wm.isInitialized) try { wm.addView(view, params) } catch (e: Exception) { e.printStackTrace() } }
    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) { if (::wm.isInitialized && view.isAttachedToWindow) try { wm.updateViewLayout(view, params) } catch (e: Exception) { e.printStackTrace() } }
    private fun safeRemoveView(view: View) { if (::wm.isInitialized && view.isAttachedToWindow) try { wm.removeView(view) } catch (e: Exception) { e.printStackTrace() } }

    /** ✅ 屏幕旋转适配 */
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            wm.defaultDisplay.getMetrics(displayMetrics)
            if (menuExpanded) hideMenu()
        }
    }

    /** ✅ 防杀进程 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        startService(Intent(this, FloatWindowService::class.java))
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(configurationReceiver) } catch (e: Exception) { e.printStackTrace() }
        if (clothVisible && clothView != null) safeRemoveView(clothView!!)
        if (paramsVisible && paramsPanel != null) safeRemoveView(paramsPanel!!)
        if (aimVisible && aimCircle != null) safeRemoveView(aimCircle!!)
        if (overlayVisible && lineOverlay != null) safeRemoveView(lineOverlay!!)
        if (modeVisible && modeBar != null) safeRemoveView(modeBar!!)
        if (::floatRoot.isInitialized) safeRemoveView(floatRoot)
        ScreenCaptureService.detectionCallback = null
        stopForeground(true)
    }
}
