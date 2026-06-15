from ultralytics import YOLO

def train_model():
    print("Loading YOLOv8s baseline model...")
    # Load a model
    # Resuming from the last checkpoint
    model = YOLO(r'd:\Army_drones\runs\detect\ladakh_drone_runs\yolov8s_altitude_optimized\weights\last.pt')

    print("Starting YOLOv8 training for Ladakh Drone Detection System...")

    # Train the model (resume=True automatically uses the previous arguments)
    results = model.train(resume=True)
    
    print("Training successfully initialized/completed. Results saved to the same directory.")

if __name__ == '__main__':
    # Make sure to run this script from a terminal or IDE where your GPU environment is activated!
    train_model()
