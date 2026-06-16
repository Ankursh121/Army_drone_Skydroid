package com.ladakh.drone.gcs.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.ladakh.drone.gcs.network.Detection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class YoloDetector @Inject constructor(
    private val context: Context
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    companion object {
        private const val MODEL_NAME = "best.onnx"
        private const val INPUT_WIDTH = 640
        private const val INPUT_HEIGHT = 640
        private const val IOU_THRESHOLD = 0.45f
        
        // Class-specific confidence thresholds to satisfy Precision >= 98% requirements
        private const val CONF_HUMAN = 0.58f
        private const val CONF_TANK = 0.65f
        private const val CONF_TRUCK = 0.60f
        
        // Classes mapped based on data.yaml
        private val CLASS_NAMES = listOf("human", "tank", "military truck")
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBytes = context.assets.open(MODEL_NAME).readBytes()
            val options = OrtSession.SessionOptions().apply {
                // Optimize for mobile execution
                addConfigEntry("session.intra_op.num_threads", "4") // Set thread count
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(modelBytes, options)
            android.util.Log.i("YoloDetector", "ONNX Model loaded successfully.")
        } catch (e: Exception) {
            android.util.Log.e("YoloDetector", "Failed to load model: ${e.message}", e)
        }
    }

    /**
     * Executes real-time inference on the input bitmap.
     * Returns a list of parsed detections.
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        val currentSession = session ?: return emptyList()
        
        // 1. Preprocess Bitmap (Resize and Normalize)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val inputBuffer = allocateFloatBuffer(resizedBitmap)
        
        // 2. Create ONNX Tensor
        val inputShape = longArrayOf(1, 3, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong())
        var inputTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        
        try {
            inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)
            val inputs = mapOf("images" to inputTensor) // YOLOv8 input is named "images"
            
            // 3. Run Inference
            results = currentSession.run(inputs)
            
            // 4. Parse Detections
            val outputTensor = results[0] as? OnnxTensor ?: return emptyList()
            val outputArray = outputTensor.value as? Array<Array<FloatArray>> ?: return emptyList()
            
            // Output shape is [1][7][8400]
            val detections = parseDetections(outputArray[0], bitmap.width, bitmap.height)
            
            // 5. Apply Non-Maximum Suppression (NMS)
            return applyNMS(detections)
        } catch (e: Exception) {
            android.util.Log.e("YoloDetector", "Error running inference: ${e.message}", e)
            return emptyList()
        } finally {
            inputTensor?.close()
            results?.close()
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
        }
    }

    /**
     * Preprocesses bitmap into BCHW format (1, 3, 640, 640)
     */
    private fun allocateFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val size = INPUT_WIDTH * INPUT_HEIGHT
        val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * size * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val intValues = IntArray(size)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Channels-First (BCHW): Write Red, Green, Blue channels separately
        // Red channel
        for (i in 0 until size) {
            val pixel = intValues[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            floatBuffer.put(r)
        }
        // Green channel
        for (i in 0 until size) {
            val pixel = intValues[i]
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            floatBuffer.put(g)
        }
        // Blue channel
        for (i in 0 until size) {
            val pixel = intValues[i]
            val b = (pixel and 0xFF) / 255.0f
            floatBuffer.put(b)
        }
        
        floatBuffer.rewind()
        return floatBuffer
    }

    /**
     * Parses the raw ONNX output array of shape [7][8400]
     */
    private fun parseDetections(
        output: Array<FloatArray>,
        origWidth: Int,
        origHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numPredictions = output[0].size // Should be 8400
        
        for (col in 0 until numPredictions) {
            // Find class with max score (rows 4 to 6)
            var maxScore = -1f
            var maxClassId = -1
            for (row in 4 until 7) {
                val score = output[row][col]
                if (score > maxScore) {
                    maxScore = score
                    maxClassId = row - 4
                }
            }

            val threshold = when (maxClassId) {
                0 -> CONF_HUMAN
                1 -> CONF_TANK
                2 -> CONF_TRUCK
                else -> 0.50f
            }

            if (maxScore >= threshold) {
                // Bounding Box (rows 0 to 3): [x_center, y_center, width, height]
                val xCenter = output[0][col]
                val yCenter = output[1][col]
                val w = output[2][col]
                val h = output[3][col]

                // Convert from center representation to corner coordinates [x1, y1, x2, y2]
                val x1Val = xCenter - w / 2f
                val y1Val = yCenter - h / 2f
                val x2Val = xCenter + w / 2f
                val y2Val = yCenter + h / 2f

                // Scale bounding boxes back to the original image dimensions
                val x1 = (x1Val / INPUT_WIDTH * origWidth).toInt().coerceIn(0, origWidth)
                val y1 = (y1Val / INPUT_HEIGHT * origHeight).toInt().coerceIn(0, origHeight)
                val x2 = (x2Val / INPUT_WIDTH * origWidth).toInt().coerceIn(0, origWidth)
                val y2 = (y2Val / INPUT_HEIGHT * origHeight).toInt().coerceIn(0, origHeight)

                val className = CLASS_NAMES.getOrNull(maxClassId) ?: "unknown"

                detections.add(
                    Detection(
                        track_id = null, // Track ID will be computed by local tracker
                        class_name = className,
                        confidence = maxScore.toDouble(),
                        bbox = listOf(x1, y1, x2, y2)
                    )
                )
            }
        }
        return detections
    }

    /**
     * Non-Maximum Suppression (NMS) to eliminate overlapping bounding boxes
     */
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence ?: 0.0 }
        val selectedDetections = mutableListOf<Detection>()
        val active = BooleanArray(sortedDetections.size) { true }

        for (i in sortedDetections.indices) {
            if (!active[i]) continue
            val main = sortedDetections[i]
            selectedDetections.add(main)

            for (j in i + 1 until sortedDetections.size) {
                if (!active[j]) continue
                val candidate = sortedDetections[j]
                if (main.class_name == candidate.class_name) {
                    val iou = calculateIoU(main.bbox ?: emptyList(), candidate.bbox ?: emptyList())
                    if (iou > IOU_THRESHOLD) {
                        active[j] = false
                    }
                }
            }
        }
        return selectedDetections
    }

    private fun calculateIoU(box1: List<Int>, box2: List<Int>): Float {
        if (box1.size < 4 || box2.size < 4) return 0f
        val x1 = max(box1[0], box2[0])
        val y1 = max(box1[1], box2[1])
        val x2 = min(box1[2], box2[2])
        val y2 = min(box1[3], box2[3])

        val intersectionArea = max(0, x2 - x1) * max(0, y2 - y1)
        val area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
        val area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
        val unionArea = area1 + area2 - intersectionArea

        if (unionArea <= 0) return 0f
        return intersectionArea.toFloat() / unionArea.toFloat()
    }
}
