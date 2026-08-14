package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.content.ContextCompat
import com.probilliards.ai.R
import com.probilliards.ai.ai.CueAction
import com.probilliards.ai.ai.ShotRecommendation
import com.probilliards.ai.ai.ShotType
import kotlin.math.*

class GuideLineView(context: Context) : View(context) {
    
    private val featuresEnabled = mutableMapOf(
        "main_line" to false,
        "target_line" to false,
        "rebound_line" to false,
        "power_bar" to false,
        "ai_recommend" to false
    )
    
    private var whiteBallPosition: PointF? = null
    private var targetBallPosition: PointF? = null
    private var pocketPosition: PointF? = null
    private var aiRecommendation: ShotRecommendation? = null
    
    private val mainLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_main_line)
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 220
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
    
    private val aiLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_ai_line)
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
        alpha = 255
    }
    
    private val powerBarPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.guide_power_bar)
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
    }
    
    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    fun setFeatureEnabled(feature: String, enabled: Boolean) {
        featuresEnabled[feature] = enabled
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
        if (featuresEnabled["main_line"] == true && whiteBallPosition != null && targetBallPosition != null) {
            drawMainLine(canvas)
        }
        if (featuresEnabled["target_line"] == true && targetBallPosition != null && pocketPosition != null) {
            drawTargetLine(canvas)
        }
        if (featuresEnabled["ai_recommend"] == true && aiRecommendation != null) {
            drawAIRecommendPath(canvas)
        }
        if (featuresEnabled["power_bar"] == true && whiteBallPosition != null) {
            drawPowerBar(canvas)
        }
    }
    
    private fun drawMainLine(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val target = targetBallPosition ?: return
        val dx = target.x - white.x
        val dy = target.y - white.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 0) {
            val ux = dx / dist
            val uy = dy / dist
            val endX = target.x + ux * 200
            val endY = target.y + uy * 200
            canvas.drawLine(white.x, white.y, endX, endY, mainLinePaint)
        }
    }
    
    private fun drawTargetLine(canvas: Canvas) {
        val target = targetBallPosition ?: return
        val pocket = pocketPosition ?: return
        canvas.drawLine(target.x, target.y, pocket.x, pocket.y, targetLinePaint)
    }
    
    private fun drawAIRecommendPath(canvas: Canvas) {
        val rec = aiRecommendation ?: return
        val path = rec.path
        if (path.size < 2) return
        for (i in 0 until path.size - 1) {
            canvas.drawLine(path[i].x, path[i].y, path[i+1].x, path[i+1].y, aiLinePaint)
        }
        path.lastOrNull()?.let {
            canvas.drawCircle(it.x, it.y, 12f, aiLinePaint)
        }
    }
    
    private fun drawPowerBar(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val rec = aiRecommendation
        val power = rec?.power ?: 0.5f
        val centerX = white.x
        val centerY = white.y - 100
        val radius = 50f
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(rect, 135f, 270f * power, false, powerBarPaint)
        canvas.drawText("${(power * 100).toInt()}%", centerX, centerY + 10, textPaint)
    }
}
