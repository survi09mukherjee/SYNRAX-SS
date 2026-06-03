from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, JSON
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base

class Media(Base):
    __tablename__ = "media"

    id = Column(Integer, primary_key=True, index=True)
    event_id = Column(String, ForeignKey("events.id", ondelete="CASCADE"), nullable=False)
    uploader_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    storage_path = Column(String, nullable=False)
    file_name = Column(String, nullable=False)
    file_size = Column(Integer, nullable=False)
    content_type = Column(String, nullable=False)
    phash = Column(String, index=True, nullable=True)  # For duplicate detection
    metadata_json = Column(JSON, nullable=True)  # Storing GPS, camera model, date taken, etc.
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    event = relationship("Event", back_populates="media_files")
    uploader = relationship("User", back_populates="media_uploads")
    detected_faces = relationship("DetectedFace", back_populates="media", cascade="all, delete-orphan")


class FaceCluster(Base):
    __tablename__ = "face_clusters"

    id = Column(Integer, primary_key=True, index=True)
    event_id = Column(String, ForeignKey("events.id", ondelete="CASCADE"), nullable=False)
    label = Column(String, nullable=False)  # e.g., "Person A", "Person B"
    representative_face_path = Column(String, nullable=True)  # Cropped face thumbnail path
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    event = relationship("Event", back_populates="face_clusters")
    faces = relationship("DetectedFace", back_populates="cluster")


class DetectedFace(Base):
    __tablename__ = "detected_faces"

    id = Column(Integer, primary_key=True, index=True)
    media_id = Column(Integer, ForeignKey("media.id", ondelete="CASCADE"), nullable=False)
    face_cluster_id = Column(Integer, ForeignKey("face_clusters.id", ondelete="SET NULL"), nullable=True)
    bounding_box = Column(JSON, nullable=False)  # {"x": 10, "y": 20, "w": 50, "h": 50}
    embedding_json = Column(JSON, nullable=False)  # Vector representation as list of floats
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    media = relationship("Media", back_populates="detected_faces")
    cluster = relationship("FaceCluster", back_populates="faces")
