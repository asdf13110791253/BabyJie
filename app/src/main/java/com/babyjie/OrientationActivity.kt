package com.babyjie

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动录屏授权", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 启动录屏服务（内部会创建透明画布 + 帧分析）
                val captureIntent = ScreenCaptureService.newIntent(this, resultCode, data, selectedOrientation)
                startService(captureIntent)

                // 同时启动悬浮窗服务（粉色按钮 + 菜单）
                startService(Intent(this, FloatWindowService::class.java))
            } else {
                Toast.makeText(this, "需要录屏权限才能使用辅助功能", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
