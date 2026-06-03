import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config import settings
from app.database import engine, Base
import app.models  # Registers all models with Base metadata

# Import routers
from app.routers import auth, event, media, websocket

# Create database tables automatically if they don't exist
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title=settings.APP_NAME,
    description="SYNRAX SS - AI-Powered Realtime Event Media Backend Infrastructure",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Configure CORS for frontend clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Adjust in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API Router Grouping
from fastapi import APIRouter
api_router = APIRouter(prefix="/api/v1")

api_router.include_router(auth.router)
api_router.include_router(event.router)
api_router.include_router(media.router)
api_router.include_router(websocket.router)

app.include_router(api_router)

# Mount local uploads directory to serve files statically
# Example: Local media path 'ABC123/media/file.png' is accessed at 'http://localhost:8000/uploads/ABC123/media/file.png'
if settings.STORAGE_PROVIDER == "local" and os.path.exists(settings.LOCAL_STORAGE_DIR):
    app.mount(
        "/uploads",
        StaticFiles(directory=settings.LOCAL_STORAGE_DIR),
        name="uploads"
    )

@app.get("/", tags=["Health Check"])
def root():
    """Simple root health check endpoint."""
    return {
        "status": "healthy",
        "app_name": settings.APP_NAME,
        "storage_provider": settings.STORAGE_PROVIDER,
        "version": "1.0.0"
    }
