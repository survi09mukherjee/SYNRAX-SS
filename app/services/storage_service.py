import os
import uuid
from abc import ABC, abstractmethod
from app.config import settings

class BaseStorageService(ABC):
    @abstractmethod
    def save_file(self, content: bytes, file_name: str, subfolder: str) -> str:
        """Save file bytes to storage and return the public URL or relative file path."""
        pass

    @abstractmethod
    def delete_file(self, storage_path: str) -> bool:
        """Delete a file from storage. Returns True if successful, False otherwise."""
        pass


class LocalStorageService(BaseStorageService):
    def __init__(self, base_dir: str = None):
        self.base_dir = base_dir or settings.LOCAL_STORAGE_DIR
        os.makedirs(self.base_dir, exist_ok=True)

    def save_file(self, content: bytes, file_name: str, subfolder: str) -> str:
        # Create subfolder path (e.g. event_id folder)
        target_dir = os.path.join(self.base_dir, subfolder)
        os.makedirs(target_dir, exist_ok=True)
        
        # Generate unique filename to prevent collisions
        unique_name = f"{uuid.uuid4().hex}_{file_name}"
        file_path = os.path.join(target_dir, unique_name)
        
        # Write bytes
        with open(file_path, "wb") as f:
            f.write(content)
            
        # Return path normalized with forward slashes for cross-platform URL compatibility
        relative_path = os.path.relpath(file_path, self.base_dir)
        return relative_path.replace("\\", "/")

    def delete_file(self, storage_path: str) -> bool:
        full_path = os.path.join(self.base_dir, storage_path)
        if os.path.exists(full_path):
            try:
                os.remove(full_path)
                return True
            except OSError:
                return False
        return False


class FirebaseStorageService(BaseStorageService):
    def __init__(self):
        self.bucket_name = settings.FIREBASE_STORAGE_BUCKET
        self.credentials_json = settings.FIREBASE_CREDENTIALS_JSON
        self._initialized = False
        
        if not self.credentials_json:
            # Fallback to local storage if credentials are missing
            print("WARNING: Firebase credentials not provided. Falling back to Local Storage.")
            self.fallback_service = LocalStorageService()
            return
            
        try:
            import firebase_admin
            from firebase_admin import credentials, storage
            
            # Prevent double initialization of firebase app
            if not firebase_admin._apps:
                import json
                cred_dict = json.loads(self.credentials_json)
                cred = credentials.Certificate(cred_dict)
                firebase_admin.initialize_app(cred, {
                    'storageBucket': self.bucket_name
                })
            self._initialized = True
        except Exception as e:
            print(f"ERROR: Failed to initialize Firebase Storage: {str(e)}. Falling back to Local Storage.")
            self.fallback_service = LocalStorageService()

    def save_file(self, content: bytes, file_name: str, subfolder: str) -> str:
        if not self._initialized:
            return self.fallback_service.save_file(content, file_name, subfolder)
            
        import firebase_admin
        from firebase_admin import storage
        
        bucket = storage.bucket()
        unique_name = f"{uuid.uuid4().hex}_{file_name}"
        blob_path = f"{subfolder}/{unique_name}"
        blob = bucket.blob(blob_path)
        
        # Upload content
        blob.upload_from_string(content)
        # Make it public or return public link (or sign URL)
        blob.make_public()
        return blob.public_url

    def delete_file(self, storage_path: str) -> bool:
        if not self._initialized:
            return self.fallback_service.delete_file(storage_path)
            
        import firebase_admin
        from firebase_admin import storage
        
        try:
            bucket = storage.bucket()
            # If path is full URL, extract blob name
            if storage_path.startswith("http"):
                # URL structure: https://storage.googleapis.com/<bucket>/<blob_name>
                prefix = f"https://storage.googleapis.com/{self.bucket_name}/"
                if storage_path.startswith(prefix):
                    blob_name = storage_path[len(prefix):]
                else:
                    return False
            else:
                blob_name = storage_path
                
            blob = bucket.blob(blob_name)
            blob.delete()
            return True
        except Exception:
            return False


def get_storage_service() -> BaseStorageService:
    """Factory dependency to resolve the configured Storage Service."""
    if settings.STORAGE_PROVIDER.lower() == "firebase":
        return FirebaseStorageService()
    return LocalStorageService()
