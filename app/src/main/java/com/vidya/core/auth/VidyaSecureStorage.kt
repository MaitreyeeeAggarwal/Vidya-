package com.vidya.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android Client Implementation Strategy for Hardware-Locked Auth.
 * Uses Jetpack Security to store JWT tokens directly inside the phone's hardware cryptographic keystore.
 */
class VidyaSecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vidya_secure_session_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String, deviceId: String) {
        sharedPreferences.edit().apply {
            putString("jwt_access_token", accessToken)
            putString("jwt_refresh_token", refreshToken)
            putString("local_bound_device_id", deviceId)
            apply()
        }
    }

    fun getLocalDeviceId(): String? = sharedPreferences.getString("local_bound_device_id", null)
    fun getAccessToken(): String? = sharedPreferences.getString("jwt_access_token", null)
}
