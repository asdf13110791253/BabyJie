package com.babyjie

import android.content.Context
import android.graphics.Matrix
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout

class FullScreenVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var textureView: TextureView = TextureView(context)
    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var onCompletionListener: (() -> Unit)? = null

    init {
        textureView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                prepareVideo(surface)
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
    }

    fun setVideoURI(uri: Uri) {
        videoUri = uri
        if (textureView.isAvailable) {
            prepareVideo(textureView.surfaceTexture!!)
        }
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    fun start() {
        mediaPlayer?.start()
    }

    private fun prepareVideo(surface: android.graphics.SurfaceTexture) {
        val uri = videoUri ?: return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setSurface(Surface(surface))
            setOnPreparedListener { mp ->
                mp.isLooping = false
                updateTextureViewScale(mp.videoWidth, mp.videoHeight)
                mp.start()
            }
            setOnCompletionListener {
                onCompletionListener?.invoke()
            }
            prepareAsync()
        }
    }

    private fun updateTextureViewScale(videoWidth: Int, videoHeight: Int) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth == 0 || viewHeight == 0 || videoWidth == 0 || videoHeight == 0) return

        val matrix = Matrix()
        val scaleX = viewWidth.toFloat() / videoWidth
        val scaleY = viewHeight.toFloat() / videoHeight
        val scale = minOf(scaleX, scaleY)   // 这里改成 minOf，完整显示不裁剪

        matrix.setScale(scale, scale, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
