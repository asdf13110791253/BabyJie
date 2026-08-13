// File: app/src/main/java/com/probilliards/ai/overlay/GuideLineView.kt
package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sqrt

/**
 * 辅助线视图
 * 绘制各种瞄准线和AI推荐路线
 */
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
    private var aiRecommendPath: List<PointF> = emptyList()
    
    private val mainLinePaint = Paint().apply {
        color = Color.rgb(255, 87, 34) // 橙色
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    
    private val targetLinePaint = Paint().apply {
        color = Color.rgb(76, 175, 80) // 绿色
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val reboundLinePaint = Paint().apply {
        color = Color.rgb(63, 81, 181) // 蓝色
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f)
    }
    
    private val aiRecommendPaint = Paint().apply {
        color = Color.rgb(255, 193, 7) // 金色
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    
    private val powerBarPaint = Paint().apply {
        color = Color.rgb(255, 87, 34)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val ballPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val circlePaint = Paint().apply {
        color = Color.rgb(255, 87, 34)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    fun setFeatureEnabled(feature: String, enabled: Boolean) {
        featuresEnabled[feature] = enabled
        invalidate()
    }
    
    fun updateFrame(bitmap: Bitmap) {
        // 这里会接收图像识别结果并更新球的位置
        // 实际项目中会调用OpenCV处理后的结果
        invalidate()
    }
    
    fun updateBallPositions(white: PointF?, target: PointF?, pocket: PointF?) {
        whiteBallPosition = white
        targetBallPosition = target
        pocketPosition = pocket
        invalidate()
    }
    
    fun updateAIRecommendPath(path: List<PointF>) {
        aiRecommendPath = path
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制主瞄准线
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
        
        // 绘制AI推荐路线
        if (featuresEnabled["ai_recommend"] == true && aiRecommendPath.isNotEmpty()) {
            drawAIRecommendPath(canvas)
        }
        
        // 绘制力度条
        if (featuresEnabled["power_bar"] == true && whiteBallPosition != null) {
            drawPowerBar(canvas)
        }
    }
    
    private fun drawMainLine(canvas: Canvas) {
        val white = whiteBallPosition ?: return
        val target = targetBallPosition ?: return
        
        // 计算白球到目标球的方向
        val dx = target.x - white.x
        val dy = target.y - white.y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 0) {
            val unitX = dx / distance
            val unitY = dy / distance
            
            // 绘制瞄准线
            val startX = white.x
            val startY = white.y
            val endX = target.x + unitX * 200
            val endY = target.y + unitY * 200
            
            canvas.drawLine(startX, startY, endX, endY, mainLinePaint)
            
            // 绘制瞄准点圆圈
            val aimX = target.x + unitX * 30
            val aimY = target.y + unitY * 30
            canvas.drawCircle(aimX, aimY, 15f, circlePaint)
        }
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
        
        // 简化的反弹线计算
        val dx = target.x - white.x
        val dy = target.y - white.y
        
        val reboundX = screenWidth - 50
        val reboundY = white.y + dy * (reboundX - white.x) / dx
        
        canvas.drawLine(white.x, white.y, reboundX, reboundY, reboundLinePaint)
        canvas.drawLine(reboundX, reboundY, target.x, target.y, reboundLinePaint)
        
        canvas.drawCircle(reboundX, reboundY, 8f, ballPaint)
    }
    
    private fun drawAIRecommendPath(canvas: Canvas) {
        if (aiRecommendPath.size < 2) return
        
        val path = Path()
        path.moveTo(aiRecommendPath[0].x, aiRecommendPath[0].y)
        
        for (i in 1 until aiRecommendPath.size) {
            path.lineTo(aiRecommendPath[i].x, aiRecommendPath[i].y)
        }
        
        canvas.drawPath(path, aiRecommendPaint)
        
        aiRecommendPath.lastOrNull()?.let { endPoint ->
            canvas.drawCircle(endPoint.x, endPoint.y, 12f, aiRecommendPaint)
        }
    }
    
    private fun drawPowerBar(canvas: Canvas) {
        val barX = width - 80f
        val barY = height / 3f
        val barWidth = 20f
        val barHeight = height / 3f
        
        // 背景
        val bgPaint = Paint().apply {
            color = Color.argb(100, 128, 128, 128)
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            barX, barY, barX + barWidth, barY + barHeight,
            10f, 10f, bgPaint
        )
        
        // 力度填充（50%）
        val fillHeight = barHeight * 0.5f
        canvas.drawRoundRect(
            barX, barY + barHeight - fillHeight, 
            barX + barWidth, barY + barHeight,
            10f, 10f, powerBarPaint
        )
        
        // 力度文字
        val textPaint = Paint().apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("50%", barX + barWidth / 2, barY - 20, textPaint)
    }
}
