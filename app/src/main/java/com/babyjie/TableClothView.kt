package com.babyjie

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TableClothView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 选择框
    private lateinit var selectionBox: View
    // 四个方向的微调按钮
    private lateinit var btnLeft: View
    private lateinit var btnRight: View
    private lateinit var btnTop: View
    private lateinit var btnBottom: View
    // 四角缩放按钮
    private lateinit var btnTopLeft: View
    private lateinit var btnTopRight: View
    private lateinit var btnBottomLeft: View
    private lateinit var btnBottomRight: View

    // 信息面板
    private lateinit var infoPanel: LinearLayout
    private lateinit var tvRegionSize: TextView
    private var stepIndex = 1 // 0=小(2px), 1=中(8px), 2=大(20px)
    private val steps = floatArrayOf(2f, 8f, 20f)

    // 完成按钮
    private lateinit var btnDone: TextView

    // 状态变量
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var isResizing = false
    private val resizeBorder = 80f
    private var resizeEdge = 0

    var detectionRegion: RectF = RectF()
        private set

    var onDoneListener: ((RectF) -> Unit)? = null

    init {
        setBackgroundColor(0x00000000)

        // 选择框
        selectionBox = View(context).apply {
            setBackgroundResource(R.drawable.selection_box_bg)
        }
        addView(selectionBox, LayoutParams(600, 400).apply {
            gravity = Gravity.CENTER
        })

        // 微调按钮（圆形毛玻璃）
        btnLeft = createAdjustButton("◀", Gravity.START or Gravity.CENTER_VERTICAL)
        btnRight = createAdjustButton("▶", Gravity.END or Gravity.CENTER_VERTICAL)
        btnTop = createAdjustButton("▲", Gravity.CENTER_HORIZONTAL or Gravity.TOP)
        btnBottom = createAdjustButton("▼", Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)

        // 四角缩放点
        btnTopLeft = createCornerButton(Gravity.TOP or Gravity.START)
        btnTopRight = createCornerButton(Gravity.TOP or Gravity.END)
        btnBottomLeft = createCornerButton(Gravity.BOTTOM or Gravity.START)
        btnBottomRight = createCornerButton(Gravity.BOTTOM or Gravity.END)

        // 信息面板
        infoPanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC0A0A1A.toInt())
            setPadding(20, 12, 20, 12)
        }
        tvRegionSize = TextView(context).apply {
            text = "600 × 400"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        val btnStepDown = createStepButton("-")
        val btnStepUp = createStepButton("+")
        infoPanel.addView(btnStepDown)
        infoPanel.addView(tvRegionSize, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER; marginStart = 12; marginEnd = 12 })
        infoPanel.addView(btnStepUp)
        btnStepDown.setOnClickListener { stepIndex = maxOf(stepIndex - 1, 0); updateStepHint() }
        btnStepUp.setOnClickListener { stepIndex = minOf(stepIndex + 1, 2); updateStepHint() }
        addView(infoPanel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 40
        })

        // 完成按钮
        btnDone = TextView(context).apply {
            text = "完成"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCCFF69B4.toInt())
            gravity = Gravity.CENTER
            setPadding(48, 20, 48, 20)
            textSize = 18f
        }
        addView(btnDone, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 150
        })

        setupListeners()
    }

    private fun createAdjustButton(text: String, gravity: Int): View {
        val btn = TextView(context).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.btn_micro_adjust_bg)
            this.gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            textSize = 22f
        }
        addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { this.gravity = gravity })
        return btn
    }

    private fun createCornerButton(gravity: Int): View {
        val dot = View(context).apply {
            setBackgroundColor(0xCCFF69B4.toInt())
            layoutParams = LayoutParams(24, 24)
        }
        addView(dot, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { this.gravity = gravity })
        return dot
    }

    private fun createStepButton(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x30FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            textSize = 18f
        }
    }

    private fun updateStepHint() {
        val hint = when (stepIndex) {
            0 -> "微调: 2px"
            1 -> "微调: 8px"
            else -> "微调: 20px"
        }
        Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
    }

    private fun setupListeners() {
        btnLeft.setOnClickListener { adjustLeft(-steps[stepIndex]) }
        btnRight.setOnClickListener { adjustRight(steps[stepIndex]) }
        btnTop.setOnClickListener { adjustTop(-steps[stepIndex]) }
        btnBottom.setOnClickListener { adjustBottom(steps[stepIndex]) }
        btnDone.setOnClickListener { onDoneListener?.invoke(detectionRegion) }
    }

    private fun adjustLeft(dx: Float) {
        val lp = selectionBox.layoutParams as LayoutParams
        lp.leftMargin = (lp.leftMargin + dx.toInt()).coerceAtLeast(0)
        selectionBox.layoutParams = lp
        updateRegion()
    }

    private fun adjustRight(dx: Float) {
        val lp = selectionBox.layoutParams as LayoutParams
        lp.width = (lp.width + dx.toInt()).coerceAtLeast(80)
        selectionBox.layoutParams = lp
        updateRegion()
    }

    private fun adjustTop(dy: Float) {
        val lp = selectionBox.layoutParams as LayoutParams
        lp.topMargin = (lp.topMargin + dy.toInt()).coerceAtLeast(0)
        selectionBox.layoutParams = lp
        updateRegion()
    }

    private fun adjustBottom(dy: Float) {
        val lp = selectionBox.layoutParams as LayoutParams
        lp.height = (lp.height + dy.toInt()).coerceAtLeast(80)
        selectionBox.layoutParams = lp
        updateRegion()
    }

    private fun updateRegion() {
        val lp = selectionBox.layoutParams as LayoutParams
        detectionRegion.set(
            lp.leftMargin.toFloat(),
            lp.topMargin.toFloat(),
            (lp.leftMargin + lp.width).toFloat(),
            (lp.topMargin + lp.height).toFloat()
        )
        tvRegionSize.text = "${lp.width} × ${lp.height}"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val lp = selectionBox.layoutParams as LayoutParams

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                val leftEdge = selectionBox.x + resizeBorder
                val rightEdge = selectionBox.x + selectionBox.width - resizeBorder
                val topEdge = selectionBox.y + resizeBorder
                val bottomEdge = selectionBox.y + selectionBox.height - resizeBorder

                if (x < leftEdge && y > topEdge && y < bottomEdge) { isResizing = true; resizeEdge = 1 }
                else if (x > rightEdge && y > topEdge && y < bottomEdge) { isResizing = true; resizeEdge = 2 }
                else if (y < topEdge && x > leftEdge && x < rightEdge) { isResizing = true; resizeEdge = 3 }
                else if (y > bottomEdge && x > leftEdge && x < rightEdge) { isResizing = true; resizeEdge = 4 }
                else { isDragging = true }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = (x - lastX).toInt()
                    val dy = (y - lastY).toInt()
                    lp.leftMargin = (lp.leftMargin + dx).coerceAtLeast(0)
                    lp.topMargin = (lp.topMargin + dy).coerceAtLeast(0)
                    selectionBox.layoutParams = lp
                    lastX = x; lastY = y
                    updateRegion()
                } else if (isResizing) {
                    when (resizeEdge) {
                        1 -> { val dx = (x - lastX).toInt(); lp.leftMargin = (lp.leftMargin + dx).coerceAtLeast(0); lp.width = (lp.width - dx).coerceAtLeast(80) }
                        2 -> { val dx = (x - lastX).toInt(); lp.width = (lp.width + dx).coerceAtLeast(80) }
                        3 -> { val dy = (y - lastY).toInt(); lp.topMargin = (lp.topMargin + dy).coerceAtLeast(0); lp.height = (lp.height - dy).coerceAtLeast(80) }
                        4 -> { val dy = (y - lastY).toInt(); lp.height = (lp.height + dy).coerceAtLeast(80) }
                    }
                    selectionBox.layoutParams = lp
                    lastX = x; lastY = y
                    updateRegion()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false; isResizing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
