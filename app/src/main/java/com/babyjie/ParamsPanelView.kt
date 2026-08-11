package com.babyjie

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView

class ParamsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var panelView: View
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    var onDoneListener: ((power: Int, angle: Int, sensitivity: Int) -> Unit)? = null

    private val sbPower: SeekBar
    private val sbAngle: SeekBar
    private val sbSensitivity: SeekBar
    private val tvPowerValue: TextView
    private val tvAngleValue: TextView
    private val tvSensitivityValue: TextView

    init {
        panelView = LayoutInflater.from(context).inflate(R.layout.view_params_panel, this, true)
        sbPower = findViewById(R.id.sbPower)
        sbAngle = findViewById(R.id.sbAngle)
        sbSensitivity = findViewById(R.id.sbSensitivity)
        tvPowerValue = findViewById(R.id.tvPowerValue)
        tvAngleValue = findViewById(R.id.tvAngleValue)
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue)

        sbPower.setOnSeekBarChangeListener(createListener(tvPowerValue))
        sbAngle.setOnSeekBarChangeListener(createListener(tvAngleValue))
        sbSensitivity.setOnSeekBarChangeListener(createListener(tvSensitivityValue))

        findViewById<TextView>(R.id.btnParamsDone).setOnClickListener {
            onDoneListener?.invoke(sbPower.progress, sbAngle.progress, sbSensitivity.progress)
        }

        // 标题栏可拖动
        findViewById<TextView>(R.id.tvPanelTitle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX; lastY = event.rawY; isDragging = true; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.rawX - lastX; val dy = event.rawY - lastY
                        x += dx; y += dy
                        lastX = event.rawX; lastY = event.rawY
                    }; true
                }
                MotionEvent.ACTION_UP -> { isDragging = false; true }
                else -> false
            }
        }
    }

    private fun createListener(textView: TextView): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textView.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
}
