package com.vidya.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the resumable multipart upload API.
 *
 * Uses raw OkHttp instead of Retrofit because we need to stream
 * binary chunks as application/octet-stream request bodies,
 * which is awkward to express via Retrofit annotations.
 */
class VidyaMediaClient(
    private val baseUrl: String,
    private val authToken: String? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class InitResponse(val uploadId: String, val s3Key: String)
    data class PartResponse(val success: Boolean, val etag: String)

    fun initializeUpload(
        fileId: String,
        sessionEventId: String,
        fileName: String,
        contentType: String
    ): InitResponse {
        val json = JSONObject().apply {
            put("fileId", fileId)
            put("sessionEventId", sessionEventId)
            put("fileName", fileName)
            put("contentType", contentType)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/media/resumable/initialize")
            .post(body)
            .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
            .build()

        val resp = client.newCall(request).execute()
        val respJson = JSONObject(resp.body!!.string())
        return InitResponse(
            uploadId = respJson.getString("uploadId"),
            s3Key = respJson.getString("s3Key")
        )
    }

    fun uploadPart(uploadId: String, s3Key: String, partNumber: Int, chunk: ByteArray): PartResponse {
        val body = chunk.toRequestBody("application/octet-stream".toMediaType())
        val url = "$baseUrl/api/media/resumable/upload-part" +
                "?uploadId=$uploadId&s3Key=$s3Key&partNumber=$partNumber"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
            .build()

        val resp = client.newCall(request).execute()
        return if (resp.isSuccessful) {
            val respJson = JSONObject(resp.body!!.string())
            PartResponse(true, respJson.getString("ETag"))
        } else {
            PartResponse(false, "")
        }
    }

    fun completeUpload(uploadId: String, s3Key: String, parts: List<Pair<Int, String>>) {
        val partsArray = org.json.JSONArray()
        for ((num, etag) in parts) {
            partsArray.put(JSONObject().apply {
                put("PartNumber", num)
                put("ETag", etag)
            })
        }
        val json = JSONObject().apply {
            put("uploadId", uploadId)
            put("s3Key", s3Key)
            put("parts", partsArray)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/media/resumable/complete")
            .post(body)
            .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
            .build()

        client.newCall(request).execute()
    }
}
