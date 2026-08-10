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
import android.view.animation.AccelerateDecelerateInterpolator
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

        // 收集字母视图
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
            startTopBrandAnimation()
        }

        videoView.setOnCompletionListener {
            jumpToMain()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener(null)
    }

    private fun startTopBrandAnimation() {
        if (brandAnimSet != null) return

        val appearSet = AnimatorSet()
        val appearAnimators = mutableListOf<Animator>()

        // 逐个字母弹跳出现 (弹性过冲)
        for (i in letterViews.indices) {
            val letter = letterViews[i]
            letter.alpha = 0f
            letter.scaleX = 0.3f
            letter.scaleY = 0.3f

            val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 0f, 1f)
            val scaleXAnim = ObjectAnimator.ofFloat(letter, "scaleX", 0.3f, 1.3f, 1f)
            val scaleYAnim = ObjectAnimator.ofFloat(letter, "scaleY", 0.3f, 1.3f, 1f)

            scaleXAnim.interpolator = OvershootInterpolator(2.5f)
            scaleYAnim.interpolator = OvershootInterpolator(2.5f)

            val letterSet = AnimatorSet()
            letterSet.playTogether(alphaAnim, scaleXAnim, scaleYAnim)
            letterSet.startDelay = (i * 150L) // 每个字母间隔150ms，更流畅
            appearAnimators.add(letterSet)
        }

        appearSet.playSequentially(appearAnimators) // 实际上这里是顺序的，但用playSequentially控制
        // 注意：playSequentially 会使每个动画在前一个结束后执行，我们需要他们依次启动，但可以重叠一点。
        // 更好的方式：使用 startDelay 并同时播放，但把延迟累加到每个动画。
        // 上面已经设置了 startDelay，所以应该 playTogether 才能按延迟依次启动
        appearSet.playTogether(appearAnimators)

        // 所有字母出现后，开始呼吸发光动画
        val breathAnimator = ValueAnimator.ofFloat(8f, 22f, 8f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                for (letter in letterViews) {
                    letter.setShadowLayer(radius, 0f, 0f, letter.shadowColor)
                }
            }
        }

        // 出场动画：逆序缩小+淡出
        val disappearSet = AnimatorSet()
        val disappearAnimators = mutableListOf<Animator>()
        for (i in letterViews.size - 1 downTo 0) {
            val letter = letterViews[i]
            val alphaAnim = ObjectAnimator.ofFloat(letter, "alpha", 1f, 0f)
            val scaleXAnim = ObjectAnimator.ofFloat(letter, "scaleX", 1f, 0.3f)
            val scaleYAnim = ObjectAnimator.ofFloat(letter, "scaleY", 1f, 0.3f)

            val letterDisappear = AnimatorSet()
            letterDisappear.playTogether(alphaAnim, scaleXAnim, scaleYAnim)
            letterDisappear.startDelay = ((letterViews.size - 1 - i) * 150L)
            disappearAnimators.add(letterDisappear)
        }
        disappearSet.playTogether(disappearAnimators)

        // 总控顺序：出现 -> 开始呼吸 (延时 3.5 秒后开始消失，可调整)
        brandAnimSet = AnimatorSet()
        brandAnimSet!!.playSequentially(
            appearSet,
            AnimatorSet().apply {
                play(breathAnimator).after(200) // 出现后延迟200ms开始呼吸
                duration = 3500 // 呼吸持续3.5秒
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
        val animX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
        animX.duration = 200
        animX.interpolator = AccelerateDecelerateInterpolator()
        animX.start()
        val animY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f)
        animY.duration = 200
        animY.interpolator = AccelerateDecelerateInterpolator()
        animY.start()
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
