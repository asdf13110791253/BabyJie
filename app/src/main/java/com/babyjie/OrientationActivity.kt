package com.babyjie

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OrientationActivity : AppCompatActivity() {

    private val REQUEST_MEDIA_PROJECTION = 1001
    private var selectedOrientation = "portrait"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orientation)

        findViewById<Button>(R.id.btnPortrait).setOnClickListener {
            selectedOrientation = "portrait"
            checkOverlayPermissionAndProceed()
        }
        findViewById<Button>(R.id.btnLandscape).setOnClickListener {
            selectedOrientation = "landscape"
            checkOverlayPermissionAndProceed()
        }
    }

    private fun checkOverlayPermissionAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            // 建议用户关闭电池优化
            requestIgnoreBatteryOptimization()
            requestMediaProjection()
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // 某些设备不支持直接跳转，忽略
                }
            }
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            // 启动录屏服务
            val captureIntent = ScreenCaptureService.newIntent(this, resultCode, data, selectedOrientation)
            startService(captureIntent)

            // 启动悬浮窗服务
            startService(Intent(this, FloatWindowService::class.java))

            // 打开透明的保活 Activity，然后关闭自身
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
