import cv2

# DSHOW is usually the most stable on Windows
cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)

# 1. Force MJPG codec (often fixes green screen issues on webcams)
cap.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*'MJPG'))

# 2. Force a standard HD resolution (some cameras corrupt into green screens at default 640x480)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

if not cap.isOpened():
    print("Error: Could not open webcam.")
    exit(1)

print("Starting plain webcam feed (NO AI MODEL)... Press 'q' to quit.")

while True:
    ret, frame = cap.read()
    if not ret:
        print("Failed to grab frame")
        break
        
    cv2.imshow("Webcam Test (No YOLO)", frame)
    
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
