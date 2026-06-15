import cv2
import threading
import time
import logging
import numpy as np

class VideoStream:
    """
    A robust, multithreaded video stream reader supporting RTSP, UDP, Webcams, and files.
    Ensures the latest frame is always available, dropping older frames to prevent latency.
    If the video source is unavailable, falls back to a synthetic simulation stream.
    """
    def __init__(self, source, reconnect_timeout=5.0):
        self.source = source
        self.reconnect_timeout = reconnect_timeout
        self.use_synthetic = False
        
        # Try DirectShow first on Windows if source is an integer index
        if isinstance(self.source, int):
            self.cap = cv2.VideoCapture(self.source, cv2.CAP_DSHOW)
        else:
            self.cap = cv2.VideoCapture(self.source)
            
        self.ret = False
        self.frame = None
        self.stopped = False
        self.lock = threading.Lock()
        
        if not self.cap.isOpened():
            logging.error(f"Failed to open video source: {self.source}. Falling back to Synthetic Target Simulation.")
            self.use_synthetic = True
            self.ret = True
            # Create an initial dummy frame
            self.frame = self._generate_synthetic_frame(0)

    def start(self):
        """Starts the background thread to continuously read frames."""
        threading.Thread(target=self._update, daemon=True).start()
        return self

    def _generate_synthetic_frame(self, frame_count):
        # Generate a simulated high-altitude Ladakh terrain background (grey/brown)
        frame = np.ones((480, 640, 3), dtype=np.uint8) * 120
        # Draw some "terrain shadows" and rocks
        cv2.circle(frame, (150, 100), 20, (80, 80, 80), -1)
        cv2.circle(frame, (450, 300), 35, (90, 90, 90), -1)
        
        # Draw a simulated moving target (e.g. Tank)
        # Bounding box moves in a circle over time
        theta = (frame_count * 0.05) % (2 * np.pi)
        cx = int(320 + 150 * np.cos(theta))
        cy = int(240 + 100 * np.sin(theta))
        
        # Draw the simulated tank (green box)
        cv2.rectangle(frame, (cx - 25, cy - 15), (cx + 25, cy + 15), (34, 139, 34), -1)
        # Draw a turret barrel
        cv2.line(frame, (cx, cy), (cx + int(30 * np.cos(theta)), cy + int(30 * np.sin(theta))), (20, 100, 20), 4)
        
        return frame

    def _update(self):
        """Internal method to continuously pull frames from the capture object."""
        frame_count = 0
        while not self.stopped:
            if self.use_synthetic:
                time.sleep(0.033) # ~30 FPS
                frame_count += 1
                with self.lock:
                    self.ret = True
                    self.frame = self._generate_synthetic_frame(frame_count)
                continue

            if not self.cap.isOpened():
                logging.warning("Stream disconnected. Attempting to reconnect...")
                self._reconnect()
                continue
                
            ret, frame = self.cap.read()
            
            if ret:
                with self.lock:
                    self.ret = ret
                    self.frame = frame
            else:
                logging.warning("Frame dropped or end of stream. Reconnecting...")
                time.sleep(0.1)
                self._reconnect()

    def _reconnect(self):
        """Attempts to release and reopen the video stream."""
        self.cap.release()
        time.sleep(self.reconnect_timeout)
        if isinstance(self.source, int):
            self.cap = cv2.VideoCapture(self.source, cv2.CAP_DSHOW)
        else:
            self.cap = cv2.VideoCapture(self.source)
        if not self.cap.isOpened():
            logging.error("Reconnection failed. Staying on Synthetic simulation.")
            self.use_synthetic = True

    def read(self):
        """Returns the most recently read frame."""
        with self.lock:
            return self.ret, self.frame

    def stop(self):
        """Stops the thread and releases the video capture."""
        self.stopped = True
        if self.cap:
            self.cap.release()
