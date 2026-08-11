package com.babyjie

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tableDetector = TableDetector()
    private val ballDetector = BallDetector()

    // 控制帧率：每帧间隔约 33ms 对应 30fps，实际可根据性能调整
    private val frameIntervalMs = 33L

    interface DetectionCallback {
        fun onTableDetected(isTablePresent: Boolean, ballPosition: BallPosition?)
    }

    companion object {
        var detectionCallback: DetectionCallback? = null

        fun newIntent(context: android.content.Context, resultCode: Int, data: Intent, orientation: String): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("orientation", orientation)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, data)

        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // 进一步降低分辨率以提升帧率
        val captureWidth = width / 3
        val captureHeight = height / 3

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        // 启动定时检测
        scheduleFrameProcessing()
    }

    private fun scheduleFrameProcessing() {
        handler.postDelayed({
            processLatestFrame()
            scheduleFrameProcessing() // 循环
        }, frameIntervalMs)
    }

    private fun processLatestFrame() {
        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    val hasTable = tableDetector.detectTable(bitmap)
                    var ballPos: BallPosition? = null
                    if (hasTable) {
                        ballPos = ballDetector.detectCueBall(bitmap, null)
                    }
                    detectionCallback?.onTableDetected(hasTable, ballPos)
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Frame processing error", e)
        } finally {
            image?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
