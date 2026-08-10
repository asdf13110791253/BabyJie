package com.babyjie

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var hasJumped = false
    private var videoDurationMs: Long = 10000L

    private val letterViews = mutableListOf<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimationCancelled = false

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

        // 收集字母视图
        letterViews.add(findViewById(R.id.tvLetter1))
        letterViews.add(findViewById(R.id.tvLetter2))
        letterViews.add(findViewById(R.id.tvLetter3))
        letterViews.add(findViewById(R.id.tvLetter4))
        letterViews.add(findViewById(R.id.tvLetter5))
        letterViews.add(findViewById(R.id.tvLetter6))
        letterViews.add(findViewById(R.id.tvLetter7))

        // 关键步骤：给每个字母设置“外圈透明、内有颜色”的顶级特效
        for (letter in letterViews) {
            applyPremiumTextEffect(letter)
        }

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()

        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
            // 视频准备好后延迟 800ms 开始动画
            handler.postDelayed({
                startPremiumLetterAnimation()
            }, 800)
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener(null)
    }

    /**
     * 核心：打造“外圈透明、内有颜色”的顶级文字效果
     */
    private fun applyPremiumTextEffect(textView: TextView) {
        // 设置文字颜色为白色（内部颜色）
        textView.setTextColor(0xFFFFFFFF.toInt())

        // 设置多层阴影，制造“外圈透明发光”的感觉
        // 第一层：紧贴文字的深色阴影，增加对比度
        textView.setShadowLayer(2f, 0f, 0f, 0x55000000)
        
        // 注意：Android 原生只能设一层 shadow，但我们可以通过调整 radius 和颜色
        // 让它看起来像“外层透明光晕”
        // 这里在动画里会动态改变，所以初始设一个柔和的光晕
        textView.setShadowLayer(18f, 0f, 0f, 0x80FFFFFF)
    }

    /**
     * 顶级动画流程：依次出现 -> 停留 -> 依次消失
     */
    private fun startPremiumLetterAnimation() {
        if (isAnimationCancelled) return

        val appearInterval = 180L  // 每个字母出现间隔
        val stayDuration = 3000L   // 全部出现后停留多久
        val disappearInterval = 150L // 消失间隔

        // 1. 依次出现（淡入 + 轻微放大）
        for (i in letterViews.indices) {
            handler.postDelayed({
                if (isAnimationCancelled) return@postDelayed
                val letter = letterViews[i]
                letter.alpha = 0f
                letter.scaleX = 0.5f
                letter.scaleY = 0.5f
                letter.visibility = View.VISIBLE

                val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 0f, 1f)
                val scaleXAnim = ObjectAnimator.ofFloat(letter, "scaleX", 0.5f, 1f)
                val scaleYAnim = ObjectAnimator.ofFloat(letter, "scaleY", 0.5f, 1f)

                alphaAnim.interpolator = DecelerateInterpolator()
                scaleXAnim.interpolator = DecelerateInterpolator()
                scaleYAnim.interpolator = DecelerateInterpolator()

                alphaAnim.duration = 500
                scaleXAnim.duration = 500
                scaleYAnim.duration = 500

                alphaAnim.start()
                scaleXAnim.start()
                scaleYAnim.start()
            }, appearInterval * i)
        }

        // 2. 停留一段时间后，依次消失（淡出 + 轻微缩小并上浮）
        val disappearStartTime = appearInterval * letterViews.size + stayDuration
        handler.postDelayed({
            if (isAnimationCancelled) return@postDelayed
            for (i in letterViews.indices) {
                handler.postDelayed({
                    if (isAnimationCancelled) return@postDelayed
                    val letter = letterViews[i]
                    
                    val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 1f, 0f)
                    val scaleXAnim = ObjectAnimator.ofFloat(letter, "scaleX", 1f, 0.6f)
                    val scaleYAnim = ObjectAnimator.ofFloat(letter, "scaleY", 1f, 0.6f)
                    val transYAnim = ObjectAnimator.ofFloat(letter, "translationY", 0f, -30f)

                    alphaAnim.interpolator = DecelerateInterpolator()
                    scaleXAnim.interpolator = DecelerateInterpolator()
                    scaleYAnim.interpolator = DecelerateInterpolator()
                    transYAnim.interpolator = DecelerateInterpolator()

                    alphaAnim.duration = 400
                    scaleXAnim.duration = 400
                    scaleYAnim.duration = 400
                    transYAnim.duration = 400

                    alphaAnim.start()
                    scaleXAnim.start()
                    scaleYAnim.start()
                    transYAnim.start()
                }, disappearInterval * i)
            }
        }, disappearStartTime)
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
                    pulseAnimation(textView)
                } else {
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
        val animX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.03f, 1f)
        animX.duration = 300
        animX.interpolator = DecelerateInterpolator()
        animX.start()
        val animY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.03f, 1f)
        animY.duration = 300
        animY.interpolator = DecelerateInterpolator()
        animY.start()
    }

    private fun skipAppearAnimation(view: View) {
        view.scaleX = 0.6f
        view.scaleY = 0.6f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun jumpToMain() {
        if (hasJumped) return
        hasJumped = true
        isAnimationCancelled = true
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        isAnimationCancelled = true
        handler.removeCallbacksAndMessages(null)
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
