from fastapi import APIRouter, UploadFile, File, Header, HTTPException, Depends
from ..auth import verify_device_binding, TokenPayload
import boto3
import os

router = APIRouter(prefix="/api/media", tags=["Media"])

S3_CLIENT = None

def _get_s3_client():
    """Lazy-init the S3/MinIO client from environment variables."""
    global S3_CLIENT
    if S3_CLIENT is None:
        S3_CLIENT = boto3.client(
            "s3",
            aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
            aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
            endpoint_url=os.getenv("OBJECT_STORAGE_ENDPOINT"),  # MinIO or S3-compatible
        )
    return S3_CLIENT

BUCKET_NAME = os.getenv("AUDIO_BUCKET_NAME", "vidya-classroom-audio-vault")


@router.post("/sync-chunk/{session_event_id}")
async def upload_classroom_audio_chunk(
    session_event_id: str,
    file: UploadFile = File(...),
    token_payload: TokenPayload = Depends(verify_device_binding),
):
    """
    Receive a single audio chunk from a syncing tablet.

    The device sends:
      - The session_event_id as a path parameter.
      - The raw audio file as multipart form data.
      - A JWT in the Authorization header.

    The file is streamed directly to object storage without buffering
    the entire payload in memory.
    """

    s3_key = f"audio/events/{session_event_id}/{file.filename}"

    try:
        client = _get_s3_client()
        client.upload_fileobj(
            file.file,
            BUCKET_NAME,
            s3_key,
            ExtraArgs={"ContentType": file.content_type or "audio/ogg"},
        )
        return {
            "status": "success",
            "resource_uri": f"s3://{BUCKET_NAME}/{s3_key}",
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to upload audio chunk: {str(e)}",
        )
