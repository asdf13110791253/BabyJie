// File: app/src/main/java/com/probilliards/ai/overlay/FloatingBallView.kt
package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.probilliards.ai.R
import kotlin.math.abs

/**
 * 悬浮球视图（暗夜玫瑰主题版）
 */
class FloatingBallView(context: Context) : View(context) {
    
    var onBallClick: (() -> Unit)? = null
    var onBallDrag: ((Int, Int) -> Unit)? = null
    var onBallLongPress: (() -> Unit)? = null
    
    // 主题颜色
    private val ballStartColor = ContextCompat.getColor(context, R.color.floating_ball_start)
    private val ballCenterColor = ContextCompat.getColor(context, R.color.floating_ball_center)
    private val ballEndColor = ContextCompat.getColor(context, R.color.floating_ball_end)
    private val ballBorderColor = ContextCompat.getColor(context, R.color.floating_ball_border)
    private val ballGlowColor = ContextCompat.getColor(context, R.color.floating_ball_glow)
    private val ballShadowColor = ContextCompat.getColor(context, R.color.floating_ball_shadow)
    private val ballIconColor = ContextCompat.getColor(context, R.color.floating_ball_icon)
    private val ballHighlightColor = ContextCompat.getColor(context, R.color.floating_ball_highlight)
    
    private val ballPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        shader = RadialGradient(
            25f, 25f, 40f,
            intArrayOf(ballStartColor, ballCenterColor, ballEndColor),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    
    private val borderPaint = Paint().apply {
        color = ballBorderColor
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val glowPaint = Paint().apply {
        color = ballGlowColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val iconPaint = Paint().apply {
        color = ballIconColor
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 30f
        isFakeBoldText = true
    }
    
    private val ballRadius = 40f
    private var isDragging = false
    private var isLongPress = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragThreshold = 10f
    private var longPressThreshold = 500L
    
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPress = true
            onBallLongPress?.invoke()
        }
    }
    
    init {
        setWillNotDraw(false)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (ballRadius * 2 + 20).toInt()
        setMeasuredDimension(size, size)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        
        // 外层光晕
        canvas.drawCircle(cx, cy, ballRadius + 8, glowPaint)
        
        // 阴影
        val shadowPaint = Paint().apply {
            color = ballShadowColor
            isAntiAlias = true
        }
        canvas.drawCircle(cx + 3, cy + 3, ballRadius, shadowPaint)
        
        // 球体（渐变）
        canvas.drawCircle(cx, cy, ballRadius, ballPaint)
        
        // 边框
        canvas.drawCircle(cx, cy, ballRadius, borderPaint)
        
        // 8号球图标
        val iconCirclePaint = Paint().apply {
            color = ballIconColor
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy - 10, 12f, iconCirclePaint)
        canvas.drawText("8", cx, cy + 8, iconPaint)
        
        // 高光
        val highlightPaint = Paint().apply {
            color = ballHighlightColor
            isAntiAlias = true
        }
        canvas.drawCircle(cx - 12, cy - 15, 10f, highlightPaint)
        
        // 长按状态显示
        if (isLongPress) {
            val longPressGlowPaint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.rose_pink_glow)
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, ballRadius + 12, longPressGlowPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                isLongPress = false
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                handler.postDelayed(longPressRunnable, longPressThreshold)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - lastTouchX)
                val dy = abs(event.rawY - lastTouchY)
                
                if (dx > dragThreshold || dy > dragThreshold) {
                    isDragging = true
                    handler.removeCallbacks(longPressRunnable)
                }
                
                if (isDragging) {
                    val newX = event.rawX.toInt() - width / 2
                    val newY = event.rawY.toInt() - height / 2
                    onBallDrag?.invoke(newX, newY)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                
                if (!isDragging && !isLongPress) {
                    performClick()
                    onBallClick?.invoke()
                }
                
                isDragging = false
                isLongPress = false
                invalidate()
                return true
            }
            
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                isDragging = false
                isLongPress = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
