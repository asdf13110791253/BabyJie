package com.babyjie

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.sqrt

class SlideToUnlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onUnlockListener: (() -> Unit)? = null

    private var hintText = "滑动解锁"

    private val thumbRadius = 30f * resources.displayMetrics.density
    private val trackHeight = 8f * resources.displayMetrics.density
    private val paddingH = 12f * resources.displayMetrics.density

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.density
        color = Color.WHITE
        isFakeBoldText = true
    }

    private var trackRect = RectF()
    private var thumbX = 0f
    private var isDragging = false
    private var isUnlocked = false

    fun setHintText(text: String) {
        hintText = text
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = paddingH + thumbRadius
        val right = w - paddingH - thumbRadius
        val top = h / 2f - trackHeight / 2f
        val bottom = h / 2f + trackHeight / 2f
        trackRect = RectF(left, top, right, bottom)
        if (!isDragging && !isUnlocked) thumbX = trackRect.left
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        trackBgPaint.shader = null
        trackBgPaint.color = Color.parseColor("#2AFFFFFF")
        trackBgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackBgPaint)

        if (thumbX > trackRect.left) {
            val gradient = LinearGradient(
                trackRect.left, 0f, trackRect.right, 0f,
                Color.parseColor("#FF69B4"),
                Color.parseColor("#FF1493"),
                Shader.TileMode.CLAMP
            )
            trackProgressPaint.shader = gradient
            trackProgressPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                RectF(trackRect.left, trackRect.top, thumbX, trackRect.bottom),
                trackHeight / 2f, trackHeight / 2f, trackProgressPaint
            )
        }

        thumbShadowPaint.color = Color.parseColor("#33000000")
        thumbShadowPaint.style = Paint.Style.FILL
        thumbShadowPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(thumbX, height / 2f + 2f, thumbRadius, thumbShadowPaint)

        val thumbGradient = RadialGradient(
            thumbX, height / 2f, thumbRadius,
            intArrayOf(Color.WHITE, Color.parseColor("#FFB6C1"), Color.parseColor("#FF69B4")),
            floatArrayOf(0.2f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        thumbPaint.shader = thumbGradient
        thumbPaint.style = Paint.Style.FILL
        canvas.drawCircle(thumbX, height / 2f, thumbRadius, thumbPaint)

        textPaint.textSize = 16f * resources.displayMetrics.density
        canvas.drawText("→", thumbX, height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)

        textPaint.textSize = 14f * resources.displayMetrics.density
        val trackCenterY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(hintText, trackRect.centerX(), trackCenterY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isUnlocked) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - thumbX
                val dy = event.y - height / 2f
                if (sqrt(dx * dx + dy * dy.toDouble()) <= thumbRadius * 1.8f) {
                    isDragging = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    thumbX = event.x.coerceIn(trackRect.left, trackRect.right)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    if (thumbX >= trackRect.right - thumbRadius) {
                        isUnlocked = true
                        animateThumbTo(trackRect.right) { onUnlockListener?.invoke() }
                    } else {
                        animateThumbTo(trackRect.left)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateThumbTo(target: Float, onEnd: (() -> Unit)? = null) {
        val anim = ValueAnimator.ofFloat(thumbX, target).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { thumbX = it.animatedValue as Float; invalidate() }
        }
        anim.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) { onEnd?.invoke() }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        anim.start()
    }

    fun reset() {
        isUnlocked = false
        isDragging = false
        thumbX = trackRect.left
        invalidate()
    }
}
