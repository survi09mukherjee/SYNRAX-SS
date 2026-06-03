import qrcode
import io
import base64
import json
from hmac import HMAC
from hashlib import sha256
from app.config import settings

class QRService:
    @staticmethod
    def generate_payload(event_id: str, passcode: str = "") -> str:
        """Create a JSON payload for the QR code and add an HMAC signature to prevent tempering."""
        data = {
            "event_id": event_id,
            "passcode": passcode or ""
        }
        # Generate signature
        data_str = json.dumps(data, sort_keys=True)
        signature = HMAC(
            settings.SECRET_KEY.encode(),
            data_str.encode(),
            sha256
        ).hexdigest()
        
        # Combine signature and data
        payload = {
            "data": data,
            "signature": signature
        }
        return json.dumps(payload)

    @staticmethod
    def verify_payload(payload_str: str) -> dict:
        """Verify the HMAC signature of the QR code payload. Returns the payload data if valid, otherwise raises value error."""
        try:
            payload = json.loads(payload_str)
            data = payload["data"]
            signature = payload["signature"]
            
            # Recreate signature and compare
            data_str = json.dumps(data, sort_keys=True)
            expected_signature = HMAC(
                settings.SECRET_KEY.encode(),
                data_str.encode(),
                sha256
            ).hexdigest()
            
            if not HMAC(settings.SECRET_KEY.encode(), expected_signature.encode(), sha256).compare_digest(
                expected_signature.encode(), signature.encode()
            ):
                raise ValueError("Signature verification failed")
            
            return data
        except Exception as e:
            raise ValueError(f"Invalid QR code payload structure: {str(e)}")

    @staticmethod
    def generate_qr_image_base64(payload_str: str) -> str:
        """Generate a QR code image as a base64 encoded PNG string."""
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=10,
            border=4,
        )
        qr.add_data(payload_str)
        qr.make(fit=True)

        img = qr.make_image(fill_color="black", back_color="white")
        
        # Save image to byte stream
        buffered = io.BytesIO()
        img.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode()
        
        return f"data:image/png;base64,{img_str}"
