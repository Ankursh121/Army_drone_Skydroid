package com.ladakh.drone.gcs.domain

import com.ladakh.drone.gcs.network.Detection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class TrackedObject(
    val id: Int,
    val className: String,
    var bbox: List<Int>,
    var confidence: Double,
    val firstSeenTime: Long = System.currentTimeMillis(),
    var hitCount: Int = 1,
    val initialCenter: Pair<Float, Float>,
    var isStationary: Boolean = true,
    var inactiveFrames: Int = 0
)

class ObjectTracker {
    private val nextId = AtomicInteger(1)
    private val trackedObjects = mutableListOf<TrackedObject>()

    companion object {
        private const val IOU_THRESHOLD = 0.3f
        private const val MAX_INACTIVE_FRAMES = 10
        
        // False Positive Filter Constants (Tuned for Ladakh Terrain)
        private const val MIN_HIT_STREAK = 3
        private const val MOVEMENT_TOLERANCE_PX = 15.0
        private const val STATIONARY_TIMEOUT_MS = 8000L // Hide static errors after 8 seconds
    }

    /**
     * Updates tracks with new detections from the current frame.
     * Returns a list of detections with assigned tracking IDs after applying the FP filter.
     */
    @Synchronized fun update(detections: List<Detection>): List<Detection> {
        // Increment inactive frames for all existing tracks
        trackedObjects.forEach { it.inactiveFrames++ }

        val unmatchedDetections = detections.toMutableList()
        val matchedTracks = mutableSetOf<TrackedObject>()

        // 1. Match incoming detections with existing tracks based on maximum IoU
        for (det in detections) {
            var bestTrack: TrackedObject? = null
            var maxIoU = -1f

            for (track in trackedObjects) {
                if (track in matchedTracks) continue
                if (track.className != det.class_name) continue

                val iou = calculateIoU(track.bbox, det.bbox ?: emptyList())
                if (iou > IOU_THRESHOLD && iou > maxIoU) {
                    maxIoU = iou
                    bestTrack = track
                }
            }

            if (bestTrack != null) {
                // Update existing track
                val currentBbox = det.bbox ?: emptyList()
                bestTrack.bbox = currentBbox
                bestTrack.confidence = det.confidence ?: 0.0
                bestTrack.inactiveFrames = 0
                bestTrack.hitCount++
                
                // Track movement to see if it's stationary (e.g. rocks)
                if (bestTrack.isStationary && currentBbox.size >= 4) {
                    val cx = (currentBbox[0] + currentBbox[2]) / 2f
                    val cy = (currentBbox[1] + currentBbox[3]) / 2f
                    val dist = sqrt(((cx - bestTrack.initialCenter.first) * (cx - bestTrack.initialCenter.first) + 
                                     (cy - bestTrack.initialCenter.second) * (cy - bestTrack.initialCenter.second)).toDouble())
                    if (dist > MOVEMENT_TOLERANCE_PX) {
                        bestTrack.isStationary = false
                    }
                }

                matchedTracks.add(bestTrack)
                unmatchedDetections.remove(det)
            }
        }

        // 2. Create new tracks for unmatched detections
        for (det in unmatchedDetections) {
            val bbox = det.bbox ?: emptyList()
            if (bbox.size >= 4) {
                val cx = (bbox[0] + bbox[2]) / 2f
                val cy = (bbox[1] + bbox[3]) / 2f
                val newTrack = TrackedObject(
                    id = nextId.getAndIncrement(),
                    className = det.class_name ?: "unknown",
                    bbox = bbox,
                    confidence = det.confidence ?: 0.0,
                    initialCenter = Pair(cx, cy)
                )
                trackedObjects.add(newTrack)
            }
        }

        // 3. Clean up dead tracks
        trackedObjects.removeAll { it.inactiveFrames > MAX_INACTIVE_FRAMES }

        // 4. Filter out false positives (stationary timeout or low hit streak)
        val currentTime = System.currentTimeMillis()
        return trackedObjects
            .filter { track ->
                // Must be seen in current frame
                track.inactiveFrames == 0 &&
                // Rule 1: Hit Streak Filter (flicker noise prevention)
                track.hitCount >= MIN_HIT_STREAK &&
                // Rule 2: Stationary Timeout (rock false alarm prevention)
                (!track.isStationary || (currentTime - track.firstSeenTime) < STATIONARY_TIMEOUT_MS)
            }
            .map { track ->
                Detection(
                    track_id = track.id,
                    class_name = track.className,
                    confidence = track.confidence,
                    bbox = track.bbox
                )
            }
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
