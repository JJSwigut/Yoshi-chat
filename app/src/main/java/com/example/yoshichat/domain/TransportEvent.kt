package com.example.yoshichat.domain

sealed interface TransportEvent {
    data class UpdatedState(
        val messages: List<ChatMessage>,
        val debugInfo: TransportDebugInfo,
    ) : TransportEvent

    data object Completed : TransportEvent

    data class Failed(val message: String) : TransportEvent
}

data class TransportDebugInfo(
    val threadId: String?,
    val snapshotId: String?,
    val lastSseRawPayload: String?,
    val lastOperationPath: String?,
    val lastSseEventType: String?,
    val streamStatus: String,
    val currentMessageCount: Int,
)
