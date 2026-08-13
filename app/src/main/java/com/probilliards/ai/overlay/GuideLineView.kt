// File: app/src/main/java/com/probilliards/ai/overlay/GuideLineView.kt
package com.probilliards.ai.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.probilliards.ai.R
import com.probilliards.ai.ai.CueAction
import com.probilliards.ai.ai.ShotRecommendation
import kotlin.math.*

/**
 * 辅助线视图（暗夜玫瑰主题版）
 */
class GuideLineView(context: Context) : View(context) {
    
    // 功能开关
    private val featuresEnabled = mutableMapOf(
        "main_line" to false,
        "target_line" to false,
        "rebound_line" to false,
        "power_bar" to false,
        "ai_recommend" to false
    )
    
    // 球的位置
    private var whiteBallPosition: PointF? = null
    private var targetBallPosition: PointF? = null
    private var pocketPosition: PointF? = null
    private var aiRecommendation: ShotRecommendation? = null
    
    // 动画
    private var arrowAnimator: ValueAnimator? = null
    private var arrowProgress = 0f
    
    // 画笔 - 使用暗夜玫瑰主题颜色
    private val mainLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_main_line)
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 220
    }
    
    private val mainLineGlowPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_main_line)
        strokeWidth = 9f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 60
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val targetLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_target_line)
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 180
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val reboundLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_rebound_line)
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 180
        pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f)
    }
    
    // AI推荐线 - 金色
    private val aiLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_ai_line)
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 255
    }
    
    private val aiLineGlowPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_ai_glow)
        strokeWidth = 12f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 100
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val arrowPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_arrow)
        isAntiAlias = true
        style = Paint.Style.FILL
        alpha = 220
    }
    
    private val powerBarBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.dark_background_tertiary)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 15f
    }
    
    private val powerBarPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_power_bar)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    private val infoTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textAlign = Paint.Align.LEFT
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startArrowAnimation()
    }
    
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
        
        if (featuresEnabled["main_line"] == true && 
            whiteBallPosition != null && targetBallPosition != null) {
            drawMainLine(canvas)
        }
        
        if (featuresEnabled["target_line"] == true && 
            targetBallPosition != null && pocketPosition != null) {
            drawTargetLine(canvas)
        }
        
        if (featuresEnabled["rebound_line"] == true) {
            drawReboundLine(canvas)
        }
        
        if (featuresEnabled["ai_recommend"] == true && aiRecommendation != null) {
            drawAIRecommendPath(canvas)
        }
        
        if (featuresEnabled["power_bar"] == true && whiteBallPosition != null) {
            drawArcPowerBar(canvas)
        }
        
        if (aiRecommendation != null && featuresEnabled["ai_recommend"] == true) {
            drawInfoPanel(canvas)
        }
    }
    
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
            
            // 发光效果
            canvas.drawLine(startX, startY, endX, endY, mainLineGlowPaint)
            
            // 主线
            canvas.drawLine(startX, startY, endX, endY, mainLinePaint)
            
            // 动画箭头
            drawAnimatedArrow(canvas, white, target, unitX, unitY)
        }
    }
    
    private fun drawAnimatedArrow(canvas: Canvas, start: PointF, end: PointF, unitX: Float, unitY: Float) {
        val distance = distance(start, end)
        val arrowPosition = distance * arrowProgress
        val arrowX = start.x + unitX * arrowPosition
        val arrowY = start.y + unitY * arrowPosition
        
        val arrowSize = 15f
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
        
        val reboundPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.rose_pink_light)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(reboundX, reboundY, 8f, reboundPaint)
    }
    
    private fun drawAIRecommendPath(canvas: Canvas) {
        val recommendation = aiRecommendation ?: return
        val path = recommendation.path
        
        if (path.size < 2) return
        
        val pathObj = Path()
        pathObj.moveTo(path[0].x, path[0].y)
        
        for (i in 1 until path.size) {
            pathObj.lineTo(path[i].x, path[i].y)
        }
        
        // 金色发光效果
        canvas.drawPath(pathObj, aiLineGlowPaint)
        
        // 金色主线
        canvas.drawPath(pathObj, aiLinePaint)
        
        // 终点标记
        path.lastOrNull()?.let { endPoint ->
            canvas.drawCircle(endPoint.x, endPoint.y, 15f, aiLinePaint)
            canvas.drawCircle(endPoint.x, endPoint.y, 8f, arrowPaint)
        }
    }
    
    private fun drawArcPowerBar(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val recommendation = aiRecommendation
        
        val centerX = white.x
        val centerY = white.y - 100
        val radius = 50f
        
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        canvas.drawArc(rect, 135f, 270f, false, powerBarBackgroundPaint)
        
        val powerPercentage = recommendation?.power ?: 0.5f
        val sweepAngle = 270f * powerPercentage
        
        canvas.drawArc(rect, 135f, sweepAngle, false, powerBarPaint)
        
        val powerText = "${(powerPercentage * 100).toInt()}%"
        canvas.drawText(powerText, centerX, centerY + 10, textPaint)
    }
    
    private fun drawInfoPanel(canvas: Canvas) {
        val recommendation = aiRecommendation ?: return
        
        val panelX = 20f
        val panelY = 20f
        val panelWidth = 250f
        val panelHeight = 120f
        
        val bgPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.dark_background_overlay)
            isAntiAlias = true
        }
        canvas.drawRoundRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 
            15f, 15f, bgPaint)
        
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
    
    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        arrowAnimator?.cancel()
    }
}
