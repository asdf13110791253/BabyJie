// File: app/src/main/java/com/probilliards/ai/overlay/FloatingBallView.kt
package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 悬浮球视图（更新版）
 * 支持单击展开面板、长按快速拖动
 */
class FloatingBallView(context: Context) : View(context) {
    
    var onBallClick: (() -> Unit)? = null
    var onBallDrag: ((Int, Int) -> Unit)? = null
    var onBallLongPress: (() -> Unit)? = null
    
    private val ballPaint = Paint().apply {
        color = Color.rgb(255, 193, 7)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val iconPaint = Paint().apply {
        color = Color.BLACK
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
    private var longPressThreshold = 500L // 500ms
    
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
        
        // 绘制阴影
        val shadowPaint = Paint().apply {
            color = Color.argb(60, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawCircle(cx + 3, cy + 3, ballRadius, shadowPaint)
        
        // 绘制球体
        canvas.drawCircle(cx, cy, ballRadius, ballPaint)
        canvas.drawCircle(cx, cy, ballRadius, borderPaint)
        
        // 绘制8号球图标
        canvas.drawCircle(cx, cy - 10, 12f, Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        })
        canvas.drawText("8", cx, cy + 8, iconPaint)
        
        // 绘制高光
        val highlightPaint = Paint().apply {
            color = Color.argb(100, 255, 255, 255)
            isAntiAlias = true
        }
        canvas.drawCircle(cx - 10, cy - 15, 8f, highlightPaint)
        
        // 长按状态显示不同效果
        if (isLongPress) {
            val glowPaint = Paint().apply {
                color = Color.argb(50, 255, 255, 255)
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, ballRadius + 8, glowPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                isLongPress = false
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                
                // 启动长按检测
                handler.postDelayed(longPressRunnable, longPressThreshold)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - lastTouchX)
                val dy = abs(event.rawY - lastTouchY)
                
                if (dx > dragThreshold || dy > dragThreshold) {
                    isDragging = true
                    // 如果已经开始拖动，取消长按检测
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
                    // 单击事件
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
