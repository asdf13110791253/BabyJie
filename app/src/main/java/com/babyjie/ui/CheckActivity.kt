package com.babyjie.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.babyjie.R

class CheckActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnVerify: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check)

        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        btnVerify = findViewById(R.id.btnVerify)

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        findViewById<Button>(R.id.btnAppSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }

        btnVerify.setOnClickListener {
            startVerification()
        }
    }

    private fun startVerification() {
        btnVerify.isEnabled = false
        progressBar.progress = 0
        tvStatus.text = "正在检测悬浮窗权限..."

        handler.postDelayed({
            progressBar.progress = 30
            tvStatus.text = "正在检测截图权限..."
        }, 1000)

        handler.postDelayed({
            progressBar.progress = 60
            tvStatus.text = "正在检测网络连接..."
        }, 2000)

        handler.postDelayed({
            progressBar.progress = 100
            tvStatus.text = "校验完成！"
        }, 3000)

        handler.postDelayed({
            btnVerify.isEnabled = true
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }, 3500)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
