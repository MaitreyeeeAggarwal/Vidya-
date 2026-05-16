from fastapi import FastAPI
from .database import db
from .routes import router as auth_router
from .sync import router as sync_router
from .services.upload_router import router as media_router
from .services.resumable_upload import router as resumable_router

app = FastAPI(title="Vidya Cloud Backend")

@app.on_event("startup")
async def startup():
    await db.connect()

@app.on_event("shutdown")
async def shutdown():
    await db.disconnect()

app.include_router(auth_router)
app.include_router(sync_router)
app.include_router(media_router)
app.include_router(resumable_router)
