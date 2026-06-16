package com.ladakh.drone.gcs.domain

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class StreamStatus {
    object Idle : StreamStatus()
    object Connecting : StreamStatus()
    object Connected : StreamStatus()
    data class Error(val message: String) : StreamStatus()
}

@Singleton
class RtspStreamManager @Inject constructor(
    private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val _streamStatus = MutableStateFlow<StreamStatus>(StreamStatus.Idle)
    val streamStatus: StateFlow<StreamStatus> = _streamStatus

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    _streamStatus.value = StreamStatus.Connected
                }
                Player.STATE_BUFFERING -> {
                    _streamStatus.value = StreamStatus.Connecting
                }
                Player.STATE_ENDED -> {
                    _streamStatus.value = StreamStatus.Idle
                }
                Player.STATE_IDLE -> {
                    // State idle can also mean disconnected after stop
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _streamStatus.value = StreamStatus.Error(error.localizedMessage ?: "RTSP Link Connection Failed")
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(playerListener)
            }
        }
        return exoPlayer!!
    }

    fun connect(rtspUrl: String) {
        // Strip any accidental spaces from URL input
        val cleanedUrl = rtspUrl.replace(" ", "")
        _streamStatus.value = StreamStatus.Connecting
        try {
            val player = getPlayer()
            // Force RTSP to transport over TCP to keep video packet delivery stable 
            // over long-range tactical wireless telemetry links.
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(cleanedUrl))
                
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _streamStatus.value = StreamStatus.Error(e.localizedMessage ?: "Failed to connect to RTSP")
        }
    }

    fun disconnect() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _streamStatus.value = StreamStatus.Idle
    }
}
