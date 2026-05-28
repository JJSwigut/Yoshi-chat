package com.example.yoshichat.domain

sealed interface MessagePart {
    data class Text(val value: String) : MessagePart

    data class Reasoning(val value: String) : MessagePart

    data class ToolInterruption(
        val toolName: String,
        val toolCallId: String?,
        val displayText: String,
    ) : MessagePart

    data class Attachment(
        val filename: String,
        val contentType: String,
        val sizeBytes: Long?,
    ) : MessagePart
}
