from pydantic import BaseModel, Field
from datetime import datetime
from typing import Optional, List

class EventBase(BaseModel):
    name: str = Field(..., min_length=3, max_length=100)
    description: Optional[str] = Field(None, max_length=500)

class EventCreate(EventBase):
    passcode: Optional[str] = Field(None, description="Optional room passcode")
    duration_hours: int = Field(24, description="Event room active duration in hours (e.g., 24, 48, 72)")

class EventJoin(BaseModel):
    event_id: str
    passcode: Optional[str] = None

class EventResponse(EventBase):
    id: str
    passcode: Optional[str] = None
    qr_payload: str
    creator_id: int
    starts_at: datetime
    expires_at: datetime
    created_at: datetime

    class Config:
        from_attributes = True

class ParticipantResponse(BaseModel):
    user_id: int
    email: str
    full_name: Optional[str]
    role: str
    joined_at: datetime

    class Config:
        from_attributes = True

class EventDetailsResponse(EventResponse):
    participants: List[ParticipantResponse] = []

    class Config:
        from_attributes = True
