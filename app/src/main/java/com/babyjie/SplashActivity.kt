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
        videoView.setResizeModeFit()

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        // 初始显示“10”，倒计时从10开始读秒
        tvSkip.text = "10"
        tvSkip.visibility = View.VISIBLE
        // 点击事件先不设置，到5秒时才可点击

        // 启动倒计时
        startCountdown(tvSkip)
    }

    private fun startCountdown(textView: TextView) {
        countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                if (seconds > 5) {
                    // 前5秒：显示数字，不可点击
                    textView.text = seconds.toString()
                    textView.setOnClickListener(null) // 移除点击
                    textView.visibility = View.VISIBLE
                } else if (seconds in 1..5) {
                    // 后5秒：显示“跳过”，可点击
                    textView.text = "跳过"
                    textView.setOnClickListener {
                        jumpToMain()
                    }
                    textView.visibility = View.VISIBLE
                }
            }

            override fun onFinish() {
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
