package com.babyjie

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
    private var brandAnimSet: AnimatorSet? = null

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

        letterViews.add(findViewById(R.id.tvLetter1))
        letterViews.add(findViewById(R.id.tvLetter2))
        letterViews.add(findViewById(R.id.tvLetter3))
        letterViews.add(findViewById(R.id.tvLetter4))
        letterViews.add(findViewById(R.id.tvLetter5))
        letterViews.add(findViewById(R.id.tvLetter6))
        letterViews.add(findViewById(R.id.tvLetter7))

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()

        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
            startRefinedAnimation()
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener(null)
    }

    private fun startRefinedAnimation() {
        if (brandAnimSet != null) return

        val appearAnimators = mutableListOf<Animator>()

        // 1. 打字机式出现：每个字母单独淡入，轻微上浮
        for (i in letterViews.indices) {
            val letter = letterViews[i]
            letter.alpha = 0f
            letter.translationY = 15f // 初始向下偏移一点

            val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 0f, 1f)
            val transYAnim = ObjectAnimator.ofFloat(letter, "translationY", 15f, 0f)

            alphaAnim.interpolator = DecelerateInterpolator()
            transYAnim.interpolator = DecelerateInterpolator()

            val letterSet = AnimatorSet()
            letterSet.playTogether(alphaAnim, transYAnim)
            letterSet.duration = 600
            letterSet.startDelay = (i * 150L) // 打字机节奏
            appearAnimators.add(letterSet)
        }

        val appearSet = AnimatorSet()
        appearSet.playTogether(appearAnimators)

        // 2. 精致呼吸发光：金色光晕在 8px - 20px 间缓慢变化
        val breathAnimator = ValueAnimator.ofFloat(8f, 20f, 8f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                for (letter in letterViews) {
                    // 使用更高级的阴影颜色：半透明白色
                    letter.setShadowLayer(radius, 0f, 0f, 0x40FFFFFF)
                }
            }
        }

        // 3. 优雅消失：按顺序淡出，并有轻微下移
        val disappearAnimators = mutableListOf<Animator>()
        for (i in letterViews.indices) {
            val letter = letterViews[i]
            val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 1f, 0f)
            val transYAnim = ObjectAnimator.ofFloat(letter, "translationY", 0f, -15f) // 轻微上浮消失

            val letterDisappear = AnimatorSet()
            letterDisappear.playTogether(alphaAnim, transYAnim)
            letterDisappear.duration = 500
            letterDisappear.startDelay = (i * 120L) // 正序消失
            disappearAnimators.add(letterDisappear)
        }

        val disappearSet = AnimatorSet()
        disappearSet.playTogether(disappearAnimators)

        // 总控：出现 -> 停留呼吸 3 秒 -> 消失
        brandAnimSet = AnimatorSet()
        brandAnimSet!!.playSequentially(
            appearSet,
            AnimatorSet().apply {
                play(breathAnimator).after(200)
                duration = 3000
            },
            disappearSet
        )
        brandAnimSet!!.start()
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
        countDownTimer?.cancel()
        brandAnimSet?.cancel()
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        brandAnimSet?.cancel()
        handler.removeCallbacksAndMessages(null)
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
