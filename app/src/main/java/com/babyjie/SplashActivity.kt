package com.babyjie

import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class SplashActivity : AppCompatActivity() {

    private var countDownTimer: CountDownTimer? = null
    private var hasJumped = false
    private var videoDurationMs: Long = 10000L

    private val letterViews = mutableListOf<TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimationCancelled = false

    private lateinit var glassLoadingText: GlassLoadingTextView
    private lateinit var disclaimerOverlay: View
    private lateinit var videoView: ExoPlayerVideoView
    private lateinit var tvSkip: TextView
    private lateinit var btnContactDev: TextView
    private lateinit var btnAgreeEnter: TextView

    private var wechatLaunched = false

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

        videoView = findViewById(R.id.exoPlayerVideoView)
        tvSkip = findViewById(R.id.tvSkipCountdown)

        letterViews.add(findViewById(R.id.tvLetter1))
        letterViews.add(findViewById(R.id.tvLetter2))
        letterViews.add(findViewById(R.id.tvLetter3))
        letterViews.add(findViewById(R.id.tvLetter4))
        letterViews.add(findViewById(R.id.tvLetter5))
        letterViews.add(findViewById(R.id.tvLetter6))
        letterViews.add(findViewById(R.id.tvLetter7))

        glassLoadingText = findViewById(R.id.glassLoadingText)
        disclaimerOverlay = findViewById(R.id.disclaimerOverlay)
        btnContactDev = findViewById(R.id.btnContactDev)
        btnAgreeEnter = findViewById(R.id.btnAgreeEnter)

        // “同意并进入”按钮 → 停止视频 → 进入主界面
        btnAgreeEnter.setOnClickListener {
            videoView.stop()
            jumpToMain()
        }

        // “联系开发者”按钮 → 停止视频 → 保存二维码 → 跳转微信
        btnContactDev.setOnClickListener {
            videoView.stop()
            saveQrToGallery()
        }

        val videoPath = "android.resource://${packageName}/${R.raw.babyjielogo}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setResizeModeZoom()

        videoView.setOnPreparedListener { durationMs ->
            videoDurationMs = durationMs
            startCountdown(tvSkip)
            startPremiumLetterAnimation()
            glassLoadingText.startLoading(5000L)
        }

        videoView.setOnCompletionListener {
            videoView.stop()
            showDisclaimer()
        }

        videoView.start()

        tvSkip.visibility = View.VISIBLE
        tvSkip.text = ""
        tvSkip.setOnClickListener {
            // 点击跳过：停止视频，停止倒计时，显示免责声明
            videoView.stop()
            countDownTimer?.cancel()
            showDisclaimer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wechatLaunched && !hasJumped) {
            jumpToMain()
        }
    }

    private fun showDisclaimer() {
        if (disclaimerOverlay.visibility == View.VISIBLE) return
        disclaimerOverlay.visibility = View.VISIBLE
    }

    private fun saveQrToGallery() {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.qrcode_wechat)
            val filename = "BabyJie_WeChat_${System.currentTimeMillis()}.jpg"
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
                uri != null
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(file)
                sendBroadcast(mediaScanIntent)
                true
            }
            if (saved) {
                Toast.makeText(this, "二维码已保存到相册，即将跳转微信", Toast.LENGTH_SHORT).show()
                btnContactDev.text = "已保存"
                btnContactDev.isEnabled = false
                launchWechat()
            } else {
                Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchWechat() {
        wechatLaunched = true
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://"))
            startActivity(intent)
        } catch (e: Exception) {
            jumpToMain()
        }
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

    // 品牌字母动画（保持不变）
    private fun startPremiumLetterAnimation() {
        if (isAnimationCancelled) return

        val appearInterval = 180L
        val stayDuration = 3000L
        val disappearInterval = 150L

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
                            videoView.stop()
                            countDownTimer?.cancel()
                            showDisclaimer()
                        }
                        skipAppearAnimation(textView)
                    }
                }
            }

            override fun onFinish() {
                videoView.stop()
                showDisclaimer()
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

    override fun onDestroy() {
        videoView.stop()
        isAnimationCancelled = true
        handler.removeCallbacksAndMessages(null)
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
