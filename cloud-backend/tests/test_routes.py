import pytest
from app.auth import SECRET_KEY, ALGORITHM

def test_request_otp_success(client, mock_db, mock_sms):
    response = client.post(
        "/api/auth/request-otp",
        json={"phone_number": "+919999999999"}
    )
    assert response.status_code == 200
    assert response.json() == {"message": "OTP dispatched successfully through carrier network channels."}
    mock_sms.assert_called_once()

def test_request_otp_invalid_phone(client, mock_db, mock_sms):
    response = client.post(
        "/api/auth/request-otp",
        json={"phone_number": "1234567890"}
    )
    assert response.status_code == 400
    mock_sms.assert_not_called()

def test_verify_otp_success(client, mock_db):
    response = client.post(
        "/api/auth/verify-otp",
        json={
            "phone_number": "+919999999999",
            "otp": "123456",
            "device_id": "new_device_1"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data

def test_verify_otp_incorrect(client, mock_db):
    response = client.post(
        "/api/auth/verify-otp",
        json={
            "phone_number": "+919999999999",
            "otp": "000000",
            "device_id": "new_device_1"
        }
    )
    assert response.status_code == 400

def test_silent_login_success(client, mock_db):
    response = client.post(
        "/api/auth/silent-login",
        json={"device_id": "valid_device_1"}
    )
    assert response.status_code == 200
    assert "access_token" in response.json()

def test_silent_login_unauthorized(client, mock_db):
    response = client.post(
        "/api/auth/silent-login",
        json={"device_id": "unknown_device"}
    )
    assert response.status_code == 401

def test_secure_sync_check(client, mock_db):
    from app.auth import create_token_pair
    tokens = create_token_pair("teacher_123", "valid_device_1")
    
    response = client.get(
        "/api/auth/secure-sync-check",
        headers={"Authorization": f"Bearer {tokens['access_token']}"}
    )
    assert response.status_code == 200
    assert response.json() == {
        "status": "authorized",
        "teacher_id": "teacher_123",
        "device_locked": "valid_device_1"
    }

def test_secure_sync_check_invalid_device(client, mock_db):
    from app.auth import create_token_pair
    # device that is not found in db
    tokens = create_token_pair("teacher_123", "invalid_device_999")
    
    response = client.get(
        "/api/auth/secure-sync-check",
        headers={"Authorization": f"Bearer {tokens['access_token']}"}
    )
    assert response.status_code == 401
