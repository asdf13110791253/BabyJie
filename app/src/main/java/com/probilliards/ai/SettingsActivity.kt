// File: app/src/main/java/com/probilliards/ai/SettingsActivity.kt
package com.probilliards.ai

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            Toast.makeText(this, "设置已重置", Toast.LENGTH_SHORT).show()
        }
    }
}
