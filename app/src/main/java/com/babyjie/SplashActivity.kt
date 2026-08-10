package com.babyjie

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.splashVideoView)
        val container = findViewById<FrameLayout>(R.id.splashContainer)

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))

        videoView.setOnPreparedListener { mp ->
            // 获取视频原始尺寸
            val videoWidth = mp.videoWidth
            val videoHeight = mp.videoHeight

            // 获取屏幕实际可用尺寸（考虑状态栏导航栏）
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            // 计算缩放比例，使视频至少覆盖屏幕的宽和高
            val scaleWidth = screenWidth.toFloat() / videoWidth
            val scaleHeight = screenHeight.toFloat() / videoHeight
            val scale = maxOf(scaleWidth, scaleHeight)

            val newWidth = (videoWidth * scale).toInt()
            val newHeight = (videoHeight * scale).toInt()

            // 设置 VideoView 为放大后的尺寸，并居中，超出部分被容器裁剪
            val layoutParams = videoView.layoutParams as FrameLayout.LayoutParams
            layoutParams.width = newWidth
            layoutParams.height = newHeight
            layoutParams.gravity = android.view.Gravity.CENTER
            videoView.layoutParams = layoutParams

            // 关闭循环播放（视频默认播完就停）
            mp.isLooping = false
        }

        videoView.setOnCompletionListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        videoView.start()
    }
}
