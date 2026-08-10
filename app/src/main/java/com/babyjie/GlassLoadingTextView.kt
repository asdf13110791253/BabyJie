package com.babyjie

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class GlassLoadingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 可调参数
    var text: String = "Babyje"
        set(value) { field = value; requestLayout() }
    var textSize: Float = 170f
        set(value) { field = value; requestLayout() }
    var strokeColor: Int = Color.argb(89, 255, 182, 193)   // 半透明粉色
    var strokeWidth: Float = 2.5f
    var loaderColors: IntArray = intArrayOf(
        Color.argb(64, 255, 0, 68),
        Color.argb(64, 255, 102, 0),
        Color.argb(64, 255, 230, 0),
        Color.argb(64, 0, 255, 68),
        Color.argb(64, 0, 221, 255),
        Color.argb(64, 68, 0, 255),
        Color.argb(64, 255, 0, 204)
    )
    var loaderAlpha: Float = 0.25f
    var glowColor: Int = Color.argb(64, 255, 182, 193)
    var glowRadius: Float = 1.5f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
    }

    private var charWidths: FloatArray = floatArrayOf()
    private var charPositions: FloatArray = floatArrayOf()
    private var textBaseline: Float = 0f
    private var totalTextWidth: Float = 0f

    // 文字区域的上下边界（用于加载条的绘制范围）
    private var textTop: Float = 0f
    private var textBottom: Float = 0f

    private var loadProgress: Float = 0f
    private val letterAlphas = mutableListOf<Float>()
    private var isDisappearing = false
    private var onAllDisappearedListener: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    // 重写测量，让 View 的高度恰好包裹文字（顶部留少量余地）
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics
        val desiredHeight = (fontMetrics.descent - fontMetrics.ascent).toInt() + paddingTop + paddingBottom + 10
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        measureTextLayout()
    }

    private fun measureTextLayout() {
        textPaint.textSize = textSize
        shadowPaint.textSize = textSize
        shadowPaint.color = glowColor

        val chars = text.toCharArray()
        charWidths = FloatArray(chars.size)
        charPositions = FloatArray(chars.size)
        var totalW = 0f
        for (i in chars.indices) {
            val w = textPaint.measureText(chars, i, 1)
            charWidths[i] = w
            charPositions[i] = totalW + w / 2f
            totalW += w
        }
        totalTextWidth = totalW

        // 使文字紧贴 View 底部，descent 是基线到文字最低点的距离
        val fm = textPaint.fontMetrics
        textBaseline = height.toFloat() - fm.descent - paddingBottom - 5f  // 少量间距

        // 保存文字区域用于加载条绘制
        textTop = textBaseline + fm.ascent   // 文字顶部（ascent 是负值）
        textBottom = textBaseline + fm.descent // 文字底部
    }

    fun startLoading(durationMs: Long = 5000L, onDisappeared: (() -> Unit)? = null) {
        onAllDisappearedListener = onDisappeared
        loadProgress = 0f
        isDisappearing = false
        letterAlphas.clear()
        for (i in text.indices) {
            letterAlphas.add(1f)
        }
        invalidate()

        val loadAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                loadProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    postDelayed({
                        startDisappearSequence()
                    }, 500L)
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        loadAnim.start()
    }

    private fun startDisappearSequence() {
        isDisappearing = true
        val total = text.length
        val interval = 500L

        for (i in 0 until total) {
            val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250L
                startDelay = i * interval
                addUpdateListener { animation ->
                    letterAlphas[i] = animation.animatedValue as Float
                    invalidate()
                }
                if (i == total - 1) {
                    addListener(object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            isDisappearing = false
                            invalidate()
                            onAllDisappearedListener?.invoke()
                        }
                        override fun onAnimationCancel(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                    })
                }
            }
            anim.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val chars = text.toCharArray()
        val startX = width / 2f - totalTextWidth / 2f

        // 1. 彩色加载条
        drawLoader(canvas, chars, startX)

        // 2. 外圈描边 + 光晕
        drawTextOutline(canvas, chars, startX)
    }

    private fun drawLoader(canvas: Canvas, chars: CharArray, startX: Float) {
        val totalW = totalTextWidth
        val loaderWidth = totalW * loadProgress

        // 渐变从 startX 到 startX + totalW
        val gradient = LinearGradient(
            startX, 0f, startX + totalW, 0f,
            loaderColors, null, Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
        fillPaint.alpha = (loaderAlpha * 255).toInt()

        canvas.save()
        // 构建文字轮廓的裁剪路径
        val clipPath = Path()
        for (i in chars.indices) {
            val x = startX + charPositions[i]
            textPaint.style = Paint.Style.FILL
            textPaint.getTextPath(chars[i].toString(), 0, 1, x, textBaseline, clipPath)
        }
        textPaint.style = Paint.Style.STROKE

        canvas.clipPath(clipPath)
        // 加载条矩形：只覆盖文字区域的高度范围，避免跑到屏幕其他位置
        val rect = RectF(startX, textTop, startX + loaderWidth, textBottom)
        canvas.drawRect(rect, fillPaint)
        canvas.restore()

        fillPaint.shader = null
    }

    private fun drawTextOutline(canvas: Canvas, chars: CharArray, startX: Float) {
        // 外发光
        shadowPaint.strokeWidth = strokeWidth + glowRadius
        for (i in chars.indices) {
            val alpha = if (letterAlphas.size > i) letterAlphas[i] else 1f
            shadowPaint.alpha = (alpha * 255).toInt()
            val x = startX + charPositions[i]
            canvas.drawText(chars[i].toString(), x, textBaseline, shadowPaint)
        }

        // 实际描边
        textPaint.color = strokeColor
        textPaint.strokeWidth = strokeWidth
        for (i in chars.indices) {
            val alpha = if (letterAlphas.size > i) letterAlphas[i] else 1f
            textPaint.alpha = (alpha * 255).toInt()
            val x = startX + charPositions[i]
            canvas.drawText(chars[i].toString(), x, textBaseline, textPaint)
        }

        textPaint.alpha = 255
        shadowPaint.alpha = 255
    }
}
