import socket
import threading
import logging

class TelemetryBroadcaster:
    """
    Broadcasts the JSON metadata over a TCP Socket Server.
    TCP ensures reliable delivery of bounding box data to the Android Emulator.
    The Android Emulator can easily connect to this server via the 10.0.2.2 gateway.
    """
    def __init__(self, host="0.0.0.0", port=5005):
        self.host = host
        self.port = port
        self.server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_sock.bind((self.host, self.port))
        self.server_sock.listen(5)
        self.clients = []
        self.is_running = True
        
        logging.info(f"Telemetry TCP Server listening on {host}:{port}")
        # Accept connections in a separate thread
        threading.Thread(target=self._accept_clients, daemon=True).start()

    def _accept_clients(self):
        while self.is_running:
            try:
                client_sock, addr = self.server_sock.accept()
                logging.info(f"Ground Station connected from {addr}")
                self.clients.append(client_sock)
            except Exception:
                break

    def broadcast(self, json_payload):
        # We append a newline delimiter so the Android parser knows where a packet ends
        data = (json_payload + "\n").encode('utf-8')
        active_clients = []
        for client in self.clients:
            try:
                client.sendall(data)
                active_clients.append(client)
            except Exception:
                logging.info("Client disconnected.")
                client.close()
        self.clients = active_clients

    def close(self):
        self.is_running = False
        self.server_sock.close()
        for client in self.clients:
            client.close()
