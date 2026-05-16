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
