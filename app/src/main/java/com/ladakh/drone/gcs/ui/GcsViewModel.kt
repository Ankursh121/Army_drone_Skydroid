package com.ladakh.drone.gcs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.ladakh.drone.gcs.data.EventLog
import com.ladakh.drone.gcs.data.EventLogDao
import com.ladakh.drone.gcs.domain.RtspStreamManager
import com.ladakh.drone.gcs.domain.StreamStatus
import com.ladakh.drone.gcs.network.TelemetryTcpClient
import com.ladakh.drone.gcs.network.TelemetryPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GcsViewModel @Inject constructor(
    private val streamManager: RtspStreamManager,
    private val telemetryClient: TelemetryTcpClient,
    private val eventLogDao: EventLogDao
) : ViewModel() {

    val streamStatus: StateFlow<StreamStatus> = streamManager.streamStatus
    val telemetry: StateFlow<TelemetryPayload?> = telemetryClient.telemetryFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Reactive streams mapping detections lists to individual tactical UI counts
    val activeHumans: StateFlow<Int> = telemetry.map { payload ->
        payload?.detections?.count { it.class_name?.lowercase() == "human" } ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val activeTanks: StateFlow<Int> = telemetry.map { payload ->
        payload?.detections?.count { it.class_name?.lowercase() == "tank" } ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val activeTrucks: StateFlow<Int> = telemetry.map { payload ->
        payload?.detections?.count { it.class_name?.lowercase() == "military truck" } ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun getPlayer(): ExoPlayer = streamManager.getPlayer()

    fun connectStream(rtspUrl: String, host: String, port: Int) {
        viewModelScope.launch {
            streamManager.connect(rtspUrl)
        }
        viewModelScope.launch {
            telemetryClient.connect(host, port)
        }
    }

    fun disconnectStream() {
        streamManager.disconnect()
        telemetryClient.disconnect()
    }

    fun logDetectionEvent(objectType: String, confidence: Double, trackId: Int) {
        viewModelScope.launch {
            eventLogDao.insertLog(
                EventLog(
                    timestamp = System.currentTimeMillis(),
                    objectType = objectType,
                    confidence = confidence,
                    trackId = trackId
                )
            )
        }
    }
}
