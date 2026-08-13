// File: app/src/main/java/com/probilliards/ai/SettingsActivity.kt
package com.probilliards.ai

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.probilliards.ai.ai.AIMode
import com.probilliards.ai.service.ScreenCaptureService
import com.probilliards.ai.service.ScreenCaptureService.FrameRateMode

/**
 * 设置页Activity
 * 保存用户偏好设置
 */
class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val PREFS_NAME = "probilliards_settings"
        private const val KEY_MAIN_LINE_COLOR = "main_line_color"
        private const val KEY_TARGET_LINE_COLOR = "target_line_color"
        private const val KEY_REBOUND_LINE_COLOR = "rebound_line_color"
        private const val KEY_AI_LINE_COLOR = "ai_line_color"
        private const val KEY_LINE_WIDTH = "line_width"
        private const val KEY_LINE_ALPHA = "line_alpha"
        private const val KEY_AI_MODE = "ai_mode"
        private const val KEY_FRAME_RATE_MODE = "frame_rate_mode"
    }
    
    private lateinit var spinnerMainLineColor: Spinner
    private lateinit var spinnerTargetLineColor: Spinner
    private lateinit var spinnerReboundLineColor: Spinner
    private lateinit var spinnerAILineColor: Spinner
    private lateinit var seekBarLineWidth: SeekBar
    private lateinit var seekBarLineAlpha: SeekBar
    private lateinit var spinnerAIMode: Spinner
    private lateinit var spinnerFrameRateMode: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        initViews()
        loadSettings()
        setupListeners()
    }
    
    private fun initViews() {
        spinnerMainLineColor = findViewById(R.id.spinnerMainLineColor)
        spinnerTargetLineColor = findViewById(R.id.spinnerTargetLineColor)
        spinnerReboundLineColor = findViewById(R.id.spinnerReboundLineColor)
        spinnerAILineColor = findViewById(R.id.spinnerAILineColor)
        seekBarLineWidth = findViewById(R.id.seekBarLineWidth)
        seekBarLineAlpha = findViewById(R.id.seekBarLineAlpha)
        spinnerAIMode = findViewById(R.id.spinnerAIMode)
        spinnerFrameRateMode = findViewById(R.id.spinnerFrameRateMode)
        btnSave = findViewById(R.id.btnSave)
        btnReset = findViewById(R.id.btnReset)
        
        // 设置颜色选择器
        setupColorSpinner(spinnerMainLineColor, listOf(
            "橙色" to Color.rgb(255, 87, 34),
            "红色" to Color.RED,
            "黄色" to Color.YELLOW,
            "绿色" to Color.GREEN,
            "蓝色" to Color.BLUE,
            "白色" to Color.WHITE
        ))
        
        setupColorSpinner(spinnerTargetLineColor, listOf(
            "绿色" to Color.rgb(76, 175, 80),
            "红色" to Color.RED,
            "黄色" to Color.YELLOW,
            "蓝色" to Color.BLUE,
            "白色" to Color.WHITE
        ))
        
        setupColorSpinner(spinnerReboundLineColor, listOf(
            "蓝色" to Color.rgb(63, 81, 181),
            "红色" to Color.RED,
            "黄色" to Color.YELLOW,
            "绿色" to Color.GREEN,
            "白色" to Color.WHITE
        ))
        
        setupColorSpinner(spinnerAILineColor, listOf(
            "金色" to Color.rgb(255, 193, 7),
            "红色" to Color.RED,
            "黄色" to Color.YELLOW,
            "绿色" to Color.GREEN,
            "白色" to Color.WHITE
        ))
        
        // 设置AI模式
        val aiModes = arrayOf("平衡模式", "进攻模式", "防守模式")
        val aiModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, aiModes)
        aiModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAIMode.adapter = aiModeAdapter
        
        // 设置帧率模式
        val frameRateModes = arrayOf("快速模式(30fps)", "普通模式(20fps)", "省电模式(15fps)")
        val frameRateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frameRateModes)
        frameRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrameRateMode.adapter = frameRateAdapter
        
        // 设置SeekBar范围
        seekBarLineWidth.max = 10
        seekBarLineWidth.progress = 3
        seekBarLineAlpha.max = 255
        seekBarLineAlpha.progress = 200
    }
    
    private fun setupColorSpinner(spinner: Spinner, colors: List<Pair<String, Int>>) {
        val colorNames = colors.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.tag = colors
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // 加载颜色设置
        val mainColor = prefs.getInt(KEY_MAIN_LINE_COLOR, Color.rgb(255, 87, 34))
        val targetColor = prefs.getInt(KEY_TARGET_LINE_COLOR, Color.rgb(76, 175, 80))
        val reboundColor = prefs.getInt(KEY_REBOUND_LINE_COLOR, Color.rgb(63, 81, 181))
        val aiColor = prefs.getInt(KEY_AI_LINE_COLOR, Color.rgb(255, 193, 7))
        
        setSpinnerColor(spinnerMainLineColor, mainColor)
        setSpinnerColor(spinnerTargetLineColor, targetColor)
        setSpinnerColor(spinnerReboundLineColor, reboundColor)
        setSpinnerColor(spinnerAILineColor, aiColor)
        
        // 加载线宽和透明度
        seekBarLineWidth.progress = prefs.getInt(KEY_LINE_WIDTH, 3)
        seekBarLineAlpha.progress = prefs.getInt(KEY_LINE_ALPHA, 200)
        
        // 加载AI模式
        val aiMode = prefs.getInt(KEY_AI_MODE, 0)
        spinnerAIMode.setSelection(aiMode)
        
        // 加载帧率模式
        val frameRateMode = prefs.getInt(KEY_FRAME_RATE_MODE, 1)
        spinnerFrameRateMode.setSelection(frameRateMode)
    }
    
    private fun setSpinnerColor(spinner: Spinner, color: Int) {
        val colors = spinner.tag as? List<Pair<String, Int>> ?: return
        val index = colors.indexOfFirst { it.second == color }
        if (index >= 0) {
            spinner.setSelection(index)
        }
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        btnReset.setOnClickListener {
            resetSettings()
            Toast.makeText(this, "设置已重置", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            // 保存颜色
            putInt(KEY_MAIN_LINE_COLOR, getSpinnerColor(spinnerMainLineColor))
            putInt(KEY_TARGET_LINE_COLOR, getSpinnerColor(spinnerTargetLineColor))
            putInt(KEY_REBOUND_LINE_COLOR, getSpinnerColor(spinnerReboundLineColor))
            putInt(KEY_AI_LINE_COLOR, getSpinnerColor(spinnerAILineColor))
            
            // 保存线宽和透明度
            putInt(KEY_LINE_WIDTH, seekBarLineWidth.progress)
            putInt(KEY_LINE_ALPHA, seekBarLineAlpha.progress)
            
            // 保存AI模式
            putInt(KEY_AI_MODE, spinnerAIMode.selectedItemPosition)
            
            // 保存帧率模式
            putInt(KEY_FRAME_RATE_MODE, spinnerFrameRateMode.selectedItemPosition)
            
            apply()
        }
        
        // 应用帧率模式
        val frameRateMode = when (spinnerFrameRateMode.selectedItemPosition) {
            0 -> FrameRateMode.FAST
            1 -> FrameRateMode.NORMAL
            2 -> FrameRateMode.POWER_SAVING
            else -> FrameRateMode.NORMAL
        }
        ScreenCaptureService.frameRateMode = frameRateMode
    }
    
    private fun getSpinnerColor(spinner: Spinner): Int {
        val colors = spinner.tag as? List<Pair<String, Int>> ?: return Color.WHITE
        return colors.getOrNull(spinner.selectedItemPosition)?.second ?: Color.WHITE
    }
    
    private fun resetSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().clear().apply()
        loadSettings()
    }
}
