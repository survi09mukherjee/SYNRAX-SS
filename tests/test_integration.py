import os
import io
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from PIL import Image, ImageDraw

# Setup mock/temp environment variable before importing config
os.environ["DATABASE_URL"] = "sqlite:///./test_synrax_ss.db"
os.environ["STORAGE_PROVIDER"] = "local"
os.environ["LOCAL_STORAGE_DIR"] = "./test_uploads"

from app.main import app
from app.database import Base, get_db
from app.config import settings

# Setup a test database engine
TEST_DATABASE_URL = "sqlite:///./test_synrax_ss.db"
engine = create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

# Override FastAPI database dependency
app.dependency_overrides[get_db] = override_get_db

client = TestClient(app)

def setup_module(module):
    # Ensure tables are created in the test DB
    Base.metadata.create_all(bind=engine)
    # Ensure test upload folder exists
    os.makedirs("./test_uploads", exist_ok=True)

def teardown_module(module):
    # Clean up test database tables and files
    Base.metadata.drop_all(bind=engine)
    engine.dispose()
    
    # Dispose of the original application engine to release file lock on Windows
    try:
        from app.database import engine as app_engine
        app_engine.dispose()
    except Exception:
        pass
    
    # Remove database file
    if os.path.exists("test_synrax_ss.db"):
        try:
            os.remove("test_synrax_ss.db")
        except PermissionError:
            print("Warning: Database file is still locked by Windows, skipping deletion.")
        
    # Clean up test uploads directory
    if os.path.exists("./test_uploads"):
        import shutil
        shutil.rmtree("./test_uploads")

def generate_test_image(color="blue") -> bytes:
    """Generate a simple dummy image in memory and return JPEG bytes with distinct visual shapes."""
    img = Image.new("RGB", (100, 100), color=color)
    draw = ImageDraw.Draw(img)
    if color == "blue":
        draw.rectangle([10, 10, 90, 90], fill="white", outline="yellow")
        draw.line([0, 0, 100, 100], fill="green", width=5)
    else:
        draw.ellipse([20, 20, 80, 80], fill="black", outline="red")
        draw.line([100, 0, 0, 100], fill="pink", width=3)
        
    # Add dummy EXIF info
    exif = img.getexif()
    # 271 is Make, 272 is Model in EXIF standard
    exif[271] = "TestCamera"
    exif[272] = "Model-X"
    
    buffered = io.BytesIO()
    img.save(buffered, format="JPEG", exif=exif)
    return buffered.getvalue()

def test_integration_flow():
    # 1. Signup user
    signup_data = {
        "email": "testuser@synrax.com",
        "password": "securepassword123",
        "full_name": "Test User"
    }
    response = client.post("/api/v1/auth/signup", json=signup_data)
    assert response.status_code == 201, response.text
    user_json = response.json()
    assert user_json["email"] == "testuser@synrax.com"
    assert user_json["full_name"] == "Test User"
    assert "id" in user_json

    # 2. Login user
    login_data = {
        "email": "testuser@synrax.com",
        "password": "securepassword123"
    }
    response = client.post("/api/v1/auth/login", json=login_data)
    assert response.status_code == 200, response.text
    token_json = response.json()
    assert "access_token" in token_json
    token = token_json["access_token"]
    
    headers = {"Authorization": f"Bearer {token}"}

    # 3. Fetch profile
    response = client.get("/api/v1/auth/me", headers=headers)
    assert response.status_code == 200, response.text
    me_json = response.json()
    assert me_json["email"] == "testuser@synrax.com"

    # 4. Create an event room
    event_data = {
        "name": "Hackathon 2026",
        "description": "24-hour building fest",
        "passcode": "hack123",
        "duration_hours": 12
    }
    response = client.post("/api/v1/events/create", json=event_data, headers=headers)
    assert response.status_code == 201, response.text
    event_json = response.json()
    assert event_json["name"] == "Hackathon 2026"
    assert event_json["passcode"] == "hack123"
    event_id = event_json["id"]
    qr_payload = event_json["qr_payload"]
    assert len(event_id) == 6

    # 5. Fetch QR code
    response = client.get(f"/api/v1/events/{event_id}/qr", headers=headers)
    assert response.status_code == 200, response.text
    qr_json = response.json()
    assert "qr_code_image" in qr_json
    assert qr_json["qr_payload"] == qr_payload

    # 6. Fetch details (creator is automatically a participant)
    response = client.get(f"/api/v1/events/{event_id}", headers=headers)
    assert response.status_code == 200, response.text
    details_json = response.json()
    assert len(details_json["participants"]) == 1
    assert details_json["participants"][0]["email"] == "testuser@synrax.com"
    assert details_json["participants"][0]["role"] == "creator"

    # 7. Upload first image
    image_bytes = generate_test_image("blue")
    response = client.post(
        "/api/v1/media/upload",
        data={"event_id": event_id},
        files={"file": ("test_blue.jpg", image_bytes, "image/jpeg")},
        headers=headers
    )
    assert response.status_code == 201, response.text
    media_json = response.json()
    assert media_json["file_name"] == "test_blue.jpg"
    assert media_json["content_type"] == "image/jpeg"
    media_id = media_json["id"]
    
    # Assert EXIF parsing
    metadata = media_json["metadata_json"]
    assert metadata["camera_make"] == "TestCamera"
    assert metadata["camera_model"] == "Model-X"

    # 8. Upload DUPLICATE image (should fail with 409 Conflict)
    response = client.post(
        "/api/v1/media/upload",
        data={"event_id": event_id},
        files={"file": ("duplicate_blue.jpg", image_bytes, "image/jpeg")},
        headers=headers
    )
    assert response.status_code == 409, response.text
    dup_json = response.json()
    assert "Duplicate image detected" in dup_json["detail"]["message"]
    assert dup_json["detail"]["existing_media_id"] == media_id

    # 9. Upload different image (should succeed)
    other_image_bytes = generate_test_image("red")
    response = client.post(
        "/api/v1/media/upload",
        data={"event_id": event_id},
        files={"file": ("test_red.jpg", other_image_bytes, "image/jpeg")},
        headers=headers
    )
    assert response.status_code == 201, response.text
    other_media_json = response.json()
    other_media_id = other_media_json["id"]

    # 10. Fetch event media
    response = client.get(f"/api/v1/media/event/{event_id}", headers=headers)
    assert response.status_code == 200, response.text
    media_list = response.json()
    # List is sorted desc, so red is index 0, blue is index 1
    assert len(media_list) == 2
    assert media_list[0]["id"] == other_media_id
    assert media_list[1]["id"] == media_id

    # 11. Delete media
    response = client.delete(f"/api/v1/media/{media_id}", headers=headers)
    assert response.status_code == 200, response.text
    assert response.json()["message"] == "Media asset deleted successfully"

    # 12. Verify deleted media is gone
    response = client.get(f"/api/v1/media/event/{event_id}", headers=headers)
    assert response.status_code == 200, response.text
    media_list_after = response.json()
    assert len(media_list_after) == 1
    assert media_list_after[0]["id"] == other_media_id
