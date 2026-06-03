from pydantic import BaseModel
from datetime import datetime
from typing import Optional, Dict, Any, List

class DetectedFaceResponse(BaseModel):
    id: int
    media_id: int
    face_cluster_id: Optional[int] = None
    bounding_box: Dict[str, Any]  # {"x": int, "y": int, "w": int, "h": int}

    class Config:
        from_attributes = True

class MediaResponse(BaseModel):
    id: int
    event_id: str
    uploader_id: int
    storage_path: str
    file_name: str
    file_size: int
    content_type: str
    phash: Optional[str] = None
    metadata_json: Optional[Dict[str, Any]] = None
    created_at: datetime
    detected_faces: List[DetectedFaceResponse] = []

    class Config:
        from_attributes = True

class FaceClusterResponse(BaseModel):
    id: int
    event_id: str
    label: str
    representative_face_path: Optional[str] = None
    created_at: datetime

    class Config:
        from_attributes = True
