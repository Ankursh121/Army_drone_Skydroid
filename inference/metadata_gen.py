import json
from datetime import datetime, timezone

class MetadataGenerator:
    """
    Serializes valid detections and performance metrics into standardized JSON 
    telemetry payloads for Ground Control Stations (GCS) or external databases.
    """
    @staticmethod
    def generate_payload(valid_detections, fps, latency_ms):
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "fps_current": round(fps, 2),
            "latency_ms": round(latency_ms, 2),
            "detections": []
        }
        
        for det in valid_detections:
            payload["detections"].append({
                "track_id": det.get('track_id'),
                "class_name": det.get('class_name'),
                "class_id": det.get('class_id'),
                "confidence": round(det.get('conf', 0), 3),
                "bbox": det.get('bbox')
            })
            
        return json.dumps(payload)
