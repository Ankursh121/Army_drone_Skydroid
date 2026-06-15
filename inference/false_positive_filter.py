class FalsePositiveFilter:
    """
    A domain-specific filter for the Ladakh terrain to aggressively eliminate:
    1. Intermittent flickering noise (shadows, snow patches) using a hit-streak requirement.
    2. Completely stationary objects (rocks misidentified as tanks/humans).
    """
    def __init__(self, stationary_time_thresh=10.0, min_hit_streak=3, movement_tolerance=10):
        self.stationary_time_thresh = stationary_time_thresh
        self.min_hit_streak = min_hit_streak
        self.movement_tolerance = movement_tolerance # Max pixel movement to be considered 'stationary'
        
        # track_id -> metadata dictionary
        self.tracks_data = {}

    def filter_detections(self, detections_with_tracks, current_timestamp):
        """
        Receives a list of tracked detections and returns only the confirmed, valid ones.
        """
        valid_detections = []
        
        for det in detections_with_tracks:
            tid = det.get('track_id')
            if tid is None:
                continue # If not tracked, we can't properly filter temporally
                
            x1, y1, x2, y2 = det['bbox']
            center_x = (x1 + x2) / 2
            center_y = (y1 + y2) / 2
            
            if tid not in self.tracks_data:
                # First time seeing this object
                self.tracks_data[tid] = {
                    'first_seen': current_timestamp,
                    'last_seen': current_timestamp,
                    'hit_count': 1,
                    'initial_center': (center_x, center_y),
                    'is_stationary': True
                }
            else:
                # Update existing track
                data = self.tracks_data[tid]
                data['last_seen'] = current_timestamp
                data['hit_count'] += 1
                
                # Check for movement
                init_x, init_y = data['initial_center']
                dist_moved = ((center_x - init_x)**2 + (center_y - init_y)**2) ** 0.5
                
                if dist_moved > self.movement_tolerance:
                    data['is_stationary'] = False

            data = self.tracks_data[tid]
            
            # Rule 1: Min Hit Streak (Eliminates single-frame ghost/shadow detections)
            if data['hit_count'] < self.min_hit_streak:
                continue
                
            # Rule 2: Stationary Timeout (Eliminates rocks masquerading as objects)
            # If the object has been completely stationary for longer than the threshold, hide it.
            time_alive = current_timestamp - data['first_seen']
            if data['is_stationary'] and time_alive > self.stationary_time_thresh:
                continue
                
            # If it passes all filters, it is valid!
            valid_detections.append(det)
            
        return valid_detections
