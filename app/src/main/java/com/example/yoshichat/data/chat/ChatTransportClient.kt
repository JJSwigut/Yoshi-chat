package com.example.yoshichat.data.chat

import android.util.Log
import com.example.yoshichat.data.config.DevServerConfig
import com.example.yoshichat.data.model.AuthSession
import com.example.yoshichat.data.model.OperationPathSegment
import com.example.yoshichat.data.model.TransportEnvelope
import com.example.yoshichat.domain.TransportDebugInfo
import com.example.yoshichat.domain.TransportEvent
import com.example.yoshichat.domain.UploadedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Owns the Assistant Transport HTTP call.
 *
 * The agents service returns server-sent events where each `data:` payload is a
 * transport envelope. The important envelope today is `update-state`: it
 * contains mutation operations for the canonical backend thread state, not a
 * token-delta stream. This client keeps transport concerns here and emits
 * domain events after each state mutation is applied.
 */
class ChatTransportClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val agentsBaseUrl: String = DevServerConfig.agentsBaseUrl,
) {
    /**
     * Sends one user turn and streams backend state changes.
     *
     * [stateStore] is intentionally passed in by the caller so the request can
     * include the latest canonical state and so each incoming operation can
     * mutate the same store before UI messages are derived from it.
     */
    fun streamMessage(
        threadId: String,
        text: String,
        attachments: List<UploadedAttachment>,
        session: AuthSession,
        stateStore: TransportStateStore,
    ): Flow<TransportEvent> =
        flow {
            val requestId = UUID.randomUUID().toString()
            val requestBody =
                json.encodeToString(
                    TransportRequestBody.serializer(),
                    TransportRequestBody(
                        threadId = threadId,
                        commands =
                            listOf(
                                TransportCommand(
                                    type = "add-message",
                                    message =
                                        TransportUserMessage(
                                            role = "user",
                                            parts = listOf(TransportTextPart(type = "text", text = text)),
                                        ),
                                ),
                            ),
                        attachments = attachments.map { it.toTransportAttachment() },
                        state = stateStore.toTransportState(threadId),
                    ),
                )
            Log.d(
                TAG,
                "Opening transport requestId=$requestId thread=$threadId chars=${text.length} attachments=${attachments.size} stateMessages=${stateStore.messages.size}",
            )
            Log.d(TAG, "Transport request requestId=$requestId bodyBytes=${requestBody.length}")
            val request =
                Request.Builder()
                    .url("${agentsBaseUrl.trimEnd('/')}/api/v1/transport/chat")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .headers(session.authHeaders())
                    .addHeader("x-request-id", requestId)
                    .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Transport response requestId=$requestId code=${response.code}")
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    Log.e(TAG, "Transport failed requestId=$requestId code=${response.code} body=${body.take(LOG_BODY_LIMIT)}")
                    emit(TransportEvent.Failed("Transport failed: HTTP ${response.code}. See Logcat tag $TAG requestId=$requestId."))
                    return@use
                }

                val source = response.body.source()
                val parser = SseFrameParser()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    parser.acceptLine(line).forEach { payload ->
                        processFrame(
                            payload = payload,
                            requestId = requestId,
                            threadId = threadId,
                            stateStore = stateStore,
                        )?.let { emit(it) }
                    }
                }
                parser.finish().forEach { payload ->
                    processFrame(
                        payload = payload,
                        requestId = requestId,
                        threadId = threadId,
                        stateStore = stateStore,
                    )?.let { emit(it) }
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Converts one SSE payload into a domain event.
     *
     * Non-`update-state` envelopes are ignored because the current backend
     * contract uses `update-state` as the source of renderable chat state.
     * `[DONE]` is treated separately so the ViewModel can rehydrate the final
     * checkpoint from the backend.
     */
    private fun processFrame(
        payload: String,
        requestId: String,
        threadId: String,
        stateStore: TransportStateStore,
    ): TransportEvent? {
        if (payload.isBlank()) return null
        if (payload == "[DONE]") {
            Log.d(TAG, "SSE complete requestId=$requestId")
            return TransportEvent.Completed
        }

        val envelope =
            runCatching { json.decodeFromString(TransportEnvelope.serializer(), payload) }
                .getOrElse {
                    Log.e(TAG, "Invalid SSE payload requestId=$requestId", it)
                    return TransportEvent.Failed("Invalid transport payload. See Logcat tag $TAG requestId=$requestId.")
                }

        if (envelope.type != "update-state") {
            Log.d(TAG, "Ignoring SSE envelope requestId=$requestId type=${envelope.type}")
            return null
        }

        Log.d(
            TAG,
            "SSE update requestId=$requestId operations=${envelope.operations.size} lastPath=${envelope.operations.lastOrNull()?.path?.debugPath()}",
        )
        stateStore.applyOperations(envelope.operations)
        Log.d(
            TAG,
            "Applied update-state requestId=$requestId operations=${envelope.operations.size} messages=${stateStore.messages.size} snapshot=${stateStore.snapshotId}",
        )
        return TransportEvent.UpdatedState(
            messages = stateStore.toDomainMessages(),
            debugInfo =
                TransportDebugInfo(
                    threadId = threadId,
                    snapshotId = stateStore.snapshotId,
                    lastSseRawPayload = payload,
                    lastOperationPath = envelope.operations.lastOrNull()?.path?.debugPath(),
                    lastSseEventType = envelope.type,
                    streamStatus = "streaming",
                    currentMessageCount = stateStore.messages.size,
                ),
        )
    }

    companion object {
        private const val TAG = "YoshiTransport"
        private const val LOG_BODY_LIMIT = 800
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * Minimal SSE frame parser for the backend transport stream.
 *
 * It follows the parts of the SSE format this app needs: accumulate `data:`
 * lines until a blank frame boundary, ignore other fields such as `event:` or
 * comments, and return the joined data payload. The transport endpoint uses
 * `data: [DONE]` as its completion marker.
 */
class SseFrameParser {
    private val dataLines = mutableListOf<String>()

    fun acceptLine(line: String): List<String> {
        if (line.isBlank()) {
            return flush()
        }
        if (line.startsWith("data:")) {
            dataLines += line.removePrefix("data:").trimStart()
        }
        return emptyList()
    }

    fun finish(): List<String> = flush()

    private fun flush(): List<String> {
        if (dataLines.isEmpty()) return emptyList()
        val payload = dataLines.joinToString("\n").trim()
        dataLines.clear()
        return listOf(payload)
    }
}

@Serializable
private data class TransportRequestBody(
    @kotlinx.serialization.SerialName("thread_id")
    val threadId: String,
    val commands: List<TransportCommand>,
    val attachments: List<TransportAttachment> = emptyList(),
    val state: JsonObject,
)

@Serializable
private data class TransportCommand(
    val type: String,
    val message: TransportUserMessage,
)

@Serializable
private data class TransportUserMessage(
    val role: String,
    val parts: List<TransportTextPart>,
)

@Serializable
private data class TransportTextPart(
    val type: String,
    val text: String,
)

@Serializable
private data class TransportAttachment(
    @kotlinx.serialization.SerialName("file_id")
    val fileId: String,
    val filename: String,
    @kotlinx.serialization.SerialName("content_type")
    val contentType: String,
    @kotlinx.serialization.SerialName("size_bytes")
    val sizeBytes: Long? = null,
)

private fun UploadedAttachment.toTransportAttachment(): TransportAttachment =
    TransportAttachment(
        fileId = fileId,
        filename = filename,
        contentType = contentType,
        sizeBytes = sizeBytes,
    )

private fun List<OperationPathSegment>.debugPath(): String =
    joinToString(prefix = "[", postfix = "]") { segment ->
        when (segment) {
            is OperationPathSegment.Index -> segment.value.toString()
            is OperationPathSegment.Key -> "\"${segment.value}\""
        }
    }
