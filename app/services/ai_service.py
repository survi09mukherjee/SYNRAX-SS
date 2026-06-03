import io
import os
import cv2
import numpy as np
import imagehash
from PIL import Image, ExifTags
from sklearn.cluster import DBSCAN
from sqlalchemy.orm import Session
from typing import Dict, Any, List, Tuple
from app.models.media import Media, DetectedFace, FaceCluster
from app.config import settings

class AIService:
    @staticmethod
    def extract_metadata(image_bytes: bytes) -> Dict[str, Any]:
        """Extract EXIF metadata (dimensions, camera details, GPS, datetime) from image bytes."""
        metadata = {
            "width": 0,
            "height": 0,
            "camera_make": None,
            "camera_model": None,
            "date_taken": None,
            "gps_latitude": None,
            "gps_longitude": None,
            "raw_exif": {}
        }
        
        try:
            img = Image.open(io.BytesIO(image_bytes))
            metadata["width"] = img.width
            metadata["height"] = img.height
            
            exif_data = img._getexif()
            if not exif_data:
                return metadata
                
            for tag_id, value in exif_data.items():
                tag_name = ExifTags.TAGS.get(tag_id, tag_id)
                
                # Normalize values for JSON serialization
                if isinstance(value, bytes):
                    try:
                        value = value.decode('utf-8', errors='replace')
                    except Exception:
                        value = str(value)
                
                if tag_name == "Make":
                    metadata["camera_make"] = str(value).strip()
                elif tag_name == "Model":
                    metadata["camera_model"] = str(value).strip()
                elif tag_name == "DateTimeOriginal" or tag_name == "DateTime":
                    metadata["date_taken"] = str(value).strip()
                elif tag_name == "GPSInfo":
                    gps_info = {}
                    for g_key, g_val in value.items():
                        g_tag = ExifTags.GPSTAGS.get(g_key, g_key)
                        if isinstance(g_val, bytes):
                            g_val = g_val.decode('utf-8', errors='replace')
                        gps_info[str(g_tag)] = g_val
                    
                    metadata["raw_exif"]["GPSInfo"] = str(gps_info)
                    
                    # Try parsing coordinates to decimal degrees
                    try:
                        lat = gps_info.get("GPSLatitude")
                        lat_ref = gps_info.get("GPSLatitudeRef")
                        lon = gps_info.get("GPSLongitude")
                        lon_ref = gps_info.get("GPSLongitudeRef")
                        
                        if lat and lat_ref and lon and lon_ref:
                            # Convert rational tuples to float
                            def convert_to_degrees(value):
                                d = float(value[0])
                                m = float(value[1])
                                s = float(value[2])
                                return d + (m / 60.0) + (s / 3600.0)
                            
                            dec_lat = convert_to_degrees(lat)
                            if lat_ref != "N":
                                dec_lat = -dec_lat
                                
                            dec_lon = convert_to_degrees(lon)
                            if lon_ref != "E":
                                dec_lon = -dec_lon
                                
                            metadata["gps_latitude"] = dec_lat
                            metadata["gps_longitude"] = dec_lon
                    except Exception as e:
                        print(f"Failed to parse GPS: {str(e)}")
                else:
                    # Save other tags in raw EXIF dict
                    if tag_name not in ["MakerNote", "UserComment"]:
                        metadata["raw_exif"][str(tag_name)] = str(value)
                        
        except Exception as e:
            print(f"Error parsing metadata: {str(e)}")
            
        return metadata

    @staticmethod
    def calculate_phash(image_bytes: bytes) -> str:
        """Compute the perceptual hash (pHash) of an image."""
        try:
            img = Image.open(io.BytesIO(image_bytes))
            phash = imagehash.phash(img)
            return str(phash)
        except Exception as e:
            print(f"Error calculating pHash: {str(e)}")
            return ""

    @staticmethod
    def check_is_duplicate(db: Session, event_id: str, current_phash_str: str) -> Media:
        """Check if an image with similar pHash exists in the same event. Returns matching Media if duplicate, else None."""
        if not current_phash_str:
            return None
            
        current_phash = imagehash.hex_to_hash(current_phash_str)
        
        # Query all media in this event room
        existing_media = db.query(Media).filter(Media.event_id == event_id).all()
        
        for media in existing_media:
            if media.phash:
                try:
                    compare_phash = imagehash.hex_to_hash(media.phash)
                    # Hamming distance
                    distance = current_phash - compare_phash
                    if distance <= settings.DUPLICATE_THRESHOLD:
                        return media
                except Exception as e:
                    print(f"Error comparing pHash: {str(e)}")
                    
        return None

    @staticmethod
    def detect_faces(image_bytes: bytes) -> List[Tuple[Dict[str, int], List[float]]]:
        """
        Detect faces in image and extract lightweight normalized feature embeddings.
        Returns a list of tuples containing:
        - Bounding box dictionary: {"x": int, "y": int, "w": int, "h": int}
        - Feature embedding: List of 256 floats (grayscale template)
        """
        results = []
        try:
            # Decode image for OpenCV
            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                return []
                
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            
            # Load cascade classifier
            cascade_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            face_cascade = cv2.CascadeClassifier(cascade_path)
            
            # Detect faces
            faces = face_cascade.detectMultiScale(
                gray, 
                scaleFactor=1.1, 
                minNeighbors=4, 
                minSize=(30, 30)
            )
            
            for (x, y, w, h) in faces:
                bbox = {"x": int(x), "y": int(y), "w": int(w), "h": int(h)}
                
                # Crop and resize to 16x16 to get a lightweight, noise-resilient grayscale vector (256-D)
                face_crop = gray[y:y+h, x:x+w]
                face_resized = cv2.resize(face_crop, (16, 16), interpolation=cv2.INTER_AREA)
                
                # Normalize features (L2 normalization)
                vector = face_resized.flatten().astype(float)
                norm = np.linalg.norm(vector)
                if norm > 0:
                    vector = vector / norm
                    
                results.append((bbox, vector.tolist()))
                
        except Exception as e:
            print(f"Error in face detection: {str(e)}")
            
        return results

    @staticmethod
    def run_face_clustering(db: Session, event_id: str, storage_service) -> Dict[str, Any]:
        """
        Query all detected faces in the event, perform DBSCAN clustering,
        create/update FaceCluster records, and assign faces to clusters.
        """
        # Fetch all faces related to this event's media
        faces = db.query(DetectedFace).join(Media).filter(
            Media.event_id == event_id
        ).all()
        
        if len(faces) < 2:
            return {"message": "Not enough faces detected in this event to cluster yet (minimum 2 required)"}
            
        # Collect embeddings
        embeddings = [face.embedding_json for face in faces]
        X = np.array(embeddings)
        
        # Run DBSCAN (eps: distance threshold, min_samples: min faces to form a group)
        # Using cosine distance or euclidean distance since vectors are L2 normalized
        dbscan = DBSCAN(eps=0.3, min_samples=2, metric="euclidean")
        labels = dbscan.fit_predict(X)
        
        # Reset current face cluster assignments for recalculation
        # Delete old face clusters for this event (cascade sets cluster_id in face to NULL)
        old_clusters = db.query(FaceCluster).filter(FaceCluster.event_id == event_id).all()
        for oc in old_clusters:
            # Optionally delete face crop thumbnails from storage
            if oc.representative_face_path:
                storage_service.delete_file(oc.representative_face_path)
            db.delete(oc)
        db.flush()
        
        # Mapping labels to db cluster models
        label_to_db_cluster = {}
        unique_labels = set(labels)
        
        created_count = 0
        assigned_count = 0
        
        for label in unique_labels:
            if label == -1:
                # -1 represents noise in DBSCAN (unclustered/outlier faces)
                continue
                
            # Create a new FaceCluster for this group
            cluster_label = f"Person {created_count + 1}"
            
            # Find the first face in this cluster to crop as representative thumbnail
            cluster_face_index = np.where(labels == label)[0][0]
            rep_face_obj = faces[cluster_face_index]
            
            # Crop representative thumbnail
            rep_face_thumbnail_path = None
            try:
                # Read original media file bytes
                from app.database import SessionLocal
                media_file = rep_face_obj.media
                
                # Fetch image from storage service
                # We need to construct absolute path or read local file
                # To be general, if storage is local, we read file content
                if settings.STORAGE_PROVIDER == "local":
                    full_media_path = os.path.join(settings.LOCAL_STORAGE_DIR, media_file.storage_path)
                    with open(full_media_path, "rb") as f:
                        img_bytes = f.read()
                else:
                    # For cloud storage, we would download, but here we fall back to standard local read/stub
                    img_bytes = None
                
                if img_bytes:
                    nparr = np.frombuffer(img_bytes, np.uint8)
                    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
                    
                    bbox = rep_face_obj.bounding_box
                    x, y, w, h = bbox["x"], bbox["y"], bbox["w"], bbox["h"]
                    crop = img[y:y+h, x:x+w]
                    
                    # Convert crop to png bytes
                    _, encoded_img = cv2.imencode(".png", crop)
                    crop_bytes = encoded_img.tobytes()
                    
                    # Save thumbnail to storage
                    rep_face_thumbnail_path = storage_service.save_file(
                        crop_bytes, 
                        f"face_thumb_{rep_face_obj.id}.png", 
                        f"{event_id}/faces"
                    )
            except Exception as e:
                print(f"Failed to generate face thumbnail: {str(e)}")
            
            db_cluster = FaceCluster(
                event_id=event_id,
                label=cluster_label,
                representative_face_path=rep_face_thumbnail_path
            )
            db.add(db_cluster)
            db.flush()
            
            label_to_db_cluster[label] = db_cluster.id
            created_count += 1
            
        # Assign cluster ids to faces
        for face_obj, label in zip(faces, labels):
            if label != -1:
                face_obj.face_cluster_id = label_to_db_cluster[label]
                assigned_count += 1
                
        db.commit()
        return {
            "clusters_created": created_count,
            "faces_grouped": assigned_count,
            "total_faces": len(faces)
        }
