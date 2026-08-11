package com.babyjie

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OrientationActivity : AppCompatActivity() {

    private val REQUEST_MEDIA_PROJECTION = 1001
    private var selectedOrientation = "portrait"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orientation)

        findViewById<Button>(R.id.btnPortrait).setOnClickListener {
            selectedOrientation = "portrait"
            requestMediaProjection()
        }
        findViewById<Button>(R.id.btnLandscape).setOnClickListener {
            selectedOrientation = "landscape"
            requestMediaProjection()
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
            // 关闭当前页，悬浮窗保留
            finish()
        }
    }
}
