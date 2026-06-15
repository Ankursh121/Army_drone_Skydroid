import sys
import os
import argparse

# Ensure the root directory is on the python path to resolve core.* imports
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from inference.logger import setup_logger
from inference.pipeline import DetectionPipeline

def main():
    parser = argparse.ArgumentParser(description="Ladakh Drone Detection System - Real-Time Engine")
    parser.add_argument("--source", type=str, default="0", 
                        help="Video source (e.g., 0 for webcam, RTSP URL, or video.mp4)")
    parser.add_argument("--model", type=str, 
                        default=r"d:\Army_drones\runs\detect\ladakh_drone_runs\yolov8s_altitude_optimized\weights\best.pt",
                        help="Path to trained YOLO .pt or .engine file")
    
    args = parser.parse_args()

    # Initialize logger
    setup_logger()
    
    # Cast source to integer if it's "0" (for webcam)
    video_source = int(args.source) if args.source.isdigit() else args.source
    
    # Initialize and start pipeline
    pipeline = DetectionPipeline(video_source=video_source, model_path=args.model)
    pipeline.start()

if __name__ == "__main__":
    main()
