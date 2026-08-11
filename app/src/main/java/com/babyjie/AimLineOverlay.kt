package com.babyjie

import android.content.Context
import android.graphics.*
import android.view.View

class AimLineOverlay(context: Context) : View(context) {

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.YELLOW
    }
    private val linePaints = mapOf(
        LineType.STRAIGHT to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            color = Color.parseColor("#FF69B4"); pathEffect = DashPathEffect(floatArrayOf(10f,6f),0f)
        },
        LineType.BANK to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            color = Color.parseColor("#00FFAA"); pathEffect = DashPathEffect(floatArrayOf(8f,6f),0f)
        },
        LineType.MULTI_RAIL to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            color = Color.parseColor("#FFA500"); pathEffect = DashPathEffect(floatArrayOf(12f,8f),0f)
        },
        LineType.CUSHION_SHOT to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            color = Color.parseColor("#00BFFF"); pathEffect = DashPathEffect(floatArrayOf(8f,6f),0f)
        },
        LineType.PASS to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            color = Color.parseColor("#FFD700"); pathEffect = DashPathEffect(floatArrayOf(10f,4f),0f)
        }
    )

    var balls: List<BallPosition> = emptyList()
    var aimLine: AimLine? = null
    var currentMode: LineType = LineType.STRAIGHT
    var highlightedBall: PointF? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (ball in balls) {
            ballPaint.color = ball.color
            canvas.drawCircle(ball.x, ball.y, 7f, ballPaint)
        }
        highlightedBall?.let {
            canvas.drawCircle(it.x, it.y, 12f, highlightPaint)
        }
        val line = aimLine
        if (line != null && line.type == currentMode) {
            linePaints[line.type]?.let { paint ->
                canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
            }
        }
    }

    fun clear() {
        balls = emptyList(); aimLine = null; highlightedBall = null; invalidate()
    }
}
