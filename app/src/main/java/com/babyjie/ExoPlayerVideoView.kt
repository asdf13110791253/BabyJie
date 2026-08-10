package com.babyjie

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView

class ExoPlayerVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var player: ExoPlayer? = null
    private val playerView: PlayerView = PlayerView(context)
    private var onCompletionListener: (() -> Unit)? = null

    init {
        // PlayerView 默认会带播放控件，我们隐藏掉，让它只显示视频
        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 默认填满屏幕，不裁剪内容
        playerView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(playerView)
    }

    /**
     * 设置视频 URI（支持本地 raw 资源或网络链接）
     */
    fun setVideoURI(uri: Uri) {
        player?.release()
        player = ExoPlayer.Builder(context).build().also { exoPlayer ->
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onCompletionListener?.invoke()
                    }
                }
            })
            playerView.player = exoPlayer
        }
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    fun start() {
        player?.playWhenReady = true
    }

    fun setResizeModeFit() {
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT  // 完整显示，有黑边
    }

    fun setResizeModeZoom() {
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 填满屏幕，可能裁切
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.release()
        player = null
    }
}
