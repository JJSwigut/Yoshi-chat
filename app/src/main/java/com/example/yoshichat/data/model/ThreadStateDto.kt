package com.example.yoshichat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ThreadStateDto(
    @SerialName("thread_id")
    val threadId: String,
    val messages: List<MessageNode> = emptyList(),
    val artifacts: JsonObject? = null,
    @SerialName("pending_interrupt")
    val pendingInterrupt: JsonElement? = null,
    @SerialName("is_onboarding_complete")
    val isOnboardingComplete: Boolean? = null,
    @SerialName("onboarding_progress")
    val onboardingProgress: JsonObject? = null,
)

@Serializable
data class ThreadInitDto(
    @SerialName("thread_id")
    val threadId: String,
    @SerialName("created_new")
    val createdNew: Boolean,
)

@Serializable
data class SuggestionsDto(
    @SerialName("thread_id")
    val threadId: String,
    val suggestions: List<String> = emptyList(),
)
