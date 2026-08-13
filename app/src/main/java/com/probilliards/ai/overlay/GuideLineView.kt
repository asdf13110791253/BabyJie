// File: app/src/main/java/com/probilliards/ai/overlay/GuideLineView.kt
package com.probilliards.ai.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import com.probilliards.ai.ai.CueAction
import com.probilliards.ai.ai.ShotRecommendation
import kotlin.math.*

/**
 * 辅助线视图（优化版）
 * 支持发光效果、圆弧力度条、动画箭头
 */
class GuideLineView(context: Context) : View(context) {
    
    companion object {
        // 默认颜色
        private const val DEFAULT_MAIN_LINE_COLOR = Color.rgb(255, 87, 34)
        private const val DEFAULT_TARGET_LINE_COLOR = Color.rgb(76, 175, 80)
        private const val DEFAULT_REBOUND_LINE_COLOR = Color.rgb(63, 81, 181)
        private const val DEFAULT_AI_LINE_COLOR = Color.rgb(255, 193, 7)
        
        // 默认参数
        private const val DEFAULT_LINE_WIDTH = 3f
        private const val DEFAULT_LINE_ALPHA = 200
    }
    
    // 功能开关
    private val featuresEnabled = mutableMapOf(
        "main_line" to false,
        "target_line" to false,
        "rebound_line" to false,
        "power_bar" to false,
        "ai_recommend" to false
    )
    
    // 自定义设置
    private var mainLineColor = DEFAULT_MAIN_LINE_COLOR
    private var targetLineColor = DEFAULT_TARGET_LINE_COLOR
    private var reboundLineColor = DEFAULT_REBOUND_LINE_COLOR
    private var aiLineColor = DEFAULT_AI_LINE_COLOR
    private var lineWidth = DEFAULT_LINE_WIDTH
    private var lineAlpha = DEFAULT_LINE_ALPHA
    
    // 球的位置
    private var whiteBallPosition: PointF? = null
    private var targetBallPosition: PointF? = null
    private var pocketPosition: PointF? = null
    private var aiRecommendation: ShotRecommendation? = null
    
    // 动画
    private var arrowAnimator: ValueAnimator? = null
    private var arrowProgress = 0f
    private var powerBarAnimator: ValueAnimator? = null
    private var powerBarProgress = 0f
    
    // 画笔
    private val mainLinePaint = Paint().apply {
        color = DEFAULT_MAIN_LINE_COLOR
        strokeWidth = DEFAULT_LINE_WIDTH
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = DEFAULT_LINE_ALPHA
    }
    
    private val mainLineGlowPaint = Paint().apply {
        color = DEFAULT_MAIN_LINE_COLOR
        strokeWidth = DEFAULT_LINE_WIDTH * 3
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 50
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val targetLinePaint = Paint().apply {
        color = DEFAULT_TARGET_LINE_COLOR
        strokeWidth = DEFAULT_LINE_WIDTH
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = DEFAULT_LINE_ALPHA
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val reboundLinePaint = Paint().apply {
        color = DEFAULT_REBOUND_LINE_COLOR
        strokeWidth = DEFAULT_LINE_WIDTH
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = DEFAULT_LINE_ALPHA
        pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f)
    }
    
    private val aiLinePaint = Paint().apply {
        color = DEFAULT_AI_LINE_COLOR
        strokeWidth = DEFAULT_LINE_WIDTH + 1
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 255
    }
    
    private val aiLineGlowPaint = Paint().apply {
        color = DEFAULT_AI_LINE_COLOR
        strokeWidth = (DEFAULT_LINE_WIDTH + 1) * 3
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 80
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val arrowPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
        alpha = 200
    }
    
    private val powerBarBackgroundPaint = Paint().apply {
        color = Color.argb(100, 128, 128, 128)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 15f
    }
    
    private val powerBarPaint = Paint().apply {
        color = DEFAULT_MAIN_LINE_COLOR
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    private val infoTextPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        loadCustomSettings()
        startArrowAnimation()
    }
    
    /**
     * 加载自定义设置
     */
    private fun loadCustomSettings() {
        val prefs = context.getSharedPreferences("probilliards_settings", Context.MODE_PRIVATE)
        mainLineColor = prefs.getInt("main_line_color", DEFAULT_MAIN_LINE_COLOR)
        targetLineColor = prefs.getInt("target_line_color", DEFAULT_TARGET_LINE_COLOR)
        reboundLineColor = prefs.getInt("rebound_line_color", DEFAULT_REBOUND_LINE_COLOR)
        aiLineColor = prefs.getInt("ai_line_color", DEFAULT_AI_LINE_COLOR)
        lineWidth = prefs.getFloat("line_width", DEFAULT_LINE_WIDTH)
        lineAlpha = prefs.getInt("line_alpha", DEFAULT_LINE_ALPHA)
        
        updatePaintColors()
    }
    
    /**
     * 更新画笔颜色
     */
    private fun updatePaintColors() {
        mainLinePaint.color = mainLineColor
        mainLinePaint.strokeWidth = lineWidth
        mainLinePaint.alpha = lineAlpha
        
        mainLineGlowPaint.color = mainLineColor
        mainLineGlowPaint.strokeWidth = lineWidth * 3
        mainLineGlowPaint.alpha = lineAlpha / 4
        
        targetLinePaint.color = targetLineColor
        targetLinePaint.strokeWidth = lineWidth
        targetLinePaint.alpha = lineAlpha
        
        reboundLinePaint.color = reboundLineColor
        reboundLinePaint.strokeWidth = lineWidth
        reboundLinePaint.alpha = lineAlpha
        
        aiLinePaint.color = aiLineColor
        aiLinePaint.strokeWidth = lineWidth + 1
        
        aiLineGlowPaint.color = aiLineColor
        aiLineGlowPaint.strokeWidth = (lineWidth + 1) * 3
        
        powerBarPaint.color = mainLineColor
    }
    
    /**
     * 更新自定义设置
     */
    fun updateCustomSettings(
        mainColor: Int? = null,
        targetColor: Int? = null,
        reboundColor: Int? = null,
        aiColor: Int? = null,
        width: Float? = null,
        alpha: Int? = null
    ) {
        mainColor?.let { mainLineColor = it }
        targetColor?.let { targetLineColor = it }
        reboundColor?.let { reboundLineColor = it }
        aiColor?.let { aiLineColor = it }
        width?.let { lineWidth = it }
        alpha?.let { lineAlpha = it }
        
        updatePaintColors()
        invalidate()
    }
    
    /**
     * 启动箭头动画
     */
    private fun startArrowAnimation() {
        arrowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                arrowProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        powerBarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                powerBarProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun setFeatureEnabled(feature: String, enabled: Boolean) {
        featuresEnabled[feature] = enabled
        invalidate()
    }
    
    fun updateFrame(bitmap: Bitmap) {
        invalidate()
    }
    
    fun updateBallPositions(white: PointF?, target: PointF?, pocket: PointF?) {
        whiteBallPosition = white
        targetBallPosition = target
        pocketPosition = pocket
        invalidate()
    }
    
    fun updateAIRecommendation(recommendation: ShotRecommendation?) {
        aiRecommendation = recommendation
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制主瞄准线（带发光效果）
        if (featuresEnabled["main_line"] == true && 
            whiteBallPosition != null && targetBallPosition != null) {
            drawMainLine(canvas)
        }
        
        // 绘制目标球延伸线
        if (featuresEnabled["target_line"] == true && 
            targetBallPosition != null && pocketPosition != null) {
            drawTargetLine(canvas)
        }
        
        // 绘制反弹线
        if (featuresEnabled["rebound_line"] == true) {
            drawReboundLine(canvas)
        }
        
        // 绘制AI推荐路线（带发光效果）
        if (featuresEnabled["ai_recommend"] == true && aiRecommendation != null) {
            drawAIRecommendPath(canvas)
        }
        
        // 绘制力度条（圆弧形）
        if (featuresEnabled["power_bar"] == true && whiteBallPosition != null) {
            drawArcPowerBar(canvas)
        }
        
        // 绘制信息面板
        if (aiRecommendation != null && featuresEnabled["ai_recommend"] == true) {
            drawInfoPanel(canvas)
        }
    }
    
    /**
     * 绘制主瞄准线（带发光效果）
     */
    private fun drawMainLine(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val target = targetBallPosition ?: return
        
        val dx = target.x - white.x
        val dy = target.y - white.y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 0) {
            val unitX = dx / distance
            val unitY = dy / distance
            
            val startX = white.x
            val startY = white.y
            val endX = target.x + unitX * 200
            val endY = target.y + unitY * 200
            
            // 绘制发光效果
            canvas.drawLine(startX, startY, endX, endY, mainLineGlowPaint)
            
            // 绘制主线
            canvas.drawLine(startX, startY, endX, endY, mainLinePaint)
            
            // 绘制动画箭头
            drawAnimatedArrow(canvas, white, target, unitX, unitY)
        }
    }
    
    /**
     * 绘制动画箭头
     */
    private fun drawAnimatedArrow(canvas: Canvas, start: PointF, end: PointF, unitX: Float, unitY: Float) {
        val distance = GeometryUtils.distance(start, end)
        val arrowPosition = distance * arrowProgress
        val arrowX = start.x + unitX * arrowPosition
        val arrowY = start.y + unitY * arrowPosition
        
        // 箭头大小
        val arrowSize = 15f
        
        // 计算箭头三角形的三个顶点
        val angle = atan2(unitY, unitX)
        val angle1 = angle + PI.toFloat() * 0.75f
        val angle2 = angle - PI.toFloat() * 0.75f
        
        val tipX = arrowX + cos(angle) * arrowSize
        val tipY = arrowY + sin(angle) * arrowSize
        val base1X = arrowX + cos(angle1) * arrowSize
        val base1Y = arrowY + sin(angle1) * arrowSize
        val base2X = arrowX + cos(angle2) * arrowSize
        val base2Y = arrowY + sin(angle2) * arrowSize
        
        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(base1X, base1Y)
            lineTo(base2X, base2Y)
            close()
        }
        
        canvas.drawPath(path, arrowPaint)
    }
    
    /**
     * 绘制目标球延伸线
     */
    private fun drawTargetLine(canvas: Canvas) {
        val target = targetBallPosition ?: return
        val pocket = pocketPosition ?: return
        
        val dx = pocket.x - target.x
        val dy = pocket.y - target.y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 0) {
            val unitX = dx / distance
            val unitY = dy / distance
            
            val endX = pocket.x + unitX * 150
            val endY = pocket.y + unitY * 150
            
            canvas.drawLine(target.x, target.y, endX, endY, targetLinePaint)
        }
    }
    
    /**
     * 绘制反弹线
     */
    private fun drawReboundLine(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val target = targetBallPosition ?: return
        
        val screenWidth = width.toFloat()
        
        val dx = target.x - white.x
        val dy = target.y - white.y
        
        val reboundX = screenWidth - 50
        val reboundY = white.y + dy * (reboundX - white.x) / dx
        
        canvas.drawLine(white.x, white.y, reboundX, reboundY, reboundLinePaint)
        canvas.drawLine(reboundX, reboundY, target.x, target.y, reboundLinePaint)
        
        // 绘制反弹点
        val reboundPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(reboundX, reboundY, 8f, reboundPaint)
    }
    
    /**
     * 绘制AI推荐路线（带发光效果）
     */
    private fun drawAIRecommendPath(canvas: Canvas) {
        val recommendation = aiRecommendation ?: return
        val path = recommendation.path
        
        if (path.size < 2) return
        
        val pathObj = Path()
        pathObj.moveTo(path[0].x, path[0].y)
        
        for (i in 1 until path.size) {
            pathObj.lineTo(path[i].x, path[i].y)
        }
        
        // 绘制发光效果
        canvas.drawPath(pathObj, aiLineGlowPaint)
        
        // 绘制主路线
        canvas.drawPath(pathObj, aiLinePaint)
        
        // 绘制终点标记
        path.lastOrNull()?.let { endPoint ->
            canvas.drawCircle(endPoint.x, endPoint.y, 15f, aiLinePaint)
            canvas.drawCircle(endPoint.x, endPoint.y, 8f, arrowPaint)
        }
    }
    
    /**
     * 绘制圆弧力度条
     */
    private fun drawArcPowerBar(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val recommendation = aiRecommendation
        
        val centerX = white.x
        val centerY = white.y - 100 // 在白球上方
        val radius = 50f
        
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        // 绘制背景弧
        canvas.drawArc(rect, 135f, 270f, false, powerBarBackgroundPaint)
        
        // 绘制力度弧
        val powerPercentage = recommendation?.power ?: 0.5f
        val sweepAngle = 270f * powerPercentage
        
        canvas.drawArc(rect, 135f, sweepAngle, false, powerBarPaint)
        
        // 绘制力度百分比文字
        val powerText = "${(powerPercentage * 100).toInt()}%"
        canvas.drawText(powerText, centerX, centerY + 10, textPaint)
    }
    
    /**
     * 绘制信息面板
     */
    private fun drawInfoPanel(canvas: Canvas) {
        val recommendation = aiRecommendation ?: return
        
        val panelX = 20f
        val panelY = 20f
        val panelWidth = 250f
        val panelHeight = 120f
        
        // 绘制背景
        val bgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRoundRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 
            15f, 15f, bgPaint)
        
        // 绘制信息
        val probabilityText = "进球概率: ${(recommendation.probability * 100).toInt()}%"
        val powerText = "建议力度: ${(recommendation.power * 100).toInt()}%"
        val cueActionText = "建议杆法: ${getCueActionText(recommendation.cueAction)}"
        val shotTypeText = "模式: ${getShotTypeText(recommendation.shotType)}"
        
        canvas.drawText(probabilityText, panelX + 20, panelY + 30, infoTextPaint)
        canvas.drawText(powerText, panelX + 20, panelY + 60, infoTextPaint)
        canvas.drawText(cueActionText, panelX + 20, panelY + 90, infoTextPaint)
        canvas.drawText(shotTypeText, panelX + 20, panelY + 115, infoTextPaint)
    }
    
    private fun getCueActionText(action: CueAction): String {
        return when (action) {
            CueAction.CENTER -> "中杆"
            CueAction.TOP -> "高杆"
            CueAction.BOTTOM -> "低杆"
            CueAction.LEFT -> "左塞"
            CueAction.RIGHT -> "右塞"
            CueAction.TOP_LEFT -> "高杆左塞"
            CueAction.TOP_RIGHT -> "高杆右塞"
            CueAction.BOTTOM_LEFT -> "低杆左塞"
            CueAction.BOTTOM_RIGHT -> "低杆右塞"
        }
    }
    
    private fun getShotTypeText(type: com.probilliards.ai.ai.ShotType): String {
        return when (type) {
            com.probilliards.ai.ai.ShotType.OFFENSIVE -> "进攻"
            com.probilliards.ai.ai.ShotType.DEFENSIVE -> "防守"
            com.probilliards.ai.ai.ShotType.SAFETY -> "安全球"
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        arrowAnimator?.cancel()
        powerBarAnimator?.cancel()
    }
}

// 引用工具类
object GeometryUtils {
    fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
    }
}
