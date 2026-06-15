from ultralytics import YOLO

class ByteTracker:
    """
    Wrapper for object tracking.
    Since Ultralytics YOLO has built-in deep tracking (ByteTrack/BoT-SORT),
    this module provides a standardized interface to execute tracker-enabled inference.
    """
    def __init__(self, model_path, conf_thresh=0.4, iou_thresh=0.45, tracker_type="bytetrack.yaml"):
        self.model = YOLO(model_path)
        self.conf_thresh = conf_thresh
        self.iou_thresh = iou_thresh
        self.tracker_type = tracker_type

    def track(self, frame):
        """
        Runs tracking inference on a single frame.
        Returns a list of dicts including 'track_id'.
        """
        results = self.model.track(
            frame, 
            conf=self.conf_thresh, 
            iou=self.iou_thresh, 
            persist=True, 
            tracker=self.tracker_type, 
            verbose=False
        )
        
        detections = []
        if len(results) > 0 and results[0].boxes is not None:
            boxes = results[0].boxes
            names = results[0].names
            
            for i, box in enumerate(boxes):
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                conf = float(box.conf[0])
                cls_id = int(box.cls[0])
                
                # Some frames an object is detected but not yet assigned a track ID by the tracker
                track_id = int(box.id[0]) if box.id is not None else None
                
                detections.append({
                    'track_id': track_id,
                    'class_id': cls_id,
                    'class_name': names[cls_id],
                    'conf': conf,
                    'bbox': [int(x1), int(y1), int(x2), int(y2)]
                })
                
        return detections, results[0]
