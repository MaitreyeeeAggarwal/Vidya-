import pytest
from fastapi.testclient import TestClient
from app.routes import router
from fastapi import FastAPI
import app.services.sms as sms
from unittest.mock import AsyncMock

@pytest.fixture
def test_app():
    app = FastAPI()
    app.include_router(router)
    return app

@pytest.fixture
def client(test_app):
    return TestClient(test_app)

@pytest.fixture
def mock_sms(monkeypatch):
    mock = AsyncMock(return_value=True)
    monkeypatch.setattr("app.routes.send_otp_sms", mock)
    return mock

@pytest.fixture
def mock_db(monkeypatch):
    class MockPrismaDb:
        class MockOtpVerification:
            async def upsert(self, *args, **kwargs):
                return True
            async def find_unique(self, where):
                if where.get("phoneNumber") == "+919999999999":
                    class MockRecord:
                        otpHash = "123456"
                        expiresAt = __import__("datetime").datetime.utcnow() + __import__("datetime").timedelta(minutes=5)
                    return MockRecord()
                return None
            async def delete(self, *args, **kwargs):
                return True

        class MockTeacher:
            async def find_unique(self, where):
                if where.get("phoneNumber") == "+919999999999":
                    class MockTeacherRecord:
                        id = "teacher_123"
                        isActive = True
                    return MockTeacherRecord()
                return None
            async def create(self, data):
                class MockTeacherRecord:
                    id = "teacher_123"
                    isActive = True
                return MockTeacherRecord()

        class MockDevice:
            async def find_unique(self, where, include=None):
                if where.get("deviceId") == "valid_device_1":
                    class MockDeviceRecord:
                        id = "dev_123"
                        isSigned = True
                        teacherId = "teacher_123"
                        class MockTeacher:
                            id = "teacher_123"
                            isActive = True
                        teacher = MockTeacher()
                    return MockDeviceRecord()
                return None
            async def create(self, data):
                return True

        otpverification = MockOtpVerification()
        teacher = MockTeacher()
        device = MockDevice()

    mock_db_instance = MockPrismaDb()
    monkeypatch.setattr("app.routes.db", mock_db_instance)
    monkeypatch.setattr("app.auth.db", mock_db_instance)
    return mock_db_instance


# ── Sync / Delta Engine mock ─────────────────────────────────────────────────

@pytest.fixture(autouse=True)
def mock_sync_db(monkeypatch):
    """
    Replaces the Prisma `db` used by `app.sync` with a lightweight
    in-memory store so tests never require a real database connection.

    The mock accurately simulates:
    - Idempotent find_unique: returns the stored record if the id exists.
    - Idempotent create: raises an integrity-style error if the id already
      exists (just like Postgres ON CONFLICT), but the sync handler catches
      that via find_unique first, so in practice we just store and return.
    - Transactional context manager (async with db.tx() as tx).
    """
    _store: dict = {}  # id -> mock record

    class MockSessionEvent:
        async def find_unique(self, where):
            record_id = where.get("id")
            return _store.get(record_id)

        async def create(self, data):
            class _Record:
                def __init__(self, d):
                    self.__dict__.update(d)
            record = _Record(data)
            _store[data["id"]] = record
            return record

        async def create_many(self, data, skip_duplicates=False):
            for item in data:
                if item["id"] not in _store or not skip_duplicates:
                    class _Record:
                        def __init__(self, d):
                            self.__dict__.update(d)
                    _store[item["id"]] = _Record(item)
            return len(data)

    class MockSession:
        async def upsert(self, where, data):
            return True

    class MockTxContext:
        def __init__(self):
            self.sessionevent = MockSessionEvent()
            self.session = MockSession()

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            pass

    class MockDb:
        def __init__(self):
            self.sessionevent = MockSessionEvent()
            self.session = MockSession()

        def tx(self):
            return MockTxContext()

        async def connect(self):
            pass

        async def disconnect(self):
            pass

    mock_db_instance = MockDb()
    monkeypatch.setattr("app.sync.db", mock_db_instance)
