package com.babyjie

import android.animation.ObjectAnimator
import android.content.Intent
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

    private lateinit var glassLoadingText: GlassLoadingTextView

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

        // 收集字母视图（用于之前的品牌动画，如果不需要可以删除相关代码）
        letterViews.add(findViewById(R.id.tvLetter1))
        letterViews.add(findViewById(R.id.tvLetter2))
        letterViews.add(findViewById(R.id.tvLetter3))
        letterViews.add(findViewById(R.id.tvLetter4))
        letterViews.add(findViewById(R.id.tvLetter5))
        letterViews.add(findViewById(R.id.tvLetter6))
        letterViews.add(findViewById(R.id.tvLetter7))

        // 底部玻璃文字
        glassLoadingText = findViewById(R.id.glassLoadingText)

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()

        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
            // 视频准备好后启动品牌字母动画（如果还想要以前的动画）
            startPremiumLetterAnimation()
            // 启动底部加载条动画：5秒填满，然后自动消失
            glassLoadingText.startLoading(5000L) {
                // 所有字母消失后的回调，这里留空，由视频完成或跳过按钮触发跳转
            }
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener(null)
    }

    private fun startPremiumLetterAnimation() {
        if (isAnimationCancelled) return

        val appearInterval = 180L
        val stayDuration = 3000L
        val disappearInterval = 150L

        // 1. 依次出现（淡入 + 轻微放大）
        for (i in letterViews.indices) {
            val delay = appearInterval * i.toLong()
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
            }, delay)
        }

        // 2. 消失动画开始时间
        val disappearStartTime = appearInterval * letterViews.size.toLong() + stayDuration
        handler.postDelayed({
            if (isAnimationCancelled) return@postDelayed
            for (i in letterViews.indices) {
                val delay = disappearInterval * i.toLong()
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
                }, delay)
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
