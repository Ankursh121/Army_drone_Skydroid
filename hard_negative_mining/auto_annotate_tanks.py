import os
import shutil
import cv2
try:
    from ultralytics import YOLO
except ImportError:
    print("Please install ultralytics first: pip install ultralytics")
    exit()

def auto_annotate_tanks():
    print("Loading YOLOv8 model for auto-annotation of tanks...")
    model = YOLO('yolov8n.pt') 
    
    unlabeled_dir = r"d:\Army_drones\tankssss"
    output_img_dir = r"d:\Army_drones\ladakh_drone_dataset\images\train"
    output_lbl_dir = r"d:\Army_drones\ladakh_drone_dataset\labels\train"
    preview_dir = r"d:\Army_drones\ladakh_drone_dataset\images\preview_annotations"
    
    os.makedirs(output_img_dir, exist_ok=True)
    os.makedirs(output_lbl_dir, exist_ok=True)
    os.makedirs(preview_dir, exist_ok=True)

    # Our Tank class ID in Ladakh dataset is 1
    OUR_TANK_CLASS_ID = 1
    
    image_files = [f for f in os.listdir(unlabeled_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    print(f"Found {len(image_files)} images to auto-annotate in {unlabeled_dir}.")
    
    success_count = 0
    
    for img_name in image_files:
        img_path = os.path.join(unlabeled_dir, img_name)
        
        # Run inference (tanks might look like cars, trucks, buses to YOLO)
        results = model(img_path, verbose=False, conf=0.1)
        
        yolo_lines = []
        for result in results:
            img_h, img_w = result.orig_shape
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0].item())
                # COCO classes for vehicles/large objects. We'll accept any of these as a tank since this is the tanks folder
                if cls_id in [2, 3, 4, 5, 6, 7, 8]: 
                    x_center, y_center, width, height = box.xywhn[0].tolist()
                    
                    # We can also do the aspect ratio check like in the truck script, but tanks are also rectangular or somewhat boxy
                    # Let's just add them
                    yolo_lines.append(f"{OUR_TANK_CLASS_ID} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}")
        
        # If we found at least one tank, save it
        if len(yolo_lines) > 0:
            txt_name = os.path.splitext(img_name)[0] + '.txt'
            txt_path = os.path.join(output_lbl_dir, txt_name)
            
            with open(txt_path, 'w') as f:
                f.write("\n".join(yolo_lines) + "\n")
                
            shutil.copy(img_path, os.path.join(output_img_dir, img_name))
            
            # Draw preview
            img_cv = cv2.imread(img_path)
            if img_cv is not None:
                h, w = img_cv.shape[:2]
                for line in yolo_lines:
                    _, cx, cy, bw, bh = map(float, line.split())
                    x1 = int((cx - bw / 2) * w)
                    y1 = int((cy - bh / 2) * h)
                    x2 = int((cx + bw / 2) * w)
                    y2 = int((cy + bh / 2) * h)
                    cv2.rectangle(img_cv, (x1, y1), (x2, y2), (0, 0, 255), 2)
                    cv2.putText(img_cv, "Tank", (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
                cv2.imwrite(os.path.join(preview_dir, f"annotated_{img_name}"), img_cv)
            
            success_count += 1
            
    print(f"Auto-annotation complete! Successfully found and labeled tanks in {success_count} images.")
    print(f"They have been moved to {output_img_dir}")

if __name__ == "__main__":
    auto_annotate_tanks()
