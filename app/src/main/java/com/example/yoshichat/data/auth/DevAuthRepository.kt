package com.example.yoshichat.data.auth

import android.content.Context
import android.util.Log
import com.example.yoshichat.data.config.DevServerConfig
import com.example.yoshichat.data.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Development-only auth bridge for the local Yoshi stack.
 *
 * The API worker's `/support/test-login` route creates a local Better Auth
 * session. The agents service still expects a bearer JWT, so this repository
 * turns the dev cookie into a short-lived JWT via `/api/auth/token` and returns
 * both pieces. The cookie is persisted to keep relaunches attached to the same
 * local user and therefore the same backend-owned threads; the JWT is fetched
 * fresh and is not stored.
 */
class DevAuthRepository(
    context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val apiBaseUrl: String = DevServerConfig.apiBaseUrl,
) : AuthRepository {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun getSession(): AuthSession =
        withContext(Dispatchers.IO) {
            restoreSessionFromCookie()?.let { return@withContext it }

            Log.d(TAG, "Starting dev login apiBaseUrl=$apiBaseUrl")
            val loginBody =
                json.encodeToString(
                    DevLoginRequest.serializer(),
                    DevLoginRequest(emailPrefix = "android", onboarded = true),
                )
            val loginRequest =
                Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/support/test-login")
                    .post(loginBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            client.newCall(loginRequest).execute().use { response ->
                Log.d(TAG, "Dev login response code=${response.code}")
                if (!response.isSuccessful) {
                    error("Dev login failed: HTTP ${response.code}")
                }
                val payload = response.body.string()
                val login = json.decodeFromString(DevLoginResponse.serializer(), payload)
                val cookieHeader = response.headers.values("Set-Cookie").toCookieHeader()
                val token = fetchJwt(cookieHeader)
                Log.d(TAG, "Dev login complete user=${login.id}")
                AuthSession(accessToken = token, userId = login.id, email = login.email, cookieHeader = cookieHeader)
                    .also { saveSessionCookie(it) }
            }
        }

    private fun restoreSessionFromCookie(): AuthSession? {
        val cookieHeader = preferences.getString(KEY_COOKIE_HEADER, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = preferences.getString(KEY_USER_ID, null)
        val email = preferences.getString(KEY_EMAIL, null)

        return runCatching {
            val token = fetchJwt(cookieHeader)
            Log.d(TAG, "Reused saved dev session user=$userId")
            AuthSession(accessToken = token, userId = userId, email = email, cookieHeader = cookieHeader)
        }.onFailure { throwable ->
            Log.d(TAG, "Saved dev session is unavailable; creating a new dev login message=${throwable.message}")
            clearSavedSession()
        }.getOrNull()
    }

    private fun fetchJwt(cookieHeader: String): String {
        Log.d(TAG, "Fetching JWT cookieCount=${cookieHeader.split(';').size}")
        val request =
            Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/api/auth/token")
                .get()
                .header("Cookie", cookieHeader)
                .build()
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "JWT response code=${response.code}")
            if (!response.isSuccessful) {
                error("JWT fetch failed: HTTP ${response.code}")
            }
            val payload = response.body.string()
            val tokenPayload = json.decodeFromString<JsonObject>(payload)
            return tokenPayload["token"]?.jsonPrimitive?.content ?: error("JWT response did not include token")
        }
    }

    private fun saveSessionCookie(session: AuthSession) {
        preferences.edit()
            .putString(KEY_COOKIE_HEADER, session.cookieHeader)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    private fun clearSavedSession() {
        preferences.edit().clear().apply()
    }

    private fun List<String>.toCookieHeader(): String =
        joinToString("; ") { setCookie ->
            setCookie.substringBefore(";")
        }

    companion object {
        private const val TAG = "YoshiAuth"
        private const val PREFERENCES_NAME = "yoshi_dev_auth"
        private const val KEY_COOKIE_HEADER = "cookie_header"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
private data class DevLoginRequest(
    @SerialName("email_prefix")
    val emailPrefix: String,
    val onboarded: Boolean,
)

@Serializable
private data class DevLoginResponse(
    val email: String,
    val id: String,
    val onboarded: Boolean,
)
