# Ladakh Drone Detection System

This repository hosts the **Ladakh Drone Detection System**, a production-grade real-time object detection engine and ground station application designed for drone operations at altitudes of 0–60 meters in mountainous terrains.

## Supported Classes
- **Class 0:** Human
- **Class 1:** Tank
- **Class 2:** Military Truck

---

## Directory Structure

- `datasets/` - Data preprocessing and split scripts.
- `training/` - YOLO model training pipelines.
- `evaluation/` - Precision, recall, and model validation scripts.
- `hard_negative_mining/` - Auto-labeling and terrain false-positive filtering assets.
- `models/` - Saved model weights (`best.pt`).
- `inference/` - Core real-time detection pipeline and video grabbers.
- `tracking/` - Object tracking wrapper scripts.
- `api/` - Telemetry broadcasting server.
- `app/` - Native Kotlin Android Ground Station Application codebase.
- `config/` - Pipeline YAML configs.
- `logs/` - Performance and diagnostic logs.
- `tests/` - System and stream unit tests.
- `deployment/` - Dockerfile and orchestration configs.
- `docs/` - System design and user guides.
- `scripts/` - Pipeline execution entry scripts.
- `assets/` - UI mockups and static documentation assets.

---

## Quick Start (Real-Time Engine)

### Prerequisites
Install the required python dependencies:
```bash
pip install -r requirements.txt
```

### Running Inference via Command Line
Run the engine on a local webcam:
```bash
python scripts/run_engine.py --source 0
```

To run on an RTSP stream:
```bash
python scripts/run_engine.py --source rtsp://your_stream_url
```

---

## Docker Deployment
Start the stream server and detection engine containers:
```bash
docker-compose up -d
```
