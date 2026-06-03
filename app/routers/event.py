from fastapi import APIRouter, Depends, status, Query
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.user import User
from app.schemas.event import EventCreate, EventResponse, EventJoin, EventDetailsResponse
from app.services.auth_service import get_current_user
from app.services.event_service import EventService
from app.services.qr_service import QRService

router = APIRouter(prefix="/events", tags=["Event Rooms"])

@router.post("/create", response_model=EventResponse, status_code=status.HTTP_201_CREATED)
def create_event(
    event_in: EventCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Create a temporary room. Starts immediately and expires after duration_hours."""
    return EventService.create_event(db, event_in, current_user.id)

@router.post("/join", status_code=status.HTTP_200_OK)
def join_event(
    join_in: EventJoin,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Join an event room using the unique room code and optional passcode."""
    EventService.join_event(db, join_in.event_id, current_user.id, join_in.passcode)
    return {"message": f"Successfully joined event room {join_in.event_id}"}

@router.post("/join-qr", status_code=status.HTTP_200_OK)
def join_via_qr(
    payload: str = Query(..., description="The raw JSON QR code payload string"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Join an event room by submitting a scanned QR code payload."""
    participant = EventService.join_via_qr(db, payload, current_user.id)
    return {"message": f"Successfully joined event room {participant.event_id} via QR code"}

@router.get("/{event_id}", response_model=EventDetailsResponse)
def get_event_details(
    event_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Retrieve details and list of participants for an active event room."""
    return EventService.get_event_details(db, event_id, current_user.id)

@router.get("/{event_id}/qr", status_code=status.HTTP_200_OK)
def get_event_qr(
    event_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Retrieve the base64 encoded QR code image for sharing the event room."""
    event = EventService.get_active_event(db, event_id)
    qr_b64 = QRService.generate_qr_image_base64(event.qr_payload)
    return {"qr_code_image": qr_b64, "qr_payload": event.qr_payload}
