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
import android.view.Surface

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tableDetector = TableDetector()
    private val ballDetector = BallDetector()

    // 回调接口，用于通知悬浮窗检测结果
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
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // 降低分辨率以提升性能
        val captureWidth = width / 2
        val captureHeight = height / 2

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        // 1. 检测台球桌
                        val hasTable = tableDetector.detectTable(bitmap)
                        var ballPos: BallPosition? = null
                        if (hasTable) {
                            // 2. 如果检测到台球桌，再搜索白球（可传入识别区域）
                            ballPos = ballDetector.detectCueBall(bitmap, null) // 暂用全图，后续可传入区域
                        }
                        // 回调通知悬浮窗
                        detectionCallback?.onTableDetected(hasTable, ballPos)
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Frame processing error", e)
            } finally {
                image?.close()
            }
        }, handler)
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
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
