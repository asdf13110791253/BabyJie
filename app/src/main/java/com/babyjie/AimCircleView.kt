package com.babyjie

import android.content.Context
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

    // 当前圈的大小（直径，dp）
    var circleSizeDp = 80f
        set(value) {
            field = value
            val px = dpToPx(value).toInt()
            aimCircle.layoutParams = LayoutParams(px, px)
        }

    init {
        // 背景透明，只显示圆形描边
        aimCircle = View(context).apply {
            setBackgroundResource(R.drawable.aim_circle_bg)
        }
        addView(aimCircle, LayoutParams(dpToPx(80f).toInt(), dpToPx(80f).toInt()))

        // 整个视图可拖动
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    isDragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        x += dx
                        y += dy
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 将瞄准圈中心移动到指定的屏幕坐标
     * @param screenX 目标中心 X 坐标（像素）
     * @param screenY 目标中心 Y 坐标（像素）
     */
    fun moveToScreenPosition(screenX: Float, screenY: Float) {
        x = screenX - width / 2f
        y = screenY - height / 2f
    }

    /**
     * 获取当前瞄准圈的中心坐标
     */
    fun getCenterPosition(): Pair<Float, Float> {
        return Pair(x + width / 2f, y + height / 2f)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
