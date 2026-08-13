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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.app.NotificationCompat
import com.probilliards.ai.R
import com.probilliards.ai.SettingsActivity
import com.probilliards.ai.ai.AIMode
import com.probilliards.ai.overlay.ControlPanelView
import com.probilliards.ai.overlay.FloatingBallView
import com.probilliards.ai.overlay.GuideLineView
import com.probilliards.ai.overlay.SelectionRectView
import com.probilliards.ai.vision.*
import kotlinx.coroutines.*
import org.opencv.core.MatOfPoint2f

/**
 * 悬浮窗服务（优化版）
 * 集成卡尔曼滤波、霍夫变换、ROI处理
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
    
    // 视觉检测器
    private lateinit var tableDetector: TableDetector
    private lateinit var ballDetector: BallDetector
    private lateinit var pocketDetector: PocketDetector
    private lateinit var houghLineDetector: HoughLineDetector
    private lateinit var tableCalibrator: TableCalibrator
    
    // 卡尔曼滤波器追踪器
    private val ballTrackers = mutableMapOf<Int, KalmanFilterTracker>()
    
    // ROI区域
    private var roiRect: android.graphics.Rect? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 初始化检测器
        initDetectors()
        
        createNotificationChannel()
        initOverlayViews()
        startFrameProcessing()
    }
    
    /**
     * 初始化检测器
     */
    private fun initDetectors() {
        tableDetector = TableDetector()
        ballDetector = BallDetector()
        pocketDetector = PocketDetector()
        houghLineDetector = HoughLineDetector()
        tableCalibrator = TableCalibrator(this)
        
        // 加载ROI设置
        loadROISettings()
    }
    
    /**
     * 加载ROI设置
     */
    private fun loadROISettings() {
        val prefs = getSharedPreferences("probilliards_prefs", Context.MODE_PRIVATE)
        val left = prefs.getInt("selection_left", -1)
        val top = prefs.getInt("selection_top", -1)
        val right = prefs.getInt("selection_right", -1)
        val bottom = prefs.getInt("selection_bottom", -1)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            roiRect = android.graphics.Rect(left, top, right, bottom)
        }
    }
    
    /**
     * 初始化所有悬浮窗视图
     */
    private fun initOverlayViews() {
        // 初始化悬浮球（支持长按拖动）
        floatingBallView = FloatingBallView(this).apply {
            onBallClick = { toggleControlPanel() }
            onBallDrag = { x, y -> updateFloatingBallPosition(x, y) }
            onBallLongPress = { 
                // 长按进入快速拖动模式
                Toast.makeText(this@FloatingWindowService, "快速拖动模式", Toast.LENGTH_SHORT).show()
            }
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
            onOpenSettingsClick = { openSettings() }
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
                roiRect = rect
                // 更新屏幕采集服务的ROI
                ScreenCaptureService.instance?.updateROI(rect)
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
     * 切换控制面板（带动画）
     */
    private fun toggleControlPanel() {
        if (controlPanelView.visibility == View.VISIBLE) {
            // 缩小动画
            val scaleDown = ScaleAnimation(
                1f, 0.8f, 1f, 0.8f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0f
            ).apply {
                duration = 150
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        controlPanelView.visibility = View.GONE
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }
            controlPanelView.startAnimation(scaleDown)
        } else {
            controlPanelView.visibility = View.VISIBLE
            controlPanelParams.x = floatingBallParams.x
            controlPanelParams.y = floatingBallParams.y + 100
            windowManager.updateViewLayout(controlPanelView, controlPanelParams)
            
            // 放大动画
            val scaleUp = ScaleAnimation(
                0.8f, 1f, 0.8f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0f
            ).apply {
                duration = 150
            }
            controlPanelView.startAnimation(scaleUp)
            
            // 淡入动画
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 200
            }
            controlPanelView.startAnimation(fadeIn)
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
     * 打开设置页
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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
     * 处理帧（集成视觉检测）
     */
    private suspend fun processFrame(bitmap: android.graphics.Bitmap) {
        withContext(Dispatchers.Default) {
            try {
                // 应用校准
                val calibration = tableCalibrator.getCalibration()
                val calibratedBitmap = tableCalibrator.applyCalibration(bitmap, calibration)
                
                // 使用ROI处理
                val openCvROI = roiRect?.let {
                    org.opencv.core.Rect(it.left, it.top, it.width(), it.height())
                }
                
                // 检测球桌
                val tableCorners = tableDetector.detectTable(calibratedBitmap, openCvROI)
                
                // 使用霍夫变换辅助检测
                if (tableCorners == null) {
                    val lines = houghLineDetector.detectLines(calibratedBitmap, openCvROI)
                    val boundaries = houghLineDetector.findTableBoundaries(lines)
                    // 从边界线计算球桌顶点
                }
                
                // 检测球
                val detectedBalls = ballDetector.detectBalls(calibratedBitmap)
                
                // 应用卡尔曼滤波平滑
                val smoothedBalls = smoothBallPositions(detectedBalls)
                
                // 检测袋口
                val pockets = if (tableCorners != null) {
                    pocketDetector.detectPockets(tableCorners).map { it.position }
                } else {
                    pocketDetector.estimatePockets(
                        calibratedBitmap.width,
                        calibratedBitmap.height,
                        roiRect
                    ).map { it.position }
                }
                
                // 更新辅助线
                withContext(Dispatchers.Main) {
                    updateGuideLines(smoothedBalls, pockets)
                }
            } catch (e: Exception) {
                // 处理异常
            }
        }
    }
    
    /**
     * 使用卡尔曼滤波平滑球的位置
     */
    private fun smoothBallPositions(balls: List<Ball>): List<Ball> {
        return balls.map { ball ->
            val tracker = ballTrackers.getOrPut(ball.hashCode()) {
                KalmanFilterTracker()
            }
            val smoothedPosition = tracker.update(ball.position)
            ball.copy(position = smoothedPosition)
        }
    }
    
    /**
     * 更新辅助线
     */
    private fun updateGuideLines(balls: List<Ball>, pockets: List<android.graphics.PointF>) {
        val whiteBall = balls.find { it.isWhite }
        
        if (whiteBall != null) {
            guideLineView.updateBallPositions(
                white = whiteBall.position,
                target = balls.firstOrNull { !it.isWhite }?.position,
                pocket = pockets.firstOrNull()
            )
            
            // 计算AI推荐
            val aiMode = getAIMode()
            val shotRecommender = com.probilliards.ai.ai.ShotRecommender()
            val recommendation = shotRecommender.recommendShot(
                whiteBall,
                balls.filter { !it.isWhite },
                pockets,
                aiMode
            )
            
            guideLineView.updateAIRecommendation(recommendation)
        }
    }
    
    /**
     * 获取AI模式
     */
    private fun getAIMode(): AIMode {
        val prefs = getSharedPreferences("probilliards_settings", Context.MODE_PRIVATE)
        return when (prefs.getInt("ai_mode", 0)) {
            0 -> AIMode.BALANCED
            1 -> AIMode.OFFENSIVE
            2 -> AIMode.DEFENSIVE
            else -> AIMode.BALANCED
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
