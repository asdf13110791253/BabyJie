package com.babyjie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class AimCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var aimCircle: View
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    // 方向线参数
    private var lineAngle = 0f
    private var lineLength = 40f
    private var useDashed = true
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF69B4")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    var circleSizeDp = 80f
        set(value) {
            field = value
            val px = dpToPx(value).toInt()
            aimCircle.layoutParams = LayoutParams(px, px)
        }

    init {
        aimCircle = View(context).apply {
            setBackgroundResource(R.drawable.aim_circle_bg)
        }
        addView(aimCircle, LayoutParams(dpToPx(80f).toInt(), dpToPx(80f).toInt()))
        setWillNotDraw(false)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX; lastY = event.rawY
                    isDragging = true; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        x += dx; y += dy
                        lastX = event.rawX; lastY = event.rawY
                    }; true
                }
                MotionEvent.ACTION_UP -> { isDragging = false; true }
                else -> false
            }
        }
    }

    fun setDirectionLine(angle: Float, length: Float, dashed: Boolean = true) {
        lineAngle = angle
        lineLength = length
        useDashed = dashed
        invalidate()
    }

    fun moveToScreenPosition(screenX: Float, screenY: Float) {
        x = screenX - width / 2f
        y = screenY - height / 2f
    }

    fun getCenterPosition(): Pair<Float, Float> = Pair(x + width / 2f, y + height / 2f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lineLength > 0f) {
            val centerX = width / 2f
            val centerY = height / 2f
            val rad = Math.toRadians(lineAngle.toDouble())
            val endX = centerX + dpToPx(lineLength) * Math.cos(rad).toFloat()
            val endY = centerY + dpToPx(lineLength) * Math.sin(rad).toFloat()

            if (useDashed) {
                linePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            } else {
                linePaint.pathEffect = null
            }
            canvas.drawLine(centerX, centerY, endX, endY, linePaint)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
