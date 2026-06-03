from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from app.utils.websocket_manager import manager
from app.database import SessionLocal
from app.models.participant import Participant
from app.services.event_service import EventService
from app.utils.security import decode_access_token

router = APIRouter(prefix="/events", tags=["WebSockets"])

@router.websocket("/{event_id}/ws")
async def websocket_endpoint(websocket: WebSocket, event_id: str):
    """
    WebSocket endpoint for real-time room feeds.
    Clients connect via: ws://localhost:8000/api/v1/events/{event_id}/ws?token={jwt_token}
    """
    # WebSockets do not support custom headers in standard browser APIs,
    # so we extract the JWT token from the query parameters.
    token = websocket.query_params.get("token")
    if not token:
        await websocket.close(code=4001, reason="Authentication token missing")
        return

    # Verify token
    payload = decode_access_token(token)
    if not payload:
        await websocket.close(code=4002, reason="Authentication token invalid or expired")
        return

    user_id_str = payload.get("sub")
    if not user_id_str:
        await websocket.close(code=4002, reason="Invalid token structure")
        return
        
    user_id = int(user_id_str)
    db = SessionLocal()
    
    try:
        # Verify event exists and is not expired
        EventService.get_active_event(db, event_id)
        
        # Verify user is a participant of the event room
        is_member = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == user_id
        ).first()
        
        if not is_member:
            await websocket.close(code=4003, reason="Access denied: Not a participant of this event room")
            return

        # Add connection to manager
        await manager.connect(websocket, event_id)
        
        try:
            # Keep socket alive and listen for client messages
            while True:
                # We can receive and discard messages, or handle client pings
                await websocket.receive_text()
        except WebSocketDisconnect:
            manager.disconnect(websocket, event_id)
            
    except Exception as e:
        await websocket.close(code=4000, reason=f"Server error: {str(e)}")
    finally:
        db.close()
