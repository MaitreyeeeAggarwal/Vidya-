import os
import boto3
from fastapi import APIRouter, Header, HTTPException, Query, Request
from pydantic import BaseModel
from typing import List

router = APIRouter(prefix="/api/media/resumable", tags=["Resumable Media Storage"])

_s3_client = None

def _get_s3():
    global _s3_client
    if _s3_client is None:
        _s3_client = boto3.client(
            "s3",
            aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
            aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
            endpoint_url=os.getenv("S3_ENDPOINT_URL"),
        )
    return _s3_client

BUCKET = os.getenv("AUDIO_BUCKET_NAME", "vidya-classroom-vault")


# ── Models ───────────────────────────────────────────────────────────────

class InitUploadRequest(BaseModel):
    fileId: str
    sessionEventId: str
    fileName: str
    contentType: str  # "audio/ogg" or "image/jpeg"

class PartETag(BaseModel):
    PartNumber: int
    ETag: str

class CompleteUploadRequest(BaseModel):
    uploadId: str
    s3Key: str
    parts: List[PartETag]


# ── Step 1: Initialize a multipart upload ────────────────────────────────

@router.post("/initialize")
async def initialize_resumable_session(payload: InitUploadRequest):
    """Ask S3/MinIO to create a multipart upload tracking ID."""
    s3_key = f"classroom_assets/{payload.sessionEventId}/{payload.fileId}_{payload.fileName}"
    try:
        resp = _get_s3().create_multipart_upload(
            Bucket=BUCKET,
            Key=s3_key,
            ContentType=payload.contentType,
        )
        return {
            "uploadId": resp["UploadId"],
            "s3Key": s3_key,
            "status": "INITIALIZED",
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"S3 init failed: {e}")


# ── Step 2: Upload a single part ─────────────────────────────────────────

@router.post("/upload-part")
async def upload_file_part(
    request: Request,
    uploadId: str = Query(...),
    s3Key: str = Query(...),
    partNumber: int = Query(..., ge=1),
):
    """
    Stream an isolated byte block directly into the storage engine.

    The client sends the raw binary chunk as the request body
    (Content-Type: application/octet-stream).
    """
    chunk_data = await request.body()
    if not chunk_data:
        raise HTTPException(status_code=400, detail="Empty chunk body")

    try:
        resp = _get_s3().upload_part(
            Bucket=BUCKET,
            Key=s3Key,
            UploadId=uploadId,
            PartNumber=partNumber,
            Body=chunk_data,
        )
        return {
            "partNumber": partNumber,
            "ETag": resp["ETag"],
            "bytesReceived": len(chunk_data),
            "status": "ACK",
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Part upload failed: {e}")


# ── Step 3: Complete the multipart upload ────────────────────────────────

@router.post("/complete")
async def complete_resumable_upload(payload: CompleteUploadRequest):
    """
    Finalize the multipart upload after all parts are acknowledged.

    The client sends the ordered list of (PartNumber, ETag) pairs
    so S3 can stitch the object together.
    """
    try:
        _get_s3().complete_multipart_upload(
            Bucket=BUCKET,
            Key=payload.s3Key,
            UploadId=payload.uploadId,
            MultipartUpload={
                "Parts": [
                    {"PartNumber": p.PartNumber, "ETag": p.ETag}
                    for p in sorted(payload.parts, key=lambda x: x.PartNumber)
                ]
            },
        )
        return {
            "s3Key": payload.s3Key,
            "resourceUri": f"s3://{BUCKET}/{payload.s3Key}",
            "status": "COMPLETED",
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Complete failed: {e}")


# ── Step 4 (optional): Abort a stale upload ──────────────────────────────

@router.delete("/abort")
async def abort_resumable_upload(
    uploadId: str = Query(...),
    s3Key: str = Query(...),
):
    """Clean up an abandoned multipart upload to free S3 storage."""
    try:
        _get_s3().abort_multipart_upload(
            Bucket=BUCKET, Key=s3Key, UploadId=uploadId
        )
        return {"status": "ABORTED"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Abort failed: {e}")
