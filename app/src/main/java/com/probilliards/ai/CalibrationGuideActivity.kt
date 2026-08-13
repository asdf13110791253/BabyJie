// File: app/src/main/java/com/probilliards/ai/CalibrationGuideActivity.kt
package com.probilliards.ai

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CalibrationGuideActivity : AppCompatActivity() {
    
    private lateinit var btnSkip: Button
    private lateinit var btnNext: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration_guide)
        
        btnSkip = findViewById(R.id.btnSkip)
        btnNext = findViewById(R.id.btnNext)
        
        btnSkip.setOnClickListener {
            val prefs = getSharedPreferences("probilliards_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("calibration_done", true).apply()
            finish()
        }
        
        btnNext.setOnClickListener {
            Toast.makeText(this, "校准完成", Toast.LENGTH_SHORT).show()
            val prefs = getSharedPreferences("probilliards_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("calibration_done", true).apply()
            finish()
        }
    }
}
