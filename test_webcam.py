import cv2
from ultralytics import YOLO

# Load the trained model
model_path = r"d:\Army_drones\runs\detect\ladakh_drone_runs\yolov8s_altitude_optimized\weights\best.pt"
print(f"Loading model from {model_path}...")
model = YOLO(model_path)

# Using DirectShow explicitly because the default MSMF backend is crashing (Error -2147483638)
cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)

if not cap.isOpened():
    print("Error: Could not open webcam.")
    exit(1)

print("Starting webcam inference... Press 'q' to quit the window.")

while True:
    ret, frame = cap.read()
    if not ret:
        print("Error: Failed to grab frame.")
        break
        
    # Run YOLO inference with lowered confidence threshold
    results = model(frame, verbose=False, conf=0.15)
    
    # Get the frame with the bounding boxes plotted
    annotated_frame = results[0].plot()
    
    # Display the annotated frame in a window
    cv2.imshow("YOLOv8 Real-Time Inference", annotated_frame)
    
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Release the webcam and close all OpenCV windows
cap.release()
cv2.destroyAllWindows()
