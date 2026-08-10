package com.babyjie

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var hasJumped = false
    private var videoDurationMs: Long = 10000L

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
        val tvBrand = findViewById<TextView>(R.id.tvBrand)

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()  // 或 Fill，按你的设置

        // 视频准备好后获取时长并启动倒计时
        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        // 品牌文字淡入动画（1秒后出现，持续1秒）
        tvBrand.postDelayed({
            ObjectAnimator.ofFloat(tvBrand, "alpha", 0f, 1f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 1000)

        // 跳过按钮初始显示
        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener(null)
    }

    private fun startCountdown(textView: TextView) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(videoDurationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val totalSeconds = videoDurationMs / 1000
                val showSkipAfter = totalSeconds - 5

                if (seconds > showSkipAfter) {
                    textView.text = seconds.toString()
                    textView.setOnClickListener(null)
                    // 数字变化时轻微缩放动画
                    pulseAnimation(textView)
                } else {
                    // 第一次变成“跳过”时，加一个弹性动画
                    if (textView.text != "跳过") {
                        textView.text = "跳过"
                        textView.setOnClickListener {
                            jumpToMain()
                        }
                        skipAppearAnimation(textView)
                    }
                }
            }

            override fun onFinish() {
                jumpToMain()
            }
        }.start()
    }

    private fun pulseAnimation(view: View) {
        val anim = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
        anim.duration = 200
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.start()
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun skipAppearAnimation(view: View) {
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
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
