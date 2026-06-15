import os
import shutil
import cv2
try:
    from ultralytics import YOLO
except ImportError:
    print("Please install ultralytics first: pip install ultralytics")
    exit()

def auto_annotate_trucks():
    # Load a pre-trained YOLOv8 model (downloads automatically if not present)
    # This model is pre-trained on the COCO dataset and knows what generic 'trucks' look like.
    print("Loading YOLOv8 model for auto-annotation...")
    model = YOLO('yolov8n.pt') 
    
    unlabeled_dir = r"d:\Army_drones\ladakh_drone_dataset\images\unlabeled_trucks"
    output_img_dir = r"d:\Army_drones\ladakh_drone_dataset\images\train"
    output_lbl_dir = r"d:\Army_drones\ladakh_drone_dataset\labels\train"
    preview_dir = r"d:\Army_drones\ladakh_drone_dataset\images\preview_annotations"
    
    os.makedirs(output_img_dir, exist_ok=True)
    os.makedirs(output_lbl_dir, exist_ok=True)
    os.makedirs(preview_dir, exist_ok=True)

    # In the standard COCO dataset, 'truck' is class 7.
    # We want to map this to our Ladakh dataset where Military Truck is class 2.
    COCO_TRUCK_CLASS_ID = 7
    OUR_MILITARY_TRUCK_ID = 2
    
    image_files = [f for f in os.listdir(unlabeled_dir) if f.endswith('.jpg')]
    print(f"Found {len(image_files)} images to auto-annotate.")
    
    success_count = 0
    
    for img_name in image_files:
        img_path = os.path.join(unlabeled_dir, img_name)
        
        # Run inference on the image (increased confidence to ignore faint rocks)
        results = model(img_path, verbose=False, conf=0.45)
        
        yolo_lines = []
        # Parse the detections
        for result in results:
            img_h, img_w = result.orig_shape
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0].item())
                # If the AI thinks it's a truck, or a car (sometimes military trucks look like cars from high up)
                if cls_id == COCO_TRUCK_CLASS_ID or cls_id == 2: # 2 is car in COCO
                    # Get normalized xywh coordinates
                    x_center, y_center, width, height = box.xywhn[0].tolist()
                    
                    # Convert to pixel dimensions to check shape
                    pixel_w = width * img_w
                    pixel_h = height * img_h
                    
                    # Aspect ratio check: rocks are usually square, trucks are rectangular
                    aspect_ratio = max(pixel_w, pixel_h) / min(pixel_w, pixel_h) if min(pixel_w, pixel_h) > 0 else 0
                    
                    # Filter out perfectly square objects (aspect ratio < 1.3 are likely boulders)
                    if aspect_ratio > 1.3:
                        yolo_lines.append(f"{OUR_MILITARY_TRUCK_ID} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}")
        
        # If we found at least one truck, save the label and move the image to train!
        if len(yolo_lines) > 0:
            txt_name = img_name.replace('.jpg', '.txt')
            txt_path = os.path.join(output_lbl_dir, txt_name)
            
            with open(txt_path, 'w') as f:
                f.write("\n".join(yolo_lines) + "\n")
                
            # Copy image to the official train folder
            shutil.copy(img_path, os.path.join(output_img_dir, img_name))
            
            # Draw the bounding boxes to a preview image so the user can see them
            img_cv = cv2.imread(img_path)
            h, w = img_cv.shape[:2]
            for line in yolo_lines:
                _, cx, cy, bw, bh = map(float, line.split())
                # Convert YOLO back to pixel coordinates for drawing
                x1 = int((cx - bw / 2) * w)
                y1 = int((cy - bh / 2) * h)
                x2 = int((cx + bw / 2) * w)
                y2 = int((cy + bh / 2) * h)
                cv2.rectangle(img_cv, (x1, y1), (x2, y2), (0, 255, 0), 2)
                cv2.putText(img_cv, "Military Truck", (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
            
            cv2.imwrite(os.path.join(preview_dir, img_name), img_cv)
            
            success_count += 1
            
    print(f"Auto-annotation complete! Successfully found and labeled trucks in {success_count} images.")
    print(f"They have been moved to {output_img_dir}")
    print(f"Check the '{preview_dir}' folder to visually see the bounding boxes!")

if __name__ == "__main__":
    auto_annotate_trucks()
