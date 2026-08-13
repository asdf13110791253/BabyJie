// File: app/src/main/java/com/probilliards/ai/overlay/SelectionRectView.kt
package com.probilliards.ai.overlay

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * 选择区域视图
 * 可拖拽缩放矩形框，用于选择识别区域
 */
class SelectionRectView(context: Context) : View(context) {
    
    var onRectChanged: ((Rect) -> Unit)? = null
    var onConfirmClick: (() -> Unit)? = null
    var onCancelClick: (() -> Unit)? = null
    
    private val rect = RectF()
    private var isAdjusting = false
    private var selectedHandle = -1 // -1: 无, 0-3: 四角, 4-7: 四边
    
    private val borderPaint = Paint().apply {
        color = Color.rgb(255, 193, 7)
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    
    private val handlePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val handleBorderPaint = Paint().apply {
        color = Color.rgb(255, 193, 7)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.rgb(255, 193, 7)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val buttonTextPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    private val handleSize = 30f
    private val buttonWidth = 120f
    private val buttonHeight = 50f
    
    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        // 从SharedPreferences加载上次选择的区域
        val prefs = context.getSharedPreferences("probilliards_prefs", Context.MODE_PRIVATE)
        val left = prefs.getFloat("selection_left", -1f)
        val top = prefs.getFloat("selection_top", -1f)
        val right = prefs.getFloat("selection_right", -1f)
        val bottom = prefs.getFloat("selection_bottom", -1f)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            rect.set(left, top, right, bottom)
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // 首次加载时设置默认选择区域
        if (rect.isEmpty) {
            val paddingX = w * 0.2f
            val paddingY = h * 0.2f
            rect.set(paddingX, paddingY, w - paddingX, h - paddingY)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (rect.isEmpty) return
        
        // 绘制遮罩
        drawOverlay(canvas)
        
        // 绘制选择框
        canvas.drawRect(rect, borderPaint)
        
        // 绘制控制点
        drawHandles(canvas)
        
        // 绘制按钮
        drawButtons(canvas)
    }
    
    private fun drawOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, overlayPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, overlayPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, overlayPaint)
    }
    
    private fun drawHandles(canvas: Canvas) {
        val points = getHandlePoints()
        for (point in points) {
            canvas.drawCircle(point.x, point.y, handleSize / 2, handlePaint)
            canvas.drawCircle(point.x, point.y, handleSize / 2, handleBorderPaint)
        }
    }
    
    private fun getHandlePoints(): List<PointF> {
        return listOf(
            PointF(rect.left, rect.top),      // 左上角
            PointF(rect.right, rect.top),     // 右上角
            PointF(rect.left, rect.bottom),   // 左下角
            PointF(rect.right, rect.bottom),  // 右下角
            PointF(rect.centerX(), rect.top),         // 上边
            PointF(rect.centerX(), rect.bottom),      // 下边
            PointF(rect.left, rect.centerY()),        // 左边
            PointF(rect.right, rect.centerY())        // 右边
        )
    }
    
    private fun drawButtons(canvas: Canvas) {
        val confirmX = rect.centerX() - buttonWidth - 20
        val cancelX = rect.centerX() + 20
        val buttonY = rect.bottom + 60
        
        // 确认按钮
        canvas.drawRoundRect(
            confirmX, buttonY, 
            confirmX + buttonWidth, buttonY + buttonHeight,
            25f, 25f, buttonPaint
        )
        canvas.drawText("确认", confirmX + buttonWidth / 2, 
            buttonY + buttonHeight / 2 + 10, buttonTextPaint)
        
        // 取消按钮
        canvas.drawRoundRect(
            cancelX, buttonY,
            cancelX + buttonWidth, buttonY + buttonHeight,
            25f, 25f, buttonPaint
        )
        canvas.drawText("取消", cancelX + buttonWidth / 2,
            buttonY + buttonHeight / 2 + 10, buttonTextPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isButtonClick(x, y)) {
                    return true
                }
                
                selectedHandle = hitTestHandle(x, y)
                if (selectedHandle >= 0) {
                    isAdjusting = true
                    return true
                }
                
                if (rect.contains(x, y)) {
                    isAdjusting = true
                    selectedHandle = -2 // 移动整个矩形
                    return true
                }
                
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isAdjusting) {
                    adjustRect(x, y)
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (isAdjusting) {
                    isAdjusting = false
                    selectedHandle = -1
                    onRectChanged?.invoke(Rect(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt()
                    ))
                    return true
                }
                
                handleButtonClick(x, y)
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    private fun hitTestHandle(x: Float, y: Float): Int {
        val points = getHandlePoints()
        for (i in points.indices) {
            val dx = x - points[i].x
            val dy = y - points[i].y
            if (sqrt(dx * dx + dy * dy) <= handleSize) {
                return i
            }
        }
        return -1
    }
    
    private fun adjustRect(x: Float, y: Float) {
        val minSize = 100f
        val margin = 20f
        
        when (selectedHandle) {
            -2 -> {
                // 移动整个矩形
                val dx = x - rect.centerX()
                val dy = y - rect.centerY()
                val newLeft = rect.left + dx
                val newTop = rect.top + dy
                val newRight = rect.right + dx
                val newBottom = rect.bottom + dy
                
                if (newLeft >= margin && newTop >= margin && 
                    newRight <= width - margin && newBottom <= height - margin) {
                    rect.set(newLeft, newTop, newRight, newBottom)
                }
            }
            
            0 -> { // 左上角
                if (x < rect.right - minSize && y < rect.bottom - minSize) {
                    rect.left = maxOf(margin, x)
                    rect.top = maxOf(margin, y)
                }
            }
            
            1 -> { // 右上角
                if (x > rect.left + minSize && y < rect.bottom - minSize) {
                    rect.right = minOf(width - margin, x)
                    rect.top = maxOf(margin, y)
                }
            }
            
            2 -> { // 左下角
                if (x < rect.right - minSize && y > rect.top + minSize) {
                    rect.left = maxOf(margin, x)
                    rect.bottom = minOf(height - margin, y)
                }
            }
            
            3 -> { // 右下角
                if (x > rect.left + minSize && y > rect.top + minSize) {
                    rect.right = minOf(width - margin, x)
                    rect.bottom = minOf(height - margin, y)
                }
            }
            
            4 -> { // 上边
                if (y < rect.bottom - minSize) {
                    rect.top = maxOf(margin, y)
                }
            }
            
            5 -> { // 下边
                if (y > rect.top + minSize) {
                    rect.bottom = minOf(height - margin, y)
                }
            }
            
            6 -> { // 左边
                if (x < rect.right - minSize) {
                    rect.left = maxOf(margin, x)
                }
            }
            
            7 -> { // 右边
                if (x > rect.left + minSize) {
                    rect.right = minOf(width - margin, x)
                }
            }
        }
    }
    
    private fun isButtonClick(x: Float, y: Float): Boolean {
        val confirmX = rect.centerX() - buttonWidth - 20
        val cancelX = rect.centerX() + 20
        val buttonY = rect.bottom + 60
        
        return (x >= confirmX && x <= confirmX + buttonWidth &&
                y >= buttonY && y <= buttonY + buttonHeight) ||
               (x >= cancelX && x <= cancelX + buttonWidth &&
                y >= buttonY && y <= buttonY + buttonHeight)
    }
    
    private fun handleButtonClick(x: Float, y: Float) {
        val confirmX = rect.centerX() - buttonWidth - 20
        val cancelX = rect.centerX() + 20
        val buttonY = rect.bottom + 60
        
        // 确认按钮
        if (x >= confirmX && x <= confirmX + buttonWidth &&
            y >= buttonY && y <= buttonY + buttonHeight) {
            onConfirmClick?.invoke()
        }
        
        // 取消按钮
        if (x >= cancelX && x <= cancelX + buttonWidth &&
            y >= buttonY && y <= buttonY + buttonHeight) {
            onCancelClick?.invoke()
        }
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
