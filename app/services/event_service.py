import random
import string
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from app.models.event import Event
from app.models.participant import Participant
from app.models.user import User
from app.schemas.event import EventCreate
from app.services.qr_service import QRService

class EventService:
    @staticmethod
    def generate_unique_code(db: Session, length: int = 6) -> str:
        """Generate a random unique uppercase alphanumeric room code."""
        characters = string.ascii_uppercase + string.digits
        while True:
            code = "".join(random.choices(characters, k=length))
            # Verify uniqueness
            exists = db.query(Event).filter(Event.id == code).first()
            if not exists:
                return code

    @staticmethod
    def create_event(db: Session, event_in: EventCreate, creator_id: int) -> Event:
        """Create a temporary event room, calculate expiry, sign QR, and add creator as participant."""
        event_id = EventService.generate_unique_code(db)
        
        # Calculate expiry
        starts_at = datetime.utcnow()
        expires_at = starts_at + timedelta(hours=event_in.duration_hours)
        
        # Generate QR signature
        qr_payload = QRService.generate_payload(event_id, event_in.passcode)
        
        db_event = Event(
            id=event_id,
            name=event_in.name,
            description=event_in.description,
            passcode=event_in.passcode,
            qr_payload=qr_payload,
            creator_id=creator_id,
            starts_at=starts_at,
            expires_at=expires_at
        )
        
        db.add(db_event)
        db.flush()  # Obtain database session state before commit
        
        # Add creator as 'creator' participant
        creator_part = Participant(
            event_id=event_id,
            user_id=creator_id,
            role="creator"
        )
        db.add(creator_part)
        
        db.commit()
        db.refresh(db_event)
        return db_event

    @staticmethod
    def get_active_event(db: Session, event_id: str) -> Event:
        """Retrieve an event room and raise exception if it does not exist or has expired."""
        event = db.query(Event).filter(Event.id == event_id).first()
        if not event:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Event room not found"
            )
        
        # Check expiry
        if datetime.utcnow() > event.expires_at:
            raise HTTPException(
                status_code=status.HTTP_410_GONE,
                detail="Event room has expired and is no longer accessible"
            )
        return event

    @staticmethod
    def join_event(db: Session, event_id: str, user_id: int, passcode: str = None) -> Participant:
        """Join an event room after validating passcode. Creates a new participant if not already joined."""
        event = EventService.get_active_event(db, event_id)
        
        # Validate passcode
        if event.passcode and event.passcode != passcode:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Incorrect passcode for event room"
            )
            
        # Check if already a participant
        participant = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == user_id
        ).first()
        
        if not participant:
            participant = Participant(
                event_id=event_id,
                user_id=user_id,
                role="member"
            )
            db.add(participant)
            db.commit()
            db.refresh(participant)
            
        return participant

    @staticmethod
    def join_via_qr(db: Session, qr_payload_str: str, user_id: int) -> Participant:
        """Parse, verify, and join an event room directly via scanned QR code payload."""
        try:
            payload = QRService.verify_payload(qr_payload_str)
        except ValueError as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid QR code payload: {str(e)}"
            )
        
        event_id = payload["event_id"]
        passcode = payload["passcode"]
        
        return EventService.join_event(db, event_id, user_id, passcode)

    @staticmethod
    def get_event_details(db: Session, event_id: str, user_id: int) -> dict:
        """Fetch event details and verify user's participation status."""
        event = EventService.get_active_event(db, event_id)
        
        # Verify user is a participant
        is_member = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == user_id
        ).first()
        
        if not is_member:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must join the event room first to view details"
            )
            
        # Fetch participants with user info
        participants_data = []
        participants = db.query(Participant).filter(Participant.event_id == event_id).all()
        for p in participants:
            user = db.query(User).filter(User.id == p.user_id).first()
            if user:
                participants_data.append({
                    "user_id": user.id,
                    "email": user.email,
                    "full_name": user.full_name,
                    "role": p.role,
                    "joined_at": p.joined_at
                })
                
        # Structure response
        return {
            "id": event.id,
            "name": event.name,
            "description": event.description,
            "passcode": event.passcode if is_member.role in ["creator", "admin"] else None,
            "qr_payload": event.qr_payload,
            "creator_id": event.creator_id,
            "starts_at": event.starts_at,
            "expires_at": event.expires_at,
            "created_at": event.created_at,
            "participants": participants_data
        }
