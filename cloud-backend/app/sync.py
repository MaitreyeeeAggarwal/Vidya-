from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
from .database import db

router = APIRouter(prefix="/api/sync", tags=["Sync"])

class EventLogPayload(BaseModel):
    id: str
    eventType: str
    timestamp: datetime
    payload: str
    language_code: Optional[str] = None
    transcript: Optional[str] = None

class SessionSyncPayload(BaseModel):
    id: str
    teacherId: str
    classId: str
    chapterId: str
    state: str
    startedAt: datetime
    endedAt: Optional[datetime] = None
    events: List[EventLogPayload]

@router.post("/flush-classroom-sessions")
async def ingest_offline_sessions(payload: List[SessionSyncPayload]):
    try:
        # Utilizing a transaction to ensure all sessions and their events are successfully inserted or rolled back
        async with db.tx() as tx:
            for session in payload:
                # Upsert session
                await tx.session.upsert(
                    where={"id": session.id},
                    data={
                        "create": {
                            "id": session.id,
                            "teacherId": session.teacherId,
                            "classId": session.classId,
                            "chapterId": session.chapterId,
                            "state": session.state,
                            "startedAt": session.startedAt,
                            "endedAt": session.endedAt,
                            "syncState": True
                        },
                        "update": {
                            "state": session.state,
                            "endedAt": session.endedAt,
                            "syncState": True
                        }
                    }
                )

                # Bulk insert events for this session
                # If an event with same ID already exists, it would conflict on insert.
                # Since events are append-only and UUIDv7, we assume they are new.
                # For safety against duplicate syncs, we can use createMany with skip_duplicates if available
                # Prisma Python `create_many` supports `skip_duplicates`
                if session.events:
                    await tx.sessionevent.create_many(
                        data=[
                            {
                                "id": event.id,
                                "sessionId": session.id,
                                "eventType": event.eventType,
                                "timestamp": event.timestamp,
                                "payload": event.payload
                            }
                            for event in session.events
                        ],
                        skip_duplicates=True
                    )
        
        return {"status": "success", "synced_records_count": len(payload)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Data synchronization crash: {str(e)}")


# ── Idempotent Delta Sync (append-only events) ──────────────────────────

class EventSyncItem(BaseModel):
    """Single event from the on-device append-only log."""
    id: str
    sessionId: str
    eventType: str
    timestamp: datetime
    payload: str
    language_code: Optional[str] = None
    transcript: Optional[str] = None

class SyncAcknowledgement(BaseModel):
    """Per-event server acknowledgement returned to the client."""
    client_event_id: str
    server_sequence_id: int
    status: str  # "COMPLETED" (new insert) or "EXISTED" (duplicate, skipped)


@router.post("/events", response_model=List[SyncAcknowledgement])
async def ingest_classroom_events(payload: List[EventSyncItem]):
    """
    Idempotent batch event ingestion.

    The client uploads un-synced events in bulk. Each event carries a
    globally unique UUIDv7 generated on-device, so duplicate detection
    is trivial: if the ID already exists in Postgres, skip it.

    Returns a per-event ACK so the client can mark each row as synced
    only after confirmed server receipt.
    """
    if not payload:
        return []

    ack_list = []

    try:
        async with db.tx() as tx:
            for item in payload:
                # Check if this event already exists (idempotency guard)
                existing = await tx.sessionevent.find_unique(
                    where={"id": item.id}
                )

                if existing:
                    # Duplicate from a retry — skip safely
                    ack_list.append(SyncAcknowledgement(
                        client_event_id=item.id,
                        server_sequence_id=0,
                        status="EXISTED"
                    ))
                    continue

                # Insert new event
                created = await tx.sessionevent.create(
                    data={
                        "id": item.id,
                        "sessionId": item.sessionId,
                        "eventType": item.eventType,
                        "timestamp": item.timestamp,
                        "payload": item.payload,
                    }
                )

                ack_list.append(SyncAcknowledgement(
                    client_event_id=item.id,
                    server_sequence_id=hash(created.id) & 0x7FFFFFFF,  # synthetic sequence ID
                    status="COMPLETED"
                ))

        return ack_list
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Event sync failed: {str(e)}")

