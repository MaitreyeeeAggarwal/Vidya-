from contextlib import asynccontextmanager
from fastapi import FastAPI
from .database import db
from .routes import router as auth_router
from .sync import router as sync_router
from .services.upload_router import router as media_router
from .services.resumable_upload import router as resumable_router
from .services.curriculum_api import router as curriculum_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage Prisma DB connection for the lifetime of the application."""
    await db.connect()
    yield
    await db.disconnect()


app = FastAPI(title="Vidya Cloud Backend", lifespan=lifespan)

app.include_router(auth_router)
app.include_router(sync_router)
app.include_router(media_router)
app.include_router(resumable_router)
app.include_router(curriculum_router)
