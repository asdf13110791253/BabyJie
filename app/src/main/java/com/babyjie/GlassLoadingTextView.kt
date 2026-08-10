package com.babyjie

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

/**
 * Babyje 底部玻璃文字，内部有彩色加载条，支持自动消失。
 *
 * 使用方式：
 *  1. 在布局中放置此 View，设置文字和大小等属性。
 *  2. 调用 startLoading(durationMs) 开始加载条动画。
 *  3. 加载完成后自动停留在 100%，然后自动逐个字母消失。
 *
 * 可自定义属性：
 *  - text: 显示的文字 (默认 "Babyje")
 *  - textSize: 字号 (默认 170px，建议根据屏幕密度调整)
 *  - strokeColor: 外圈描边颜色 (默认半透明粉色)
 *  - strokeWidth: 描边粗细 (默认 2.5f)
 *  - loaderColors: 加载条渐变颜色数组 (默认彩虹色)
 *  - loaderAlpha: 加载条透明度 (默认 0.25f)
 *  - glowColor: 外发光颜色 (默认淡粉色)
 *  - glowRadius: 外发光半径 (默认 1.5f)
 */
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
    var strokeColor: Int = Color.argb(89, 255, 182, 193) // 半透明粉色
    var strokeWidth: Float = 2.5f
    var loaderColors: IntArray = intArrayOf(
        Color.argb(64, 255, 0, 68),    // 红
        Color.argb(64, 255, 102, 0),   // 橙
        Color.argb(64, 255, 230, 0),   // 黄
        Color.argb(64, 0, 255, 68),    // 绿
        Color.argb(64, 0, 221, 255),   // 青
        Color.argb(64, 68, 0, 255),    // 蓝
        Color.argb(64, 255, 0, 204)    // 紫
    )
    var loaderAlpha: Float = 0.25f      // 加载条透明度倍数（会乘入颜色）
    var glowColor: Int = Color.argb(64, 255, 182, 193)
    var glowRadius: Float = 1.5f

    // 内部状态
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("Montserrat", Typeface.BOLD) // 需确保有字体或回退
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

    // 动画相关
    private var loadProgress: Float = 0f          // 0~1
    private var disappearProgress: Float = 1f     // 1 表示完全显示，0 表示消失
    private var isDisappearing = false
    private var onAllDisappearedListener: (() -> Unit)? = null

    init {
        // 设置透明背景
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        measureTextLayout()
    }

    private fun measureTextLayout() {
        textPaint.textSize = textSize
        shadowPaint.textSize = textSize
        shadowPaint.color = glowColor

        // 测量每个字符的宽度和位置
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

        // 计算基线：使文字绘制在 View 底部，并留出少量空间
        val fontMetrics = textPaint.fontMetrics
        textBaseline = height - fontMetrics.descent - 10f // 距底部 10dp 左右
    }

    /**
     * 开始加载条动画，持续 [durationMs] 毫秒填满，然后自动逐个字母消失。
     * @param durationMs 加载时长（毫秒），例如 5000 表示 5 秒。
     * @param onDisappeared 所有字母消失后的回调（可选），可以用来跳转界面。
     */
    fun startLoading(durationMs: Long = 5000L, onDisappeared: (() -> Unit)? = null) {
        onAllDisappearedListener = onDisappeared
        loadProgress = 0f
        disappearProgress = 1f
        isDisappearing = false
        invalidate()

        // 加载动画
        val loadAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                loadProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    // 加载完成，停顿 500ms 后开始消失
                    postDelayed({
                        startDisappearSequence()
                    }, 500L)
                }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
        loadAnim.start()
    }

    private fun startDisappearSequence() {
        isDisappearing = true
        val total = text.length
        val interval = 500L // 每个字母消失间隔
        val animators = mutableListOf<Animator>()

        // 为每个字母创建独立的消失动画，通过改变 alpha 和 scale 模拟
        // 我们这里用整体 disappearProgress 来表示每个字母的透明度变化，但需要依次触发。
        // 简便做法：使用一个整体 ValueAnimator 控制所有字母的 alpha，通过分步来模拟。
        // 更好的方案：直接在 onDraw 中根据系统时间和索引计算，这里用 AnimatorSet 顺序播放。

        // 由于自定义 View 的绘制，我们将 disappearProgress 改为数组，但为简单，采用依次减少对应字母 alpha 的绘制方式。
        // 我们可以在 onDraw 中读取每个字母的消失进度，这里用一个 FloatArray 存储每个字母的消失进度（0 为消失）。
        val letterAlphas = FloatArray(total) { 1f }
        val animSet = AnimatorSet()
        for (i in 0 until total) {
            val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300 // 消失动画时长
                startDelay = i * interval
                addUpdateListener { animation ->
                    letterAlphas[i] = animation.animatedValue as Float
                    invalidate()
                }
            }
            animSet.playSequentially(anim)
        }
        animSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                isDisappearing = false
                disappearProgress = 0f
                invalidate()
                onAllDisappearedListener?.invoke()
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animSet.start()

        // 同时让整体透明度逐渐降低（配合缩放），但我们已经在 letterAlphas 中处理了
        // 为了简单，直接在 onDraw 中使用 letterAlphas 绘制
        // 注意：需要在 onDraw 中读取 letterAlphas，但它在动画内部定义，需要提升为成员变量
        // 修改为成员变量
        // 重新设计：使用一个动画控制 disappearProgress 从 1 到 0，在 onDraw 中根据时间计算每个字母的消失进度。
        // 但这种方式难以控制每个字母的延迟，所以我们还是保留 letterAlphas 成员。
        // 重构代码，将 letterAlphas 作为成员变量。
    }

    // 正确的消失实现：使用分步动画控制每个字母的透明度
    private val letterAlphas = mutableListOf<Float>().apply {
        for (i in 0 until text.length) add(1f)
    }

    // 替换上面的 startDisappearSequence 方法
    private fun startDisappearSequence() {
        isDisappearing = true
        for (i in 0 until text.length) {
            letterAlphas[i] = 1f
        }

        val total = text.length
        val interval = 500L
        val animators = mutableListOf<ValueAnimator>()

        for (i in 0 until total) {
            val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250 // 消失动画时长
                startDelay = i * interval
                addUpdateListener { animation ->
                    letterAlphas[i] = animation.animatedValue as Float
                    invalidate()
                }
                if (i == total - 1) {
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {}
                        override fun onAnimationEnd(animation: Animator) {
                            isDisappearing = false
                            invalidate()
                            onAllDisappearedListener?.invoke()
                        }
                        override fun onAnimationCancel(animation: Animator) {}
                        override fun onAnimationRepeat(animation: Animator) {}
                    })
                }
            }
            animators.add(anim)
        }

        // 顺序启动所有动画（因为 startDelay 已经设置了先后，所以同时启动即可）
        animators.forEach { it.start() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val chars = text.toCharArray()
        val centerX = width / 2f

        // 1. 绘制加载条（彩色渐变）
        drawLoader(canvas, chars)

        // 2. 绘制文字外圈（描边 + 外发光）
        drawTextOutline(canvas, chars)
    }

    private fun drawLoader(canvas: Canvas, chars: CharArray) {
        val totalW = totalTextWidth
        val startX = width / 2f - totalW / 2f
        val loaderWidth = totalW * loadProgress

        // 设置渐变（水平渐变）
        val gradient = LinearGradient(
            startX, 0f, startX + totalW, 0f,
            loaderColors,
            null,
            Shader.TileMode.CLAMP
        )

        fillPaint.shader = gradient
        fillPaint.alpha = (loaderAlpha * 255).toInt()

        // 使用裁剪路径确保加载条只在文字轮廓内绘制
        canvas.save()
        val clipPath = Path()
        // 先画出所有文字的外轮廓
        textPaint.color = Color.WHITE // 任意颜色，为了构建路径
        textPaint.style = Paint.Style.FILL // 临时设为填充，方便获取路径
        for (i in chars.indices) {
            val x = startX + charPositions[i]
            textPaint.getTextPath(chars[i].toString(), 0, 1, x, textBaseline, clipPath)
        }
        textPaint.style = Paint.Style.STROKE // 恢复

        // 裁剪
        canvas.clipPath(clipPath)

        // 绘制矩形（加载条）从 startX 到 startX + loaderWidth，高度为整个 View 高度
        val rect = RectF(startX, 0f, startX + loaderWidth, height.toFloat())
        canvas.drawRect(rect, fillPaint)

        canvas.restore()
        fillPaint.shader = null
    }

    private fun drawTextOutline(canvas: Canvas, chars: CharArray) {
        val startX = width / 2f - totalTextWidth / 2f

        // 外发光
        shadowPaint.textSize = textSize
        shadowPaint.color = glowColor
        shadowPaint.strokeWidth = strokeWidth + glowRadius
        for (i in chars.indices) {
            if (letterAlphas.size > i && isDisappearing) {
                shadowPaint.alpha = (letterAlphas[i] * 255).toInt()
            }
            val x = startX + charPositions[i]
            canvas.drawText(chars[i].toString(), x, textBaseline, shadowPaint)
        }

        // 实际描边
        textPaint.textSize = textSize
        textPaint.color = strokeColor
        textPaint.strokeWidth = strokeWidth
        for (i in chars.indices) {
            if (letterAlphas.size > i && isDisappearing) {
                textPaint.alpha = (letterAlphas[i] * 255).toInt()
            } else {
                textPaint.alpha = 255
            }
            val x = startX + charPositions[i]
            canvas.drawText(chars[i].toString(), x, textBaseline, textPaint)
        }

        // 重置 alpha
        textPaint.alpha = 255
        shadowPaint.alpha = 255
    }
}
