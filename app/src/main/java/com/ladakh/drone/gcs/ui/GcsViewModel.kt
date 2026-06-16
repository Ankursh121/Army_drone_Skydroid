package com.ladakh.drone.gcs.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.ladakh.drone.gcs.data.EventLog
import com.ladakh.drone.gcs.data.EventLogDao
import com.ladakh.drone.gcs.data.SettingsManager
import com.ladakh.drone.gcs.domain.ObjectTracker
import com.ladakh.drone.gcs.domain.RtspStreamManager
import com.ladakh.drone.gcs.domain.StreamStatus
import com.ladakh.drone.gcs.domain.YoloDetector
import com.ladakh.drone.gcs.network.Detection
import com.ladakh.drone.gcs.network.TelemetryPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AppScreen {
    Input,
    Live
}

@HiltViewModel
class GcsViewModel @Inject constructor(
    private val streamManager: RtspStreamManager,
    private val yoloDetector: YoloDetector,
    private val eventLogDao: EventLogDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Persistent RTSP URL
    val savedRtspUrl: StateFlow<String> = settingsManager.rtspUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "rtsp://username:password@192.168.1.100:554/live")

    // Screen State Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.Input)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Stream status from ExoPlayer manager
    val streamStatus: StateFlow<StreamStatus> = streamManager.streamStatus

    // Local telemetry state flow updated by the inference loop
    private val _telemetry = MutableStateFlow<TelemetryPayload?>(null)
    val telemetry: StateFlow<TelemetryPayload?> = _telemetry.asStateFlow()

    // Counter helpers derived from local telemetry flow
    val activeHumans = MutableStateFlow(0)
    val activeTanks = MutableStateFlow(0)
    val activeTrucks = MutableStateFlow(0)

    private val objectTracker = ObjectTracker()
    private var inferenceJob: Job? = null

    init {
        // Observe telemetry to update counts reactively
        viewModelScope.launch {
            telemetry.collect { payload ->
                val detections = payload?.detections ?: emptyList()
                activeHumans.value = detections.count { it.class_name?.lowercase() == "human" }
                activeTanks.value = detections.count { it.class_name?.lowercase() == "tank" }
                activeTrucks.value = detections.count { it.class_name?.lowercase() == "military truck" }
            }
        }
    }

    fun getPlayer(): ExoPlayer = streamManager.getPlayer()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun saveRtspUrl(url: String) {
        viewModelScope.launch {
            settingsManager.saveRtspUrl(url)
        }
    }

    fun connectStream(rtspUrl: String) {
        viewModelScope.launch {
            streamManager.connect(rtspUrl)
            _currentScreen.value = AppScreen.Live
        }
    }

    fun disconnectStream() {
        stopInferenceLoop()
        streamManager.disconnect()
        _telemetry.value = null
        _currentScreen.value = AppScreen.Input
    }

    /**
     * Starts the inference loop grabbing frames from the video surface
     */
    fun startInferenceLoop(getBitmap: () -> Bitmap?) {
        if (inferenceJob != null) return // Already running
        
        inferenceJob = viewModelScope.launch(Dispatchers.Default) {
            android.util.Log.i("GcsViewModel", "Starting inference loop...")
            var lastFrameTime = System.currentTimeMillis()
            
            while (inferenceJob?.isActive == true) {
                val bitmap = getBitmap()
                if (bitmap != null) {
                    val startTime = System.currentTimeMillis()
                    
                    // Run ONNX detection
                    val rawDetections = yoloDetector.detect(bitmap)
                    
                    // Match detections to track IDs
                    val trackedDetections = objectTracker.update(rawDetections)
                    
                    val endTime = System.currentTimeMillis()
                    val latency = endTime - startTime
                    val fps = 1000.0 / maxOf(1L, endTime - lastFrameTime).toDouble()
                    lastFrameTime = endTime
                    
                    // Update telemetry state
                    _telemetry.value = TelemetryPayload(
                        timestamp = endTime.toString(),
                        fps_current = fps,
                        latency_ms = latency.toDouble(),
                        detections = trackedDetections
                    )
                    
                    // Log detection events to database if confidence is high
                    trackedDetections.forEach { det ->
                        if ((det.confidence ?: 0.0) > 0.65 && det.track_id != null) {
                            logDetectionEvent(det.class_name ?: "unknown", det.confidence ?: 0.0, det.track_id)
                        }
                    }
                }
                
                // Adaptive delay: wait ~33ms (targeting ~30 FPS), adjusting dynamically
                delay(30)
            }
        }
    }

    fun stopInferenceLoop() {
        inferenceJob?.cancel()
        inferenceJob = null
        android.util.Log.i("GcsViewModel", "Stopped inference loop.")
    }

    private fun logDetectionEvent(objectType: String, confidence: Double, trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
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
