from fastapi import APIRouter, Depends, UploadFile, File, Form, status
from sqlalchemy.orm import Session
from typing import List
from app.database import get_db
from app.models.user import User
from app.schemas.media import MediaResponse, FaceClusterResponse
from app.services.auth_service import get_current_user
from app.services.media_service import MediaService
from app.services.storage_service import get_storage_service, BaseStorageService
from app.utils.websocket_manager import manager

router = APIRouter(prefix="/media", tags=["Media Operations"])

@router.post("/upload", response_model=MediaResponse, status_code=status.HTTP_201_CREATED)
async def upload_media(
    event_id: str = Form(..., description="The unique code of the room"),
    file: UploadFile = File(..., description="Photo or video to upload"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
    storage_service: BaseStorageService = Depends(get_storage_service)
):
    """
    Upload a media asset to the event room.
    Executes duplicate detection, EXIF extraction, face grouping,
    and broadcasts the upload to WebSocket listeners in real-time.
    """
    file_bytes = await file.read()
    
    # Process upload via service layer
    media = MediaService.upload_media(
        db=db,
        file_bytes=file_bytes,
        file_name=file.filename,
        content_type=file.content_type,
        event_id=event_id,
        uploader_id=current_user.id,
        storage_service=storage_service
    )
    
    # Structure websocket broadcast payload
    # Must be JSON serializable
    ws_payload = {
        "event_type": "MEDIA_UPLOADED",
        "data": {
            "id": media.id,
            "event_id": media.event_id,
            "uploader_id": media.uploader_id,
            "storage_path": media.storage_path,
            "file_name": media.file_name,
            "content_type": media.content_type,
            "file_size": media.file_size,
            "created_at": media.created_at.isoformat(),
            "metadata": media.metadata_json
        }
    }
    
    # Trigger WebSocket real-time room notification
    await manager.broadcast_to_room(event_id, ws_payload)
    
    return media

@router.get("/event/{event_id}", response_model=List[MediaResponse])
def get_event_media(
    event_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Fetch all media uploaded in an event room."""
    return MediaService.get_event_media(db, event_id, current_user.id)

@router.get("/faces/{event_id}", response_model=List[FaceClusterResponse])
def get_event_face_clusters(
    event_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Fetch all detected face groupings/clusters for an event room."""
    return MediaService.get_face_clusters(db, event_id, current_user.id)

@router.get("/faces/cluster/{cluster_id}", response_model=List[MediaResponse])
def get_media_by_face_cluster(
    cluster_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Fetch all photos that contain a specific person/face group."""
    return MediaService.get_media_by_cluster(db, cluster_id, current_user.id)

@router.delete("/{media_id}", status_code=status.HTTP_200_OK)
def delete_media(
    media_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
    storage_service: BaseStorageService = Depends(get_storage_service)
):
    """Delete a media asset from the event. Can only be done by the uploader or the room creator."""
    MediaService.delete_media(db, media_id, current_user.id, storage_service)
    return {"message": "Media asset deleted successfully"}
