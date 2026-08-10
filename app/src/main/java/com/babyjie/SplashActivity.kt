package com.babyjie

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var hasJumped = false
    private var videoDurationMs: Long = 10000L

    private val letterViews = mutableListOf<TextView>()
    private var masterAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸
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

        // 收集字母视图（你的布局里必须有这 7 个 id）
        letterViews.add(findViewById(R.id.tvLetter1))
        letterViews.add(findViewById(R.id.tvLetter2))
        letterViews.add(findViewById(R.id.tvLetter3))
        letterViews.add(findViewById(R.id.tvLetter4))
        letterViews.add(findViewById(R.id.tvLetter5))
        letterViews.add(findViewById(R.id.tvLetter6))
        letterViews.add(findViewById(R.id.tvLetter7))

        // 初始化字母状态：隐藏并设置顶级发光效果
        for (letter in letterViews) {
            letter.apply {
                alpha = 0f
                scaleX = 0.0f
                scaleY = 0.0f
                visibility = View.VISIBLE  // 可见，但透明
                // 设置多层阴影制造霓虹光晕
                setShadowLayer(20f, 0f, 0f, Color.parseColor("#80FF6EC7")) // 外层粉色光晕
                // 注意：Android 原生只支持一层阴影，但我们可以通过代码动态添加第二层，这里用外层足以
            }
        }

        // 视频设置
        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()  // 全屏

        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
            // 视频准备好后 800 毫秒开始品牌动画
            startPremiumLetterAnimation(800L)
        }

        videoView.setOnCompletionListener { jumpToMain() }

        videoView.start()

        // 跳过按钮初始化
        tvSkip.apply {
            visibility = View.VISIBLE
            text = ""
            setOnClickListener(null)
        }
    }

    /**
     * 顶级品牌字母动画：依次弹入 → 呼吸发光 → 依次淡出消失
     * @param startDelayMs 延迟开始时间 (毫秒)
     */
    private fun startPremiumLetterAnimation(startDelayMs: Long) {
        val appearInterval = 160L        // 每个字母出现间隔
        val appearDuration = 600L        // 出现动画时长
        val stayDuration = 2500L         // 停留/呼吸时间
        val disappearInterval = 140L     // 消失间隔
        val disappearDuration = 500L     // 消失动画时长

        // ---- 1. 出现动画：依次弹入 ----
        val appearSets = letterViews.mapIndexed { index, letter ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(letter, "alpha", 0f, 1f).apply { duration = appearDuration },
                    ObjectAnimator.ofFloat(letter, "scaleX", 0.0f, 1f).apply {
                        duration = appearDuration
                        interpolator = OvershootInterpolator(1.2f)  // 轻微弹簧
                    },
                    ObjectAnimator.ofFloat(letter, "scaleY", 0.0f, 1f).apply {
                        duration = appearDuration
                        interpolator = OvershootInterpolator(1.2f)
                    }
                )
                startDelay = appearInterval * index  // 依次出现
            }
        }.toSet() // 转为 Set 以使用 playTogether

        val appearSet = AnimatorSet().apply { playTogether(appearSets) }

        // ---- 2. 呼吸发光动画（在停留期间循环） ----
        val breathAnimator = ValueAnimator.ofFloat(20f, 35f, 20f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                for (letter in letterViews) {
                    letter.setShadowLayer(radius, 0f, 0f, Color.parseColor("#80FF6EC7"))
                }
            }
        }

        // 停留阶段：呼吸动画 + 固定时长
        val stayAnim = AnimatorSet().apply {
            play(breathAnimator)
            this.duration = stayDuration
        }

        // ---- 3. 消失动画：依次淡出 + 上浮 ----
        val disappearSets = letterViews.mapIndexed { index, letter ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(letter, "alpha", 1f, 0f).apply { duration = disappearDuration },
                    ObjectAnimator.ofFloat(letter, "translationY", 0f, -25f).apply { duration = disappearDuration }
                )
                startDelay = disappearInterval * index
            }
        }.toSet()

        val disappearSet = AnimatorSet().apply { playTogether(disappearSets) }

        // ---- 4. 总时间轴：顺序执行 ----
        masterAnimator = AnimatorSet().apply {
            playSequentially(appearSet, stayAnim, disappearSet)
            this.startDelay = startDelayMs
            start()
        }
    }

    // ========== 倒计时 ==========
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
                    // 轻微脉冲
                    ObjectAnimator.ofFloat(textView, "scaleX", 1f, 1.05f, 1f).apply { duration = 200; start() }
                    ObjectAnimator.ofFloat(textView, "scaleY", 1f, 1.05f, 1f).apply { duration = 200; start() }
                } else {
                    if (textView.text != "跳过") {
                        textView.text = "跳过"
                        textView.setOnClickListener { jumpToMain() }
                        // 弹性出现
                        textView.scaleX = 0.5f
                        textView.scaleY = 0.5f
                        textView.animate().scaleX(1f).scaleY(1f).setDuration(350)
                            .setInterpolator(OvershootInterpolator(0.8f)).start()
                    }
                }
            }

            override fun onFinish() { jumpToMain() }
        }.start()
    }

    private fun jumpToMain() {
        if (hasJumped) return
        hasJumped = true
        countDownTimer?.cancel()
        masterAnimator?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        masterAnimator?.cancel()
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
