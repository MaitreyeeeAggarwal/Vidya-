import os
import httpx
import logging

logger = logging.getLogger(__name__)

async def send_otp_sms(phone: str, otp: str) -> bool:
    if os.getenv("MOCK_SMS", "true").lower() == "true":
        logger.warning(f"[MOCK SMS] Phone: {phone}  OTP: {str(otp)}")
        return True

    auth_key   = os.getenv("MSG91_AUTH_KEY")
    sender_id  = os.getenv("MSG91_SENDER_ID", "VIDYA")
    template_id = os.getenv("MSG91_TEMPLATE_ID")

    payload = {
        "template_id": template_id,
        "short_url":   "0",
        "realTimeResponse": "1",
        "recipients": [{"mobiles": f"91{phone}", "otp": otp}]
    }

    async with httpx.AsyncClient() as client:
        response = await client.post(
            "https://api.msg91.com/api/v5/otp",
            json=payload,
            headers={"authkey": auth_key, "Content-Type": "application/json"},
        )

    if response.status_code == 200:
        return True

    logger.error(f"MSG91 dispatch failed: {response.text}")
    return False
