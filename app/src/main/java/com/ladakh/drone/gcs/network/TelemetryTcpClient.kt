package com.ladakh.drone.gcs.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class Bbox(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

data class Detection(
    val track_id: Int?,
    val class_name: String?,
    val confidence: Double?,
    val bbox: List<Int>?
)

data class TelemetryPayload(
    val timestamp: String?,
    val fps_current: Double?,
    val latency_ms: Double?,
    val detections: List<Detection>?
)

@Singleton
class TelemetryTcpClient @Inject constructor(
    private val gson: Gson
) {
    private val _telemetryFlow = MutableSharedFlow<TelemetryPayload>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val telemetryFlow: SharedFlow<TelemetryPayload> = _telemetryFlow
    private var socket: Socket? = null
    private var isConnected = false

    suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            isConnected = true
            val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            
            while (isConnected) {
                val line = reader.readLine() ?: break
                try {
                    val payload = gson.fromJson(line, TelemetryPayload::class.java)
                    _telemetryFlow.tryEmit(payload)
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("TelemetryTcpClient", "Failed to parse telemetry line: $line", e)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TelemetryTcpClient", "Failed to connect to $host:$port", e)
        } finally {
            disconnect()
        }
    }

    fun disconnect() {
        isConnected = false
        socket?.close()
        socket = null
    }
}
