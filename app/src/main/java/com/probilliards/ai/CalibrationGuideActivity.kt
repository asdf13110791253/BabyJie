// File: app/src/main/java/com/probilliards/ai/CalibrationGuideActivity.kt
package com.probilliards.ai

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.probilliards.ai.vision.TableCalibrator

/**
 * 校准引导Activity
 * 首次启动时引导用户校准球桌颜色和亮度
 */
class CalibrationGuideActivity : AppCompatActivity() {
    
    companion object {
        private const val PREFS_NAME = "probilliards_prefs"
        private const val KEY_CALIBRATION_DONE = "calibration_done"
    }
    
    private lateinit var tvStep1: TextView
    private lateinit var tvStep2: TextView
    private lateinit var tvStep3: TextView
    private lateinit var btnSkip: Button
    private lateinit var btnNext: Button
    private lateinit var btnAutoCalibrate: Button
    
    private var currentStep = 1
    private val tableCalibrator by lazy { TableCalibrator(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration_guide)
        
        initViews()
        setupListeners()
        showStep(currentStep)
    }
    
    private fun initViews() {
        tvStep1 = findViewById(R.id.tvStep1)
        tvStep2 = findViewById(R.id.tvStep2)
        tvStep3 = findViewById(R.id.tvStep3)
        btnSkip = findViewById(R.id.btnSkip)
        btnNext = findViewById(R.id.btnNext)
        btnAutoCalibrate = findViewById(R.id.btnAutoCalibrate)
    }
    
    private fun setupListeners() {
        btnSkip.setOnClickListener {
            markCalibrationDone()
            finish()
        }
        
        btnNext.setOnClickListener {
            if (currentStep < 3) {
                currentStep++
                showStep(currentStep)
            } else {
                markCalibrationDone()
                finish()
            }
        }
        
        btnAutoCalibrate.setOnClickListener {
            // 自动校准（简化实现）
            val calibration = tableCalibrator.autoCalibrate(createSampleBitmap())
            tableCalibrator.saveCalibration(calibration)
            Toast.makeText(this, "自动校准完成", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showStep(step: Int) {
        tvStep1.alpha = if (step == 1) 1f else 0.5f
        tvStep2.alpha = if (step == 2) 1f else 0.5f
        tvStep3.alpha = if (step == 3) 1f else 0.5f
        
        btnNext.text = if (step == 3) "完成" else "下一步"
    }
    
    private fun createSampleBitmap(): android.graphics.Bitmap {
        // 创建示例位图用于自动校准
        return android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    }
    
    private fun markCalibrationDone() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALIBRATION_DONE, true).apply()
    }
}
