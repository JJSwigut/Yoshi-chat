package com.example.yoshichat.data.chat

import android.util.Log
import com.example.yoshichat.data.model.MessageNode
import com.example.yoshichat.data.model.Operation
import com.example.yoshichat.data.model.OperationPathSegment
import com.example.yoshichat.domain.ChatMessage
import com.example.yoshichat.domain.ChatRole
import com.example.yoshichat.domain.MessagePart
import com.example.yoshichat.domain.MessageStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * In-memory projection of the backend transport state.
 *
 * The backend owns the thread checkpoint. During a stream it sends a narrow
 * set of state operations that mutate message nodes, snapshot metadata, and
 * artifacts. This store applies only the observed operation subset rather
 * than pretending to be a full JSON Patch engine, then maps the canonical
 * message nodes into the app's UI domain model.
 */
class TransportStateStore(
    private val json: Json,
) {
    var messages: MutableList<MessageNode> = mutableListOf()
        private set
    var artifacts: JsonObject? = null
        private set
    var snapshotId: String? = null
        private set

    /**
     * Applies backend operations in order.
     *
     * Unsupported operation types or paths are logged and ignored. That keeps
     * the app resilient to extra backend metadata while making new required
     * operation shapes visible in Logcat during development.
     */
    fun applyOperations(operations: List<Operation>) {
        Log.d(TAG, "Applying operations count=${operations.size}")
        operations.forEach(::applyOperation)
    }

    /**
     * Replaces the streaming projection from a hydrated checkpoint.
     *
     * This is used before a thread is displayed and again after `[DONE]` so
     * the UI ends on persisted backend state, not optimistic or partial stream
     * state.
     */
    fun replaceFromThreadState(threadId: String, messages: List<MessageNode>, artifacts: JsonObject?) {
        this.messages = messages.toMutableList()
        this.artifacts = artifacts
        Log.d(TAG, "Replaced state from hydrate thread=$threadId messages=${messages.size} artifacts=${artifacts != null}")
    }

    fun reset() {
        messages = mutableListOf()
        artifacts = null
        snapshotId = null
        Log.d(TAG, "Reset transport state store")
    }

    fun toDomainMessages(): List<ChatMessage> =
        messages.mapIndexedNotNull { index, node -> node.toDomainMessage(index) }

    /**
     * Builds the state object sent with the next transport request.
     *
     * The agents service expects the client to send its current transport
     * snapshot alongside the user command. The snapshot comes from this store
     * because it is the closest mobile equivalent of the web runtime state.
     */
    fun toTransportState(threadId: String): JsonObject =
        JsonObject(
            buildMap {
                put("thread_id", JsonPrimitive(threadId))
                put("messages", JsonArray(messages.map { json.encodeToJsonElement(MessageNode.serializer(), it) }))
                snapshotId?.let { put("snapshotId", JsonPrimitive(it)) }
                artifacts?.let { put("artifacts", it) }
            },
        )

    private fun applyOperation(operation: Operation) {
        Log.d(TAG, "Operation type=${operation.type} path=${operation.path.debugPath()}")
        if (operation.type != "set") {
            Log.d(TAG, "Unsupported operation type=${operation.type} path=${operation.path.debugPath()}")
            return
        }

        when {
            operation.path.matches("messages") -> replaceMessages(operation.value)
            operation.path.matches("messages", index = true) -> replaceMessageAt(operation.path[1].asIndex(), operation.value)
            operation.path.matches("messages", "content", index = true) ->
                replaceMessageContent(operation.path[1].asIndex(), operation.value)
            operation.path.matches("snapshotId") -> snapshotId = operation.value.asStringOrNull()
            operation.path.matches("artifacts") -> artifacts = operation.value as? JsonObject
            else -> Log.d(TAG, "Unsupported set path=${operation.path.debugPath()}")
        }
    }

    private fun replaceMessages(value: JsonElement) {
        messages =
            json.decodeFromJsonElement(
                ListSerializer(MessageNode.serializer()),
                value,
            ).toMutableList()
        Log.d(TAG, "Replaced messages array count=${messages.size}")
    }

    private fun replaceMessageAt(index: Int?, value: JsonElement) {
        if (index == null || index < 0) {
            Log.d(TAG, "Skipping message replacement with invalid index=$index")
            return
        }
        val message = json.decodeFromJsonElement(MessageNode.serializer(), value)
        // Some streams create an assistant message at a future index before
        // all preceding nodes have arrived. Pad with inert nodes so the index
        // replacement can still land in the same position as the backend state.
        while (messages.size <= index) {
            messages.add(MessageNode(type = "ai", content = JsonPrimitive("")))
        }
        messages[index] = message
        Log.d(TAG, "Replaced message index=$index backendType=${message.type} total=${messages.size}")
    }

    private fun replaceMessageContent(index: Int?, value: JsonElement) {
        if (index == null || index !in messages.indices) {
            Log.d(TAG, "Skipping content replacement index=$index messageCount=${messages.size}")
            return
        }
        messages[index] = messages[index].copy(content = value)
        Log.d(TAG, "Replaced message content index=$index")
    }

    companion object {
        private const val TAG = "TransportStateStore"
    }
}

private fun MessageNode.toDomainMessage(index: Int): ChatMessage? {
    val role =
        when (type) {
            "human" -> ChatRole.User
            "ai" -> ChatRole.Assistant
            "reasoning" -> ChatRole.Assistant
            "system" -> ChatRole.System
            "tool" -> ChatRole.Tool
            else -> return null
        }
    val parts = content.toMessageParts()
    if (parts.isEmpty()) {
        return null
    }
    return ChatMessage(
        id = id ?: "$type-$index",
        role = role,
        parts = parts,
        status = MessageStatus.Complete,
    )
}

private fun JsonElement.toMessageParts(): List<MessagePart> =
    when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }?.let { listOf(MessagePart.Text(it)) }.orEmpty()
        is JsonArray -> mapNotNull { it.toMessagePart() }
        else -> emptyList()
    }

private fun JsonElement.toMessagePart(): MessagePart? {
    val obj = this as? JsonObject ?: return null
    return when (obj["type"]?.asStringOrNull()) {
        "text", "input_text" -> obj["text"]?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let(MessagePart::Text)
        "file" ->
            MessagePart.Attachment(
                filename = obj["filename"]?.asStringOrNull() ?: "Attachment",
                contentType = obj["content_type"]?.asStringOrNull() ?: "application/octet-stream",
                sizeBytes = obj["size_bytes"]?.asStringOrNull()?.toLongOrNull(),
            )
        "reasoning" -> {
            val text =
                obj["text"]?.asStringOrNull()
                    ?: obj["summary"]?.asReasoningTextOrNull()
                    ?: obj["content"]?.asReasoningTextOrNull()
            text?.takeIf { it.isNotBlank() }?.let(MessagePart::Reasoning)
        }
        else -> null
    }
}

private fun List<OperationPathSegment>.matches(
    first: String,
    vararg rest: String,
    index: Boolean = false,
): Boolean {
    val expectedSize = 1 + rest.size + if (index) 1 else 0
    if (size != expectedSize || firstOrNull()?.asKey() != first) return false
    var cursor = 1
    if (index) {
        if (getOrNull(cursor)?.asIndex() == null) return false
        cursor += 1
    }
    return rest.all { expected -> getOrNull(cursor++)?.asKey() == expected }
}

private fun OperationPathSegment.asKey(): String? = (this as? OperationPathSegment.Key)?.value

private fun OperationPathSegment.asIndex(): Int? =
    when (this) {
        is OperationPathSegment.Index -> value
        is OperationPathSegment.Key -> value.toIntOrNull()
    }

private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

/**
 * Reasoning payloads have appeared as strings, objects, and arrays depending
 * on the backend/runtime shape. Flatten them defensively so reasoning remains
 * visible without crashing on non-primitive JSON.
 */
private fun JsonElement.asReasoningTextOrNull(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray ->
            mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject ->
                        item["text"]?.asStringOrNull()
                            ?: item["summary"]?.asReasoningTextOrNull()
                            ?: item["content"]?.asReasoningTextOrNull()
                    else -> null
                }
            }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        is JsonObject ->
            this["text"]?.asStringOrNull()
                ?: this["summary"]?.asReasoningTextOrNull()
                ?: this["content"]?.asReasoningTextOrNull()
    }

private fun List<OperationPathSegment>.debugPath(): String =
    joinToString(prefix = "[", postfix = "]") { segment ->
        when (segment) {
            is OperationPathSegment.Index -> segment.value.toString()
            is OperationPathSegment.Key -> "\"${segment.value}\""
        }
    }
