package com.example.yoshichat.data.chat

import android.util.Log
import com.example.yoshichat.data.config.DevServerConfig
import com.example.yoshichat.data.model.AuthSession
import com.example.yoshichat.data.model.SuggestionsDto
import com.example.yoshichat.data.model.ThreadInitDto
import com.example.yoshichat.data.model.ThreadStateDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

interface ChatApi {
    suspend fun initThread(
        session: AuthSession,
        threadId: String? = null,
        createNew: Boolean = true,
        isSessionReturn: Boolean = false,
    ): ThreadInitDto

    suspend fun getThreadState(threadId: String, session: AuthSession): ThreadStateDto

    suspend fun getSuggestions(threadId: String, session: AuthSession): SuggestionsDto
}

class OkHttpChatApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val agentsBaseUrl: String = DevServerConfig.agentsBaseUrl,
) : ChatApi {
    override suspend fun initThread(
        session: AuthSession,
        threadId: String?,
        createNew: Boolean,
        isSessionReturn: Boolean,
    ): ThreadInitDto =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Initializing thread agentsBaseUrl=$agentsBaseUrl existingThread=${threadId != null} createNew=$createNew")
            val request =
                Request.Builder()
                    .url("${agentsBaseUrl.trimEnd('/')}/api/v1/chat/thread/init")
                    .post(
                        json.encodeToString(
                            ThreadInitRequest.serializer(),
                            ThreadInitRequest(
                                threadId = threadId,
                                createNew = createNew,
                                isSessionReturn = isSessionReturn,
                            ),
                        ).toRequestBody(JSON_MEDIA_TYPE),
                    )
                    .headers(session.authHeaders())
                    .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Thread init response code=${response.code}")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    Log.e(TAG, "Thread init failed code=${response.code} body=${body.take(LOG_BODY_LIMIT)}")
                    error("Thread init failed: HTTP ${response.code} $body")
                }
                val body = response.body.string()
                val dto = json.decodeFromString(ThreadInitDto.serializer(), body)
                Log.d(TAG, "Thread init complete thread=${dto.threadId}")
                dto
            }
        }

    override suspend fun getThreadState(threadId: String, session: AuthSession): ThreadStateDto =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Fetching thread state thread=$threadId")
            val request =
                Request.Builder()
                    .url("${agentsBaseUrl.trimEnd('/')}/api/v1/chat/thread/$threadId/state")
                    .get()
                    .headers(session.authHeaders())
                    .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Thread state response code=${response.code} thread=$threadId")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    Log.e(TAG, "Thread state failed code=${response.code} thread=$threadId body=${body.take(LOG_BODY_LIMIT)}")
                    error("Thread state fetch failed: HTTP ${response.code} $body")
                }
                val body = response.body.string()
                val dto = json.decodeFromString(ThreadStateDto.serializer(), body)
                Log.d(TAG, "Thread state complete thread=${dto.threadId} messages=${dto.messages.size}")
                dto
            }
        }

    override suspend fun getSuggestions(threadId: String, session: AuthSession): SuggestionsDto =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Fetching suggestions thread=$threadId")
            val url =
                "${agentsBaseUrl.trimEnd('/')}/api/v1/chat/thread/$threadId/suggestions"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("context_thread_id", threadId)
                    .build()
            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .headers(session.authHeaders())
                    .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Suggestions response code=${response.code} thread=$threadId")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    Log.e(TAG, "Suggestions failed code=${response.code} thread=$threadId body=${body.take(LOG_BODY_LIMIT)}")
                    error("Suggestions fetch failed: HTTP ${response.code} $body")
                }
                val body = response.body.string()
                val dto = json.decodeFromString(SuggestionsDto.serializer(), body)
                Log.d(TAG, "Suggestions complete thread=${dto.threadId} count=${dto.suggestions.size}")
                dto
            }
        }

    companion object {
        private const val TAG = "YoshiChatApi"
        private const val LOG_BODY_LIMIT = 2_000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

fun AuthSession.authHeaders(): okhttp3.Headers =
    okhttp3.Headers.Builder()
        .add("Authorization", "Bearer $accessToken")
        .add("x-user-timezone", "America/New_York")
        .build()

@Serializable
private data class ThreadInitRequest(
    @SerialName("thread_id")
    val threadId: String? = null,
    @SerialName("create_new")
    val createNew: Boolean = true,
    @SerialName("is_session_return")
    val isSessionReturn: Boolean = false,
)
