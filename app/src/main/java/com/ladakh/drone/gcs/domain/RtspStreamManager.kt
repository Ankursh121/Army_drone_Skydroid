package com.ladakh.drone.gcs.domain

import android.content.Context
import androidx.media3.common.MediaItem
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

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        return exoPlayer!!
    }

    fun connect(rtspUrl: String) {
        _streamStatus.value = StreamStatus.Connecting
        try {
            val player = getPlayer()
            // Force RTSP to transport over TCP to keep video packet delivery stable 
            // over long-range tactical wireless telemetry links.
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(rtspUrl))
                
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
            _streamStatus.value = StreamStatus.Connected
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
