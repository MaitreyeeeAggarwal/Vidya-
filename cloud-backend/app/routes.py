import random
from datetime import datetime, timedelta
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from .auth import create_token_pair, verify_device_binding, TokenPayload
from .database import db
from .services.sms import send_otp_sms

router = APIRouter(prefix="/api/auth", tags=["Authentication"])

class OtpRequest(BaseModel):
    phone_number: str

class VerificationRequest(BaseModel):
    phone_number: str
    otp: str
    device_id: str
    device_model: Optional[str] = None

class SilentLoginRequest(BaseModel):
    device_id: str

@router.post("/request-otp")
async def send_otp(payload: OtpRequest):
    # Standard Indian telecom configuration format validation
    if not payload.phone_number.startswith("+91") or len(payload.phone_number) != 13:
        raise HTTPException(status_code=400, detail="Invalid phone format. Core Indian +91 format missing.")
    
    # Secure numeric tracking generation
    generated_otp = str(random.randint(100000, 999999))
    expiry = datetime.utcnow() + timedelta(minutes=5)
    
    # Upsert the OTP verification record
    await db.otpverification.upsert(
        where={"phoneNumber": payload.phone_number},
        data={
            "update": {"otpHash": generated_otp, "expiresAt": expiry},
            "create": {"phoneNumber": payload.phone_number, "otpHash": generated_otp, "expiresAt": expiry}
        }
    )
    
    # Dispatch OTP via MSG91
    success = await send_otp_sms(payload.phone_number[3:], generated_otp)
    if not success:
        raise HTTPException(status_code=500, detail="Failed to dispatch OTP via carrier network.")

    return {"message": "OTP dispatched successfully through carrier network channels."}

@router.post("/verify-otp")
async def verify_otp(payload: VerificationRequest):
    record = await db.otpverification.find_unique(where={"phoneNumber": payload.phone_number})
    if not record or record.otpHash != payload.otp or record.expiresAt < datetime.utcnow():
        raise HTTPException(status_code=400, detail="OTP expired or incorrect.")
    
    teacher = await db.teacher.find_unique(where={"phoneNumber": payload.phone_number})
    if not teacher:
        # Create profile if missing
        teacher = await db.teacher.create(data={
            "phoneNumber": payload.phone_number,
            "fullName": "New Teacher"
        })
        
    device = await db.device.find_unique(where={"deviceId": payload.device_id})
    if not device:
        await db.device.create(data={
            "deviceId": payload.device_id,
            "deviceModel": payload.device_model,
            "teacherId": teacher.id
        })
    elif device.teacherId != teacher.id:
        raise HTTPException(status_code=403, detail="Hardware node bound to another teacher profile context.")
        
    await db.otpverification.delete(where={"phoneNumber": payload.phone_number})
    
    tokens = create_token_pair(teacher_id=teacher.id, device_id=payload.device_id)
    return tokens

@router.post("/silent-login")
async def silent_device_login(payload: SilentLoginRequest):
    device = await db.device.find_unique(
        where={"deviceId": payload.device_id},
        include={"teacher": True}
    )
    
    if not device or not device.isSigned or not device.teacher.isActive:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Silent authentication rejected. Re-verification required."
        )
        
    tokens = create_token_pair(teacher_id=device.teacher.id, device_id=payload.device_id)
    return tokens

@router.get("/secure-sync-check")
async def secure_session_validation(claims: TokenPayload = Depends(verify_device_binding)):
    # This route protects ingestion points from out-of-sync cross-device poisoning
    return {"status": "authorized", "teacher_id": claims.teacher_id, "device_locked": claims.device_id}
