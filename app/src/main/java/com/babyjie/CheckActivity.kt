package com.babyjie

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class CheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check)

        // 2 秒后自动跳转到横竖屏选择页
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }, 2000)
    }
}
