import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

class Settings(BaseSettings):
    APP_NAME: str = "SYNRAX_SS"
    DEBUG: bool = True
    HOST: str = "127.0.0.1"
    PORT: int = 8000
    
    # Auth
    SECRET_KEY: str = "9a15f9b4de8e7bc9f1c7d23a49e67d26bb87d89abfd3f295e8dc9c049e623db2"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440
    
    # DB
    DATABASE_URL: str = "sqlite:///./synrax_ss.db"
    
    # Storage
    STORAGE_PROVIDER: str = "local"  # local or firebase
    LOCAL_STORAGE_DIR: str = "./uploads"
    
    # Firebase
    FIREBASE_STORAGE_BUCKET: str = "synrax-ss.appspot.com"
    FIREBASE_CREDENTIALS_JSON: str = ""
    
    # AI settings
    DUPLICATE_THRESHOLD: int = 4
    FACE_CONFIDENCE_THRESHOLD: float = 0.5

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()

# Ensure local storage directory exists
if settings.STORAGE_PROVIDER == "local":
    os.makedirs(settings.LOCAL_STORAGE_DIR, exist_ok=True)
