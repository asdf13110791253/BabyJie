package com.babyjie

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: FloatingOverlayView

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val tableDetector = TableDetector()
    private val ballDetector = BallDetector()

    private val isProcessing = AtomicBoolean(false)
    private var lastProcessTime = 0L
    private val minIntervalMs = 200L

    private lateinit var captureThread: android.os.HandlerThread
    private lateinit var captureHandler: android.os.Handler

    interface DetectionCallback {
        fun onTableDetected(isTablePresent: Boolean, ballPosition: BallPosition?)
    }

    companion object {
        var detectionCallback: DetectionCallback? = null

        fun newIntent(context: Context, resultCode: Int, data: Intent, orientation: String): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("orientation", orientation)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        captureThread = android.os.HandlerThread("CaptureThread").apply { start() }
        captureHandler = android.os.Handler(captureThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, buildNotification())
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e("ScreenCapture", "缺少录屏授权数据")
            stopSelf()
            return START_NOT_STICKY
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Log.i("ScreenCapture", "MediaProjection 已停止") }
        }, null)

        addOverlay()
        startCapture()
        return START_STICKY
    }

    private fun addOverlay() {
        overlayView = FloatingOverlayView(this)
        windowManager.addView(overlayView, FloatingOverlayView.createLayoutParams())
    }

    private fun startCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BabyJieCapture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        scheduleFrameProcessing()
    }

    private fun scheduleFrameProcessing() {
        captureHandler.postDelayed({
            processLatestFrame()
            scheduleFrameProcessing()
        }, minIntervalMs)
    }

    private fun processLatestFrame() {
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < minIntervalMs || isProcessing.get()) return

        val image = imageReader?.acquireLatestImage() ?: return
        isProcessing.set(true)
        lastProcessTime = now

        try {
            val bitmap = imageToBitmap(image)
            image.close()

            val hasTable = tableDetector.detectTable(bitmap)
            var ballPos: BallPosition? = null
            if (hasTable) ballPos = ballDetector.detectCueBall(bitmap, null)

            if (ballPos != null) {
                val center = android.graphics.PointF(ballPos.x, ballPos.y)
                val aimEnd = android.graphics.PointF(screenWidth / 2f, screenHeight / 2f)
                overlayView.updateData(null, center, 25f, Pair(center, aimEnd))
            } else {
                overlayView.updateData(null, null, 0f, null)
            }

            detectionCallback?.onTableDetected(hasTable, ballPos)
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("ScreenCapture", "帧处理出错", e)
        } finally {
            isProcessing.set(false)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun buildNotification(): Notification {
        val channelId = "screen_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "录屏服务", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("台球辅助运行中")
            .setContentText("录屏与识别已开启")
            .setSmallIcon(R.drawable.ic_launcher_temp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        if (::captureThread.isInitialized) captureThread.quitSafely()
    }
}
