from fastapi import WebSocket
from typing import Dict, List, Any

class WebSocketManager:
    def __init__(self):
        # Map event_id -> list of connected WebSocket objects
        self.active_connections: Dict[str, List[WebSocket]] = {}

    async def connect(self, websocket: WebSocket, event_id: str):
        """Accept WebSocket connection and store it under the event_id room."""
        await websocket.accept()
        if event_id not in self.active_connections:
            self.active_connections[event_id] = []
        self.active_connections[event_id].append(websocket)

    def disconnect(self, websocket: WebSocket, event_id: str):
        """Remove a closed WebSocket connection from the event_id room."""
        if event_id in self.active_connections:
            if websocket in self.active_connections[event_id]:
                self.active_connections[event_id].remove(websocket)
            if not self.active_connections[event_id]:
                del self.active_connections[event_id]

    async def broadcast_to_room(self, event_id: str, message: Any):
        """Send a JSON payload to all connected clients in a specific event room."""
        if event_id in self.active_connections:
            # Create a copy of the list to avoid mutations during iteration
            for connection in list(self.active_connections[event_id]):
                try:
                    await connection.send_json(message)
                except Exception:
                    # If socket send fails, clean up the dead connection
                    self.disconnect(connection, event_id)

manager = WebSocketManager()
