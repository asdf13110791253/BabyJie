package com.babyjie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_splash)

        val videoView = findViewById<ExoPlayerVideoView>(R.id.exoPlayerVideoView)
        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))

        // 完整显示，不放大，不裁切，背景色填充空白区域
        videoView.setResizeModeFit()

        videoView.setOnCompletionListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        videoView.start()
    }
}
