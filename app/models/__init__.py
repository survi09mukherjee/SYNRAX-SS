from app.database import Base
from app.models.user import User
from app.models.event import Event
from app.models.participant import Participant
from app.models.media import Media, FaceCluster, DetectedFace

# Expose Base metadata for migrations/creation
metadata = Base.metadata
