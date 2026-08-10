package com.babyjie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var hasJumped = false
    private var videoDurationMs: Long = 10000L  // 默认10秒，待视频准备好后更新

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
        val tvSkip = findViewById<TextView>(R.id.tvSkipCountdown)

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()  // 或 Fill，根据你的视频比例选择

        // 监听视频准备好，获取真实时长
        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            // 根据视频实际时长启动倒计时
            startCountdown(tvSkip)
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""  // 初始为空，待倒计时开始会更新
    }

    private fun startCountdown(textView: TextView) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(videoDurationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val totalSeconds = videoDurationMs / 1000
                val showSkipAfter = totalSeconds - 5  // 最后5秒显示“跳过”

                if (seconds > showSkipAfter) {
                    // 前部分显示倒计时数字（不包含最后5秒），且不可点击
                    textView.text = seconds.toString()
                    textView.setOnClickListener(null)
                } else {
                    // 最后5秒显示“跳过”，可点击
                    textView.text = "跳过"
                    textView.setOnClickListener {
                        jumpToMain()
                    }
                }
            }

            override fun onFinish() {
                // 倒计时结束（理论上视频也会同时结束），自动跳转
                jumpToMain()
            }
        }.start()
    }

    private fun jumpToMain() {
        if (hasJumped) return
        hasJumped = true
        countDownTimer?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
