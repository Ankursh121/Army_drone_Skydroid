from ultralytics import YOLO
import logging

class DroneDetector:
    """
    Wrapper for the YOLO model handling initialization, inference, NMS, and class extraction.
    """
    def __init__(self, model_path, conf_thresh=0.4, iou_thresh=0.45):
        self.model_path = model_path
        self.conf_thresh = conf_thresh
        self.iou_thresh = iou_thresh
        
        logging.info(f"Loading YOLO model from {model_path}...")
        try:
            self.model = YOLO(model_path)
            logging.info("Model loaded successfully.")
        except Exception as e:
            logging.error(f"Failed to load model: {e}")
            raise e

    def predict(self, frame):
        """
        Runs inference on a single BGR frame.
        Returns a tuple:
        - detections (list of dicts): [{'class_id': int, 'class_name': str, 'conf': float, 'bbox': [x1, y1, x2, y2]}]
        - raw_result: The raw Ultralytics result object (useful for plotting)
        """
        # verbose=False to keep terminal logs clean during high-speed real-time processing
        results = self.model(frame, conf=self.conf_thresh, iou=self.iou_thresh, verbose=False)
        
        detections = []
        if len(results) > 0:
            boxes = results[0].boxes
            names = results[0].names
            
            for box in boxes:
                # Extract coordinates
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                
                # Extract confidence and class ID
                conf = float(box.conf[0])
                cls_id = int(box.cls[0])
                cls_name = names[cls_id]
                
                detections.append({
                    'class_id': cls_id,
                    'class_name': cls_name,
                    'conf': conf,
                    'bbox': [int(x1), int(y1), int(x2), int(y2)]
                })
                
        return detections, results[0]
