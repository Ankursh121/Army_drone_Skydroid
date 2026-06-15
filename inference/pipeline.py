import time
import cv2
import logging
from inference.video_input import VideoStream
from tracking.tracker import ByteTracker
from inference.false_positive_filter import FalsePositiveFilter
from inference.metadata_gen import MetadataGenerator
from api.output_server import TelemetryBroadcaster

class DetectionPipeline:
    """
    The main orchestrator that links the Video Input, Tracking, Filtering, and Telemetry modules.
    """
    def __init__(self, video_source, model_path, udp_ip="0.0.0.0", udp_port=5005):
        self.stream = VideoStream(video_source)
        # Using conf_thresh=0.25 initially, will be filtered heavily by the FP filter anyway
        self.tracker = ByteTracker(model_path, conf_thresh=0.25)
        self.fp_filter = FalsePositiveFilter(stationary_time_thresh=10.0, min_hit_streak=3)
        self.broadcaster = TelemetryBroadcaster(udp_ip, udp_port)
        self.is_running = False

    def start(self):
        logging.info("Starting detection pipeline...")
        self.stream.start()
        self.is_running = True
        
        prev_time = time.time()
        
        try:
            while self.is_running:
                ret, frame = self.stream.read()
                if not ret or frame is None:
                    # Give the thread a tiny fraction of a second to fetch a new frame if we're going too fast
                    time.sleep(0.005)
                    continue
                
                start_inference = time.time()
                
                # Check if we are using synthetic stream fallback
                if getattr(self.stream, 'use_synthetic', False):
                    import math
                    # Generate mock telemetry detections that move over time
                    theta = (time.time() * 1.5) % (2 * 3.14159)
                    cx = int(320 + 150 * math.cos(theta))
                    cy = int(240 + 100 * math.sin(theta))
                    
                    valid_detections = [
                        {
                            'track_id': 42,
                            'class_name': 'Tank',
                            'class_id': 1,
                            'conf': 0.92,
                            'bbox': [cx - 30, cy - 20, cx + 30, cy + 20]
                        },
                        {
                            'track_id': 7,
                            'class_name': 'Human',
                            'class_id': 0,
                            'conf': 0.85,
                            'bbox': [100, 120, 130, 180]
                        }
                    ]
                    latency_ms = 4.5
                    fps = 30.0
                else:
                    # 1. Inference & Tracking
                    raw_detections, raw_result = self.tracker.track(frame)
                    
                    # 2. Filtering (Ladakh Terrain Specific)
                    valid_detections = self.fp_filter.filter_detections(raw_detections, start_inference)
                    
                    # Metrics Calculation
                    end_inference = time.time()
                    latency_ms = (end_inference - start_inference) * 1000
                    fps = 1.0 / (time.time() - prev_time) if (time.time() - prev_time) > 0 else 0
                
                prev_time = time.time()
                
                # 3. Metadata Generation
                payload = MetadataGenerator.generate_payload(valid_detections, fps, latency_ms)
                
                # 4. Telemetry Broadcast
                self.broadcaster.broadcast(payload)
                
                # --- VISUALIZATION (For local testing & diagnostics) ---
                viz_frame = frame.copy()
                for det in valid_detections:
                    x1, y1, x2, y2 = det['bbox']
                    # Draw Green Bounding Box for valid detections
                    cv2.rectangle(viz_frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
                    label = f"ID:{det['track_id']} {det['class_name']} {det['conf']:.2f}"
                    cv2.putText(viz_frame, label, (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                
                # Display HUD stats
                cv2.putText(viz_frame, f"FPS: {fps:.1f} | Latency: {latency_ms:.1f}ms", (10, 30), 
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)
                
                cv2.imshow("Ladakh Drone Real-Time Engine", viz_frame)
                
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    self.stop()
                    
        except KeyboardInterrupt:
            logging.info("Keyboard interrupt received.")
            self.stop()

    def stop(self):
        logging.info("Stopping detection pipeline safely...")
        self.is_running = False
        self.stream.stop()
        self.broadcaster.close()
        cv2.destroyAllWindows()
