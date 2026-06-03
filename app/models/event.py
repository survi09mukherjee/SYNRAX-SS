from sqlalchemy import Column, String, Integer, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Event(Base):
    __tablename__ = "events"

    # ID is a unique room code (e.g., shortcode like 'A8F2K9' or UUID string)
    id = Column(String, primary_key=True, index=True)
    name = Column(String, nullable=False)
    description = Column(String, nullable=True)
    passcode = Column(String, nullable=True)  # Optional passcode to lock the room
    qr_payload = Column(String, nullable=False)  # Payload to validate QR room joins
    creator_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    starts_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=False)  # Temporary room lifespan
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    creator = relationship("User", back_populates="created_events")
    participants = relationship("Participant", back_populates="event", cascade="all, delete-orphan")
    media_files = relationship("Media", back_populates="event", cascade="all, delete-orphan")
    face_clusters = relationship("FaceCluster", back_populates="event", cascade="all, delete-orphan")
