package com.ladakh.drone.gcs.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView

@Composable
fun GcsDashboard(
    viewModel: GcsViewModel = hiltViewModel()
) {
    val telemetry by viewModel.telemetry.collectAsState()
    val activeTanks by viewModel.activeTanks.collectAsState()
    val activeTrucks by viewModel.activeTrucks.collectAsState()
    val activeHumans by viewModel.activeHumans.collectAsState()

    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Left Column: Live Video and Overlay Canvas (75% Width)
        Box(modifier = Modifier.weight(0.75f).fillMaxHeight()) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = viewModel.getPlayer()
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Detection Bounding Box Overlay Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                telemetry?.detections?.forEach { det ->
                    val bbox = det.bbox
                    if (bbox != null && bbox.size >= 4) {
                        val x1 = bbox[0]
                        val y1 = bbox[1]
                        val x2 = bbox[2]
                        val y2 = bbox[3]
                        val width = x2 - x1
                        val height = y2 - y1
                        
                        // Draw Bounding Box
                        drawRect(
                            color = Color.Green,
                            topLeft = Offset(x1.toFloat(), y1.toFloat()),
                            size = Size(width.toFloat(), height.toFloat()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        
                        // Draw Label
                        val confidenceText = det.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                        val classNameText = det.class_name ?: "Unknown"
                        val trackIdText = det.track_id?.let { "#$it" } ?: ""
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            "$classNameText $trackIdText ($confidenceText)",
                            x1.toFloat(),
                            (y1 - 10).toFloat(),
                            Paint().apply {
                                color = android.graphics.Color.GREEN
                                textSize = 30f
                            }
                        )
                    }
                }
            }
        }

        // Right Column: Side Control Panel (25% Width)
        Column(
            modifier = Modifier.weight(0.25f).fillMaxHeight().background(Color.DarkGray).padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Tactical Counters", color = Color.White, style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(10.dp))
                CounterRow("Military Trucks", activeTrucks, Color.Yellow)
                CounterRow("Tanks", activeTanks, Color.Red)
                CounterRow("Humans", activeHumans, Color.Cyan)
            }
            
            // Connection Controls
            Column {
                Button(
                    onClick = { 
                        viewModel.connectStream(
                            "rtsp://127.0.0.1:8554/drone", 
                            "127.0.0.1",
                            5005
                        ) 
                    }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Stream")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.disconnectStream() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
fun CounterRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White)
        Text(count.toString(), color = color, style = MaterialTheme.typography.subtitle1)
    }
}
