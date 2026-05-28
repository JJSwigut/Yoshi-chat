package com.example.yoshichat.domain

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val parts: List<MessagePart>,
    val status: MessageStatus,
)

