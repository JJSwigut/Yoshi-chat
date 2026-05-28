package com.example.yoshichat.data.thread

import android.util.Log
import com.example.yoshichat.data.config.DevServerConfig
import com.example.yoshichat.data.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface ThreadMetadataApi {
    suspend fun listThreads(session: AuthSession): List<ThreadMetadataDto>

    suspend fun updateThreadTitle(
        session: AuthSession,
        threadId: String,
        title: String,
    ): ThreadMetadataDto
}

class OkHttpThreadMetadataApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val apiBaseUrl: String = DevServerConfig.apiBaseUrl,
) : ThreadMetadataApi {
    override suspend fun listThreads(session: AuthSession): List<ThreadMetadataDto> =
        withContext(Dispatchers.IO) {
            val url =
                "${apiBaseUrl.trimEnd('/')}/threads"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("status", "active")
                    .addQueryParameter("limit", "50")
                    .build()
            Log.d(TAG, "Fetching thread metadata")
            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .sessionCookie(session)
                    .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Thread metadata response code=${response.code}")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    Log.d(TAG, "Thread metadata unavailable code=${response.code} body=${body.take(LOG_BODY_LIMIT)}")
                    return@withContext emptyList()
                }
                json.decodeFromString(ThreadListResponseDto.serializer(), response.body.string()).threads
            }
        }

    override suspend fun updateThreadTitle(
        session: AuthSession,
        threadId: String,
        title: String,
    ): ThreadMetadataDto =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Updating thread title thread=$threadId chars=${title.length}")
            val request =
                Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/threads/$threadId")
                    .patch(
                        json.encodeToString(
                            UpdateThreadRequestDto.serializer(),
                            UpdateThreadRequestDto(title = title),
                        ).toRequestBody(JSON_MEDIA_TYPE),
                    )
                    .sessionCookie(session)
                    .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Update thread title response code=${response.code} thread=$threadId")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    error("Thread title update failed: HTTP ${response.code} $body")
                }
                json.decodeFromString(ThreadMetadataDto.serializer(), response.body.string())
            }
        }

    companion object {
        private const val TAG = "YoshiThreadMetadata"
        private const val LOG_BODY_LIMIT = 800
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
data class ThreadMetadataDto(
    val id: String,
    val title: String? = null,
    val status: String,
    @SerialName("isOnboarding")
    val isOnboarding: Boolean,
    @SerialName("createdAt")
    val createdAt: String,
    @SerialName("updatedAt")
    val updatedAt: String,
    @SerialName("lastMessageAt")
    val lastMessageAt: String? = null,
)

@Serializable
private data class ThreadListResponseDto(
    val threads: List<ThreadMetadataDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
private data class UpdateThreadRequestDto(
    val title: String,
)

private fun Request.Builder.sessionCookie(session: AuthSession): Request.Builder {
    val cookieHeader = session.cookieHeader
    if (!cookieHeader.isNullOrBlank()) {
        header("Cookie", cookieHeader)
    }
    return this
}
