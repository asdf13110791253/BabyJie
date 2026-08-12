package com.babyjie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.View

/**
 * 全屏透明悬浮窗，直接在游戏画面上绘制辅助线、球体标记等。
 * 通过 WindowManager 添加，不响应触摸事件，完全透明。
 */
class FloatingOverlayView(context: Context) : View(context) {

    @Volatile
    var tableCorners: List<android.graphics.PointF>? = null
    @Volatile
    var ballCenter: android.graphics.PointF? = null
    @Volatile
    var ballRadius: Float = 0f
    @Volatile
    var cueLine: Pair<android.graphics.PointF, android.graphics.PointF>? = null

    private val tablePaint = Paint().apply {
        color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val cuePaint = Paint().apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val ballPaint = Paint().apply {
        color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 5f
    }

    /**
     * 更新绘制数据并异步刷新画布。
     * @param corners 台球桌的四个角点（或 null 表示不绘制）
     * @param center 白球的中心坐标（或 null 表示不绘制）
     * @param radius 白球半径
     * @param line 瞄准线（起点为白球，终点为瞄准方向）
     */
    fun updateData(
        corners: List<android.graphics.PointF>?,
        center: android.graphics.PointF?,
        radius: Float,
        line: Pair<android.graphics.PointF, android.graphics.PointF>?
    ) {
        this.tableCorners = corners
        this.ballCenter = center
        this.ballRadius = radius
        this.cueLine = line
        postInvalidate() // 异步刷新，适用于后台线程调用
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制台球桌边框
        tableCorners?.let { corners ->
            if (corners.size == 4) {
                for (i in corners.indices) {
                    val a = corners[i]; val b = corners[(i + 1) % corners.size]
                    canvas.drawLine(a.x, a.y, b.x, b.y, tablePaint)
                }
            }
        }

        // 绘制白球标记
        ballCenter?.let { center ->
            canvas.drawCircle(center.x, center.y, ballRadius, ballPaint)
        }

        // 绘制瞄准延长线
        cueLine?.let { (start, end) ->
            val dx = end.x - start.x; val dy = end.y - start.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                val ux = dx / len; val uy = dy / len
                val farX = end.x + ux * 2000f
                val farY = end.y + uy * 2000f
                canvas.drawLine(start.x, start.y, farX, farY, cuePaint)
            }
        }
    }

    companion object {
        /**
         * 创建适合作为全屏悬浮窗的 LayoutParams：不可点击、不获取焦点、置顶显示。
         */
        fun createLayoutParams(): WindowManager.LayoutParams {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
    }
}
