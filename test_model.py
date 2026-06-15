from ultralytics import YOLO
import os

model_path = r"d:\Army_drones\runs\detect\ladakh_drone_runs\yolov8s_altitude_optimized\weights\best.pt"
image_path = r"d:\Army_drones\ladakh_drone_dataset\images\val\0000007_04999_d_0000036.jpg"

if not os.path.exists(model_path):
    print(f"Model not found at {model_path}")
    exit(1)

if not os.path.exists(image_path):
    print(f"Image not found at {image_path}")
    exit(1)

print("Loading model...")
model = YOLO(model_path)

print("Running inference...")
results = model.predict(image_path, save=True)

print("Inference completed. Results saved.")
