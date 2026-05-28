package com.example.yoshichat.domain

sealed interface ChatUiState {
    data object Connecting : ChatUiState

    data object Hydrating : ChatUiState

    data class Ready(
        val threadId: String?,
        val messages: List<ChatMessage>,
        val isStreaming: Boolean,
        val debugInfo: TransportDebugInfo? = null,
        val recentThreadIds: List<String> = emptyList(),
        val recentThreads: List<ThreadSummary> = emptyList(),
        val currentThreadTitle: String = "New conversation",
        val suggestedPrompts: List<String> = emptyList(),
        val pendingToolInterruption: PendingToolInterruption? = null,
        val lastFailedMessage: String? = null,
        val composerAttachments: List<ComposerAttachment> = emptyList(),
    ) : ChatUiState

    data class Error(val message: String) : ChatUiState
}

data class PendingToolInterruption(
    val toolName: String,
    val toolCallId: String?,
    val displayText: String,
)

data class ThreadSummary(
    val id: String,
    val title: String,
    val subtitle: String,
)
