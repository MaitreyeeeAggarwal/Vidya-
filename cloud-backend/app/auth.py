import os
from datetime import datetime, timedelta
from typing import Dict, Optional
from jose import jwt, JWTError
from fastapi import HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel
from .database import db

SECRET_KEY = os.getenv("JWT_SECRET_KEY", "your_production_secure_hmac_secret")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7  # 7 Days long-lived access token for low-sync field environments
REFRESH_TOKEN_EXPIRE_DAYS = 90

security_agent = HTTPBearer()

class TokenPayload(BaseModel):
    teacher_id: str
    device_id: str

def create_token_pair(teacher_id: str, device_id: str) -> Dict[str, str]:
    now = datetime.utcnow()
    
    access_claims = {
        "sub": teacher_id,
        "device_id": device_id,
        "exp": now + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
        "type": "access"
    }
    
    refresh_claims = {
        "sub": teacher_id,
        "device_id": device_id,
        "exp": now + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS),
        "type": "refresh"
    }
    
    return {
        "access_token": jwt.encode(access_claims, SECRET_KEY, algorithm=ALGORITHM),
        "refresh_token": jwt.encode(refresh_claims, SECRET_KEY, algorithm=ALGORITHM)
    }

async def verify_device_binding(credentials: HTTPAuthorizationCredentials = Security(security_agent)) -> TokenPayload:
    token = credentials.credentials
    error_state = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid token or broken device binding configuration.",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        teacher_id: str = payload.get("sub")
        device_id: str = payload.get("device_id")
        token_type: str = payload.get("type")
        
        if not teacher_id or not device_id or token_type != "access":
            raise error_state
            
        # Verify device against database
        device = await db.device.find_unique(where={"deviceId": device_id})
        if not device or not device.isSigned or device.teacherId != teacher_id:
            raise error_state
            
        return TokenPayload(teacher_id=teacher_id, device_id=device_id)
    except JWTError:
        raise error_state
