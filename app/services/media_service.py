from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from typing import List
from app.models.media import Media, DetectedFace, FaceCluster
from app.models.participant import Participant
from app.models.event import Event
from app.services.event_service import EventService
from app.services.ai_service import AIService
from app.services.storage_service import BaseStorageService

class MediaService:
    @staticmethod
    def upload_media(
        db: Session,
        file_bytes: bytes,
        file_name: str,
        content_type: str,
        event_id: str,
        uploader_id: int,
        storage_service: BaseStorageService
    ) -> Media:
        """
        Upload media workflow:
        1. Validate event room accessibility
        2. Validate user participation
        3. Check for near-duplicate images using pHash
        4. Upload original file to storage
        5. Extract EXIF metadata
        6. Detect faces and register embeddings
        7. Recalculate DBSCAN face clusters for the event
        """
        # 1 & 2. Verify event is active and user is joined
        EventService.get_active_event(db, event_id)
        is_member = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == uploader_id
        ).first()
        
        if not is_member:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must join the event room before uploading media"
            )
            
        # 3. Check for duplicates
        phash_val = AIService.calculate_phash(file_bytes)
        duplicate = AIService.check_is_duplicate(db, event_id, phash_val)
        if duplicate:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail={
                    "message": "Duplicate image detected. File already exists in this event room.",
                    "existing_media_id": duplicate.id,
                    "existing_storage_path": duplicate.storage_path
                }
            )
            
        # 4. Save file to storage
        storage_path = storage_service.save_file(
            file_bytes,
            file_name,
            f"{event_id}/media"
        )
        
        # 5. Extract metadata
        metadata = AIService.extract_metadata(file_bytes)
        
        # 6. Save media record to get ID
        db_media = Media(
            event_id=event_id,
            uploader_id=uploader_id,
            storage_path=storage_path,
            file_name=file_name,
            file_size=len(file_bytes),
            content_type=content_type,
            phash=phash_val,
            metadata_json=metadata
        )
        db.add(db_media)
        db.flush()  # Populates db_media.id
        
        # Detect faces
        faces = AIService.detect_faces(file_bytes)
        for bbox, embedding in faces:
            db_face = DetectedFace(
                media_id=db_media.id,
                bounding_box=bbox,
                embedding_json=embedding
            )
            db.add(db_face)
            
        db.commit()
        db.refresh(db_media)
        
        # 7. Recalculate face clusters for this event room dynamically
        try:
            AIService.run_face_clustering(db, event_id, storage_service)
            db.refresh(db_media)  # Refresh relationships after clustering updates
        except Exception as e:
            print(f"Non-critical Error during face clustering recalculation: {str(e)}")
            
        return db_media

    @staticmethod
    def get_event_media(db: Session, event_id: str, user_id: int) -> List[Media]:
        """Retrieve all media uploaded to an event room, validating user participation."""
        EventService.get_active_event(db, event_id)
        
        is_member = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == user_id
        ).first()
        if not is_member:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You must join the event room to view its media"
            )
            
        return db.query(Media).filter(Media.event_id == event_id).order_by(Media.created_at.desc()).all()

    @staticmethod
    def get_face_clusters(db: Session, event_id: str, user_id: int) -> List[FaceCluster]:
        """Fetch all face clusters identified in the event room."""
        EventService.get_active_event(db, event_id)
        
        is_member = db.query(Participant).filter(
            Participant.event_id == event_id,
            Participant.user_id == user_id
        ).first()
        if not is_member:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied"
            )
            
        return db.query(FaceCluster).filter(FaceCluster.event_id == event_id).all()

    @staticmethod
    def get_media_by_cluster(db: Session, cluster_id: int, user_id: int) -> List[Media]:
        """Fetch all media matching a specific face cluster (Person group)."""
        cluster = db.query(FaceCluster).filter(FaceCluster.id == cluster_id).first()
        if not cluster:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Face cluster not found"
            )
            
        # Verify user is a participant of the cluster's event
        is_member = db.query(Participant).filter(
            Participant.event_id == cluster.event_id,
            Participant.user_id == user_id
        ).first()
        if not is_member:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied to event media"
            )
            
        # Retrieve all media containing a face belonging to this cluster
        return db.query(Media).join(DetectedFace).filter(
            DetectedFace.face_cluster_id == cluster_id
        ).all()

    @staticmethod
    def delete_media(
        db: Session,
        media_id: int,
        user_id: int,
        storage_service: BaseStorageService
    ) -> bool:
        """Delete media from db and storage. Allowed only for uploader or room creator."""
        media = db.query(Media).filter(Media.id == media_id).first()
        if not media:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Media file not found"
            )
            
        event = db.query(Event).filter(Event.id == media.event_id).first()
        
        # Check permissions
        if media.uploader_id != user_id and event.creator_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have permission to delete this media file"
            )
            
        # Delete file from storage
        storage_service.delete_file(media.storage_path)
        
        # Delete from DB (cascades to DetectedFace)
        db.delete(media)
        db.commit()
        
        # Trigger face clustering recalculation since a media file was removed
        try:
            AIService.run_face_clustering(db, event.id, storage_service)
        except Exception as e:
            print(f"Non-critical Error during face clustering recalculation: {str(e)}")
            
        return True
