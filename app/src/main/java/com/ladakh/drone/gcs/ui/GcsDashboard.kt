package com.ladakh.drone.gcs.ui

import android.graphics.Bitmap
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.ladakh.drone.gcs.domain.StreamStatus
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.ladakh.drone.gcs.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Modern Tactical Cyberpunk Color Palette
val DarkBg = Color(0xFF0A0F14)
val PanelBg = Color(0xD0121C24)
val NeonCyan = Color(0xFF00E5FF)
val NeonRed = Color(0xFFFF3D00)
val NeonYellow = Color(0xFFFFB300)
val GridColor = Color(0xFF1E323E)

@Composable
fun GcsDashboard(
    viewModel: GcsViewModel = hiltViewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val savedUrl by viewModel.savedRtspUrl.collectAsState()

    MaterialTheme(
        colors = darkColors(
            primary = NeonCyan,
            background = DarkBg,
            surface = PanelBg
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            // Futuristic Grid Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStep = 80.dp.toPx()
                // Vertical lines
                var x = 0f
                while (x < size.width) {
                    drawLine(GridColor.copy(alpha = 0.2f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gridStep
                }
                // Horizontal lines
                var y = 0f
                while (y < size.height) {
                    drawLine(GridColor.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gridStep
                }
            }

            when (currentScreen) {
                AppScreen.Input -> RtspConfigScreen(
                    savedUrl = savedUrl,
                    onConnect = { url ->
                        viewModel.saveRtspUrl(url)
                        viewModel.connectStream(url)
                    },
                    onSaveOnly = { url ->
                        viewModel.saveRtspUrl(url)
                    }
                )
                AppScreen.Live -> LiveCameraScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RtspConfigScreen(
    savedUrl: String,
    onConnect: (String) -> Unit,
    onSaveOnly: (String) -> Unit
) {
    val context = LocalContext.current
    var urlText by remember(savedUrl) { mutableStateOf(savedUrl) }
    var errorMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(550.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelBg)
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Yudru Logo Image
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Yudru Logo",
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp)
            )

            // App Title
            Text(
                text = "YUDRU",
                color = NeonCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "LADAKH DRONE DETECTION SYSTEM",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Input Field
            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    errorMessage = ""
                },
                label = { Text("Tactical RTSP Stream Source URL", color = Color.Gray) },
                placeholder = { Text("rtsp://username:password@ip:port/stream", color = Color.DarkGray) },
                singleLine = true,
                isError = errorMessage.isNotEmpty(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    errorBorderColor = NeonRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = NeonRed,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 4.dp),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cleaned = urlText.replace(" ", "")
                        if (validateUrl(cleaned)) {
                            onSaveOnly(cleaned)
                            Toast.makeText(context, "URL Configuration Saved Locally", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = "Invalid RTSP URL format. Must start with rtsp://"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    modifier = Modifier.weight(1f),
                    border = ButtonDefaults.outlinedBorder.copy(brush = Brush.horizontalGradient(listOf(NeonCyan, NeonCyan)))
                ) {
                    Text("SAVE CONFIG", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = {
                        val cleaned = urlText.replace(" ", "")
                        if (validateUrl(cleaned)) {
                            onConnect(cleaned)
                        } else {
                            errorMessage = "Invalid RTSP URL format. Must start with rtsp://"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = NeonCyan, contentColor = DarkBg),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text("CONNECT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun LiveCameraScreen(viewModel: GcsViewModel) {
    val streamStatus by viewModel.streamStatus.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val activeHumans by viewModel.activeHumans.collectAsState()
    val activeTanks by viewModel.activeTanks.collectAsState()
    val activeTrucks by viewModel.activeTrucks.collectAsState()
    
    val context = LocalContext.current

    // Stop inference loop when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopInferenceLoop()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Area: Video Stream + Bounding Box Overlays
        Box(
            modifier = Modifier
                .weight(0.78f)
                .fillMaxHeight()
                .background(Color.Black)
        ) {
            // Live Video Feed via TextureView to support offline frame extraction
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        viewModel.getPlayer().setVideoTextureView(this)
                        
                        // Start inference frame extraction loop
                        viewModel.startInferenceLoop {
                            if (isAvailable) {
                                bitmap
                            } else {
                                null
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Bounding Box Drawing Canvas
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

                        // Color depending on class
                        val color = when (det.class_name?.lowercase()) {
                            "human" -> NeonCyan
                            "tank" -> NeonRed
                            "military truck" -> NeonYellow
                            else -> Color.Green
                        }

                        // Draw corner bounding boxes (glowing tactical boxes)
                        drawRect(
                            color = color,
                            topLeft = Offset(x1.toFloat(), y1.toFloat()),
                            size = Size(width.toFloat(), height.toFloat()),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Label info
                        val scoreText = det.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                        val classLabel = det.class_name?.replace("_", " ")?.uppercase() ?: "UNKNOWN"
                        val trackId = det.track_id?.let { "#$it" } ?: ""
                        val textToDraw = "$classLabel $trackId ($scoreText)"

                        drawContext.canvas.nativeCanvas.drawText(
                            textToDraw,
                            x1.toFloat(),
                            (y1 - 8).toFloat(),
                            android.graphics.Paint().apply {
                                this.color = color.value.toLong().toInt()
                                this.textSize = 28f
                                this.isFakeBoldText = true
                                this.typeface = android.graphics.Typeface.MONOSPACE
                            }
                        )
                    }
                }
            }

            // Top Status Overlay HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Connection Status Indicator
                HudBadge(
                    label = "STREAM STATUS",
                    value = when (streamStatus) {
                        is StreamStatus.Idle -> "IDLE"
                        is StreamStatus.Connecting -> "CONNECTING"
                        is StreamStatus.Connected -> "CONNECTED"
                        is StreamStatus.Error -> "DISCONNECTED"
                    },
                    color = when (streamStatus) {
                        is StreamStatus.Connected -> NeonCyan
                        is StreamStatus.Connecting -> NeonYellow
                        else -> NeonRed
                    }
                )

                // Error Details
                if (streamStatus is StreamStatus.Error) {
                    val errMsg = (streamStatus as StreamStatus.Error).message
                    HudBadge(
                        label = "ERROR DETAIL",
                        value = errMsg,
                        color = NeonRed
                    )
                }

                // AI Engine Indicator
                HudBadge(
                    label = "AI ENGINE",
                    value = if (telemetry != null) "ACTIVE" else "STANDBY",
                    color = if (telemetry != null) NeonCyan else Color.Gray
                )

                // Performance Specs
                telemetry?.let {
                    HudBadge(
                        label = "FPS",
                        value = String.format("%.1f", it.fps_current ?: 0.0),
                        color = NeonCyan
                    )
                    HudBadge(
                        label = "LATENCY",
                        value = "${(it.latency_ms ?: 0.0).toInt()}ms",
                        color = NeonCyan
                    )
                }
            }
        }

        // Right Panel: Tactical Information & Mission Controls
        Column(
            modifier = Modifier
                .weight(0.22f)
                .fillMaxHeight()
                .background(PanelBg)
                .border(1.dp, GridColor, RoundedCornerShape(0.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TACTICAL HUD",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Counter displays
                TacticalCounterCard("HUMANS", activeHumans, NeonCyan)
                TacticalCounterCard("TANKS", activeTanks, NeonRed)
                TacticalCounterCard("TRUCKS", activeTrucks, NeonYellow)
            }

            // Controls
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        viewModel.disconnectStream()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = NeonRed.copy(alpha = 0.2f),
                        contentColor = NeonRed
                    ),
                    border = Stroke(width = 1f).let { ButtonDefaults.outlinedBorder.copy(brush = Brush.horizontalGradient(listOf(NeonRed, NeonRed))) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DISCONNECT SOURCE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun HudBadge(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            color = Color.LightGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TacticalCounterCard(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .border(0.5.dp, GridColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = String.format("%02d", count),
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun validateUrl(url: String): Boolean {
    val cleaned = url.replace(" ", "")
    return cleaned.startsWith("rtsp://", ignoreCase = true)
}
