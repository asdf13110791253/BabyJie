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
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class SplashActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("opencv_java4")
        }
    }

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
    private lateinit var tvAntiAddiction: TextView

    private var wechatLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求忽略电池优化（防止后台被杀）
        requestBatteryOptimization()

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
        tvAntiAddiction = findViewById(R.id.tvAntiAddiction)

        tvAntiAddiction.visibility = View.VISIBLE

        btnAgreeEnter.setOnClickListener {
            videoView.stop()
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }

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
            videoView.stop()
            countDownTimer?.cancel()
            showDisclaimer()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (wechatLaunched && !hasJumped) {
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }
    }

    private fun showDisclaimer() {
        if (disclaimerOverlay.visibility == View.VISIBLE) return
        disclaimerOverlay.visibility = View.VISIBLE
        tvAntiAddiction.visibility = View.GONE
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
                uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
                uri != null
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
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
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("weixin://")))
        } catch (e: Exception) {
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }
    }

    private fun startPremiumLetterAnimation() { /* 保持不变 */ }
    private fun startCountdown(textView: TextView) { /* 保持不变 */ }
    private fun pulseAnimation(view: View) { /* 保持不变 */ }
    private fun skipAppearAnimation(view: View) { /* 保持不变 */ }

    override fun onDestroy() {
        videoView.stop()
        isAnimationCancelled = true
        handler.removeCallbacksAndMessages(null)
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
