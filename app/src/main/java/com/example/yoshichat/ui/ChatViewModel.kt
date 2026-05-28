package com.example.yoshichat.ui

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yoshichat.data.attachments.OkHttpAttachmentUploadApi
import com.example.yoshichat.data.auth.DevAuthRepository
import com.example.yoshichat.data.chat.ChatTransportClient
import com.example.yoshichat.data.chat.OkHttpChatApi
import com.example.yoshichat.data.chat.TransportStateStore
import com.example.yoshichat.data.model.AuthSession
import com.example.yoshichat.data.model.ThreadStateDto
import com.example.yoshichat.data.thread.OkHttpThreadMetadataApi
import com.example.yoshichat.data.thread.ThreadSessionRepository
import com.example.yoshichat.domain.AttachmentUploadStatus
import com.example.yoshichat.domain.ChatMessage
import com.example.yoshichat.domain.ChatRole
import com.example.yoshichat.domain.ChatUiState
import com.example.yoshichat.domain.ComposerAttachment
import com.example.yoshichat.domain.MessagePart
import com.example.yoshichat.domain.MessageStatus
import com.example.yoshichat.domain.PendingToolInterruption
import com.example.yoshichat.domain.ThreadSummary
import com.example.yoshichat.domain.TransportDebugInfo
import com.example.yoshichat.domain.TransportEvent
import com.example.yoshichat.domain.UploadedAttachment
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Coordinates the chat integration flow for the proof of concept.
 *
 * The backend remains the source of truth: this ViewModel authenticates,
 * initializes or resumes a thread, hydrates checkpoint state, streams one user
 * turn through Assistant Transport, and rehydrates again after completion.
 * Local state is limited to UX concerns such as optimistic messages, selected
 * thread IDs, composer attachments, debug metadata, and cached reasoning.
 */
class ChatViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    private val authRepository = DevAuthRepository(appContext, client, json)
    private val chatApi = OkHttpChatApi(client, json)
    private val transportClient = ChatTransportClient(client, json)
    private val stateStore = TransportStateStore(json)
    private val threadSessionRepository = ThreadSessionRepository(appContext)
    private val threadMetadataApi = OkHttpThreadMetadataApi(client, json)
    private val attachmentUploadApi = OkHttpAttachmentUploadApi(appContext, client, json)

    private var session: AuthSession? = null
    private var threadId: String? = null
    private var streamJob: Job? = null
    private var lastDebugInfo: TransportDebugInfo? = null
    private var recentThreadIds: List<String> = threadSessionRepository.recentThreadIds()
    private var threadSummaries: List<ThreadSummary> = emptyList()
    private var currentThreadTitle: String = "New conversation"
    private var suggestedPrompts: List<String> = emptyList()
    private var pendingToolInterruption: PendingToolInterruption? = null
    private var lastFailedMessage: String? = null
    private var composerAttachments: List<ComposerAttachment> = emptyList()
    private val reasoningByUserMessageId = linkedMapOf<String, List<MessagePart.Reasoning>>()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Connecting)

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "Initializing chat view model")
        refreshThreadState()
    }

    /**
     * Sends one user message.
     *
     * The user and empty assistant messages are shown optimistically, but the
     * stream's canonical state updates replace them as soon as backend
     * `update-state` operations arrive.
     */
    fun sendMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            Log.d(TAG, "Ignoring empty send")
            return
        }
        if (streamJob?.isActive == true) {
            Log.d(TAG, "Ignoring send while stream is active")
            return
        }

        val activeSession = session ?: return setError("Not authenticated yet")
        val activeThreadId = threadId ?: return setError("Thread is not initialized yet")
        val currentMessages = (_uiState.value as? ChatUiState.Ready)?.messages.orEmpty()
        if (composerAttachments.any { it.status == AttachmentUploadStatus.Uploading }) {
            Log.d(TAG, "Ignoring send while attachments are uploading")
            return
        }
        if (composerAttachments.any { it.status == AttachmentUploadStatus.Failed }) {
            Log.d(TAG, "Ignoring send with failed attachments")
            return
        }
        val uploadedAttachments = composerAttachments.toUploadedAttachments()
        Log.d(TAG, "Sending message thread=$activeThreadId chars=${cleanText.length} attachments=${uploadedAttachments.size}")
        lastFailedMessage = null
        suggestedPrompts = emptyList()
        composerAttachments = emptyList()

        _uiState.value = readyState(
            messages = currentMessages + optimisticUser(cleanText, uploadedAttachments) + optimisticAssistant(),
            isStreaming = true,
        )

        streamJob =
            viewModelScope.launch {
                runCatching {
                    transportClient
                        .streamMessage(
                            threadId = activeThreadId,
                            text = cleanText,
                            attachments = uploadedAttachments,
                            session = activeSession,
                            stateStore = stateStore,
                        )
                        .collect(::handleTransportEvent)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        Log.d(TAG, "Transport coroutine cancelled")
                        return@onFailure
                    }
                    Log.e(TAG, "Transport coroutine failed", throwable)
                    renderErrorBubble(throwable.message ?: "Transport failed", failedMessage = cleanText)
                }
            }
    }

    fun stopStreaming() {
        if (streamJob?.isActive != true) {
            Log.d(TAG, "Ignoring stop because no stream is active")
            return
        }
        Log.d(TAG, "Stopping active stream")
        streamJob?.cancel()
        streamJob = null
        lastDebugInfo = lastDebugInfo?.copy(streamStatus = "stopped")
        val current = _uiState.value as? ChatUiState.Ready ?: return
        _uiState.value = readyState(
            messages = current.messages.markStreamingComplete(),
            isStreaming = false,
        )
    }

    fun retryLastFailedSend() {
        val failedMessage = lastFailedMessage ?: return
        Log.d(TAG, "Retrying failed send chars=${failedMessage.length}")
        sendMessage(failedMessage)
    }

    fun dismissMessage(messageId: String) {
        val current = _uiState.value as? ChatUiState.Ready ?: return
        val updatedMessages = current.messages.filterNot { it.id == messageId }
        if (current.messages.any { it.id == messageId && it.status == MessageStatus.Failed }) {
            lastFailedMessage = null
        }
        _uiState.value = readyState(
            messages = updatedMessages,
            isStreaming = current.isStreaming,
        )
    }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val activeSession = session ?: return setError("Not authenticated yet")
        val activeThreadId = threadId
        val availableSlots = MAX_ATTACHMENTS_PER_MESSAGE - composerAttachments.size
        if (availableSlots <= 0) {
            Log.d(TAG, "Ignoring attachments because composer is full")
            return
        }

        val selected = uris.take(availableSlots).map { uri -> uri.toComposerAttachment() }
        Log.d(TAG, "Adding attachments count=${selected.size} skipped=${uris.size - selected.size}")
        if (uris.size > selected.size) {
            Log.d(TAG, "Skipped ${uris.size - selected.size} attachments because max=$MAX_ATTACHMENTS_PER_MESSAGE")
        }
        composerAttachments = composerAttachments + selected
        updateReadyComposerAttachments()

        selected.filter { it.status == AttachmentUploadStatus.Uploading }.forEach { attachment ->
            viewModelScope.launch {
                runCatching {
                    attachmentUploadApi.upload(
                        uri = Uri.parse(attachment.uri),
                        threadId = activeThreadId,
                        session = activeSession,
                    )
                }.onSuccess { uploaded ->
                    Log.d(TAG, "Attachment uploaded id=${attachment.id} filename=${uploaded.filename}")
                    composerAttachments =
                        composerAttachments.map {
                            if (it.id == attachment.id) {
                                it.copy(
                                    filename = uploaded.filename,
                                    contentType = uploaded.contentType,
                                    sizeBytes = uploaded.sizeBytes,
                                    status = AttachmentUploadStatus.Uploaded,
                                    fileId = uploaded.fileId,
                                    error = null,
                                )
                            } else {
                                it
                            }
                        }
                    updateReadyComposerAttachments()
                }.onFailure { throwable ->
                    Log.e(TAG, "Attachment upload failed id=${attachment.id} filename=${attachment.filename}", throwable)
                    composerAttachments =
                        composerAttachments.map {
                            if (it.id == attachment.id) {
                                it.copy(
                                    status = AttachmentUploadStatus.Failed,
                                    error = throwable.message?.take(240) ?: "Upload failed",
                                )
                            } else {
                                it
                            }
                        }
                    updateReadyComposerAttachments()
                }
            }
        }
    }

    fun removeAttachment(attachmentId: String) {
        Log.d(TAG, "Removing attachment id=$attachmentId")
        composerAttachments = composerAttachments.filterNot { it.id == attachmentId }
        updateReadyComposerAttachments()
    }

    fun startNewThread() {
        if (streamJob?.isActive == true) {
            Log.d(TAG, "Ignoring new thread request while stream is active")
            return
        }
        Log.d(TAG, "Starting new thread")
        threadId = null
        lastDebugInfo = null
        suggestedPrompts = emptyList()
        pendingToolInterruption = null
        lastFailedMessage = null
        composerAttachments = emptyList()
        reasoningByUserMessageId.clear()
        stateStore.reset()
        refreshThreadState(preferredThreadId = null, createNew = true, fallbackToNewOnUnavailable = false)
    }

    fun openThread(threadId: String) {
        val cleanThreadId = threadId.trim()
        if (cleanThreadId.isEmpty()) return
        if (streamJob?.isActive == true) {
            Log.d(TAG, "Ignoring open thread request while stream is active")
            return
        }
        Log.d(TAG, "Opening existing thread=$cleanThreadId")
        this.threadId = null
        lastDebugInfo = null
        suggestedPrompts = emptyList()
        pendingToolInterruption = null
        lastFailedMessage = null
        composerAttachments = emptyList()
        reasoningByUserMessageId.clear()
        stateStore.reset()
        refreshThreadState(preferredThreadId = cleanThreadId, createNew = false, fallbackToNewOnUnavailable = false)
    }

    fun renameCurrentThread(title: String) {
        val cleanTitle = title.trim().take(MAX_THREAD_TITLE_LENGTH)
        val activeSession = session ?: return
        val activeThreadId = threadId ?: return
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                threadMetadataApi.updateThreadTitle(activeSession, activeThreadId, cleanTitle)
                refreshThreadMetadata(activeSession)
                currentThreadTitle = cleanTitle
                val currentMessages = (_uiState.value as? ChatUiState.Ready)?.messages ?: stateStore.toDomainMessages()
                _uiState.value = readyState(messages = currentMessages, isStreaming = streamJob?.isActive == true)
            }.onFailure { throwable ->
                Log.d(TAG, "Unable to rename thread=$activeThreadId message=${throwable.message}")
            }
        }
    }

    fun refreshThreadState() {
        val preferredThreadId = threadId ?: threadSessionRepository.currentThreadId()
        refreshThreadState(
            preferredThreadId = preferredThreadId,
            createNew = preferredThreadId == null,
            fallbackToNewOnUnavailable = threadId == null && preferredThreadId != null,
        )
    }

    /**
     * Connects to the backend thread before showing chat.
     *
     * A saved thread ID is only a pointer. If it no longer belongs to the dev
     * session, the app can drop that pointer and create a fresh backend thread
     * rather than showing stale local history.
     */
    private fun refreshThreadState(
        preferredThreadId: String?,
        createNew: Boolean,
        fallbackToNewOnUnavailable: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                Log.d(TAG, "Refreshing thread state preferredThread=${preferredThreadId != null} createNew=$createNew")
                val activeSession =
                    session ?: authRepository.getSession().also {
                        session = it
                        Log.d(TAG, "Authenticated user=${it.userId}")
                    }
                if (threadId == null) {
                    _uiState.value = ChatUiState.Connecting
                    val init = initializeThread(activeSession, preferredThreadId, createNew, fallbackToNewOnUnavailable)
                    threadId = init.threadId
                    rememberThread(init.threadId)
                    Log.d(TAG, "Initialized thread=${init.threadId} createdNew=${init.createdNew}")
                }
                _uiState.value = ChatUiState.Hydrating
                hydrateState(activeSession, threadId ?: error("Thread initialization failed"), isStreaming = false)
            }.onFailure { throwable ->
                Log.e(TAG, "Unable to initialize chat", throwable)
                setError(throwable.message ?: "Unable to initialize chat")
            }
        }
    }

    private suspend fun initializeThread(
        activeSession: AuthSession,
        preferredThreadId: String?,
        createNew: Boolean,
        fallbackToNewOnUnavailable: Boolean,
    ) = runCatching {
        chatApi.initThread(
            session = activeSession,
            threadId = preferredThreadId,
            createNew = createNew || preferredThreadId == null,
            isSessionReturn = preferredThreadId != null,
        )
    }.getOrElse { throwable ->
        if (preferredThreadId == null || createNew || !fallbackToNewOnUnavailable) throw throwable
        Log.d(TAG, "Saved thread is unavailable; dropping local reference and creating a new thread")
        threadSessionRepository.forgetThread(preferredThreadId)
        recentThreadIds = threadSessionRepository.recentThreadIds()
        refreshThreadMetadata(activeSession)
        chatApi.initThread(activeSession)
    }

    /**
     * Applies stream events to UI state.
     *
     * Incremental updates come from the transport state store. Completion
     * triggers a normal thread-state fetch so the final display matches the
     * persisted checkpoint.
     */
    private suspend fun handleTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.UpdatedState -> {
                lastDebugInfo = event.debugInfo
                Log.d(
                    TAG,
                    "Transport update thread=${event.debugInfo.threadId} snapshot=${event.debugInfo.snapshotId} messages=${event.messages.size} lastPath=${event.debugInfo.lastOperationPath}",
                )
                _uiState.value = readyState(
                    messages = event.messages,
                    isStreaming = true,
                    debugInfoOverride = event.debugInfo,
                )
            }
            TransportEvent.Completed -> {
                streamJob = null
                val activeSession = session ?: return
                val activeThreadId = threadId ?: return
                lastDebugInfo = lastDebugInfo?.copy(streamStatus = "completed")
                Log.d(TAG, "Transport completed thread=$activeThreadId; hydrating canonical state")
                hydrateState(activeSession, activeThreadId, isStreaming = false)
            }
            is TransportEvent.Failed -> {
                streamJob = null
                Log.e(TAG, "Transport event failed: ${event.message}")
                renderErrorBubble(event.message)
            }
        }
    }

    /**
     * Hydrates the canonical checkpoint for a thread and publishes ready UI.
     *
     * This is the path used before first render, when switching threads, and
     * after a stream completes.
     */
    private suspend fun hydrateState(
        activeSession: AuthSession,
        activeThreadId: String,
        isStreaming: Boolean,
    ) {
        Log.d(TAG, "Hydrating thread=$activeThreadId streaming=$isStreaming")
        val state = chatApi.getThreadState(activeThreadId, activeSession)
        stateStore.replaceFromThreadState(state.threadId, state.messages, state.artifacts)
        threadId = state.threadId
        rememberThread(state.threadId)
        pendingToolInterruption = state.toPendingToolInterruption()
        refreshThreadMetadata(activeSession)
        ensureHumanReadableThreadTitle(activeSession, state.threadId, stateStore.toDomainMessages())
        suggestedPrompts = if (isStreaming) emptyList() else loadSuggestions(activeSession, state.threadId)
        Log.d(
            TAG,
            "Hydrated thread=${state.threadId} messages=${state.messages.size} suggestions=${suggestedPrompts.size} pendingTool=${pendingToolInterruption != null}",
        )
        _uiState.value = readyState(
            messages = stateStore.toDomainMessages(),
            isStreaming = isStreaming,
        )
    }

    private suspend fun refreshThreadMetadata(activeSession: AuthSession) {
        val metadataThreads = threadMetadataApi.listThreads(activeSession)
        threadSummaries =
            if (metadataThreads.isNotEmpty()) {
                metadataThreads.map { thread ->
                    ThreadSummary(
                        id = thread.id,
                        title = thread.title?.takeIf { it.isNotBlank() } ?: "New conversation",
                        subtitle = thread.lastMessageAt?.let { "Last active ${it.toDisplayTimestamp()}" } ?: "Started ${thread.createdAt.toDisplayTimestamp()}",
                    )
                }
            } else {
                recentThreadIds.map { id ->
                    ThreadSummary(
                        id = id,
                        title = if (id == threadId) currentThreadTitle else "Recent conversation",
                        subtitle = "Local only ${id.shortId()}",
                    )
                }
            }

        currentThreadTitle =
            threadSummaries.firstOrNull { it.id == threadId }?.title
                ?: currentThreadTitle.takeIf { it.isNotBlank() }
                ?: "New conversation"
    }

    /**
     * Mirrors the web-style readable thread title behavior.
     *
     * The backend thread ID is stable but not useful in a mobile drawer, so a
     * blank/default title is derived from the first user message and patched
     * back to the API-worker metadata route when available.
     */
    private suspend fun ensureHumanReadableThreadTitle(
        activeSession: AuthSession,
        activeThreadId: String,
        messages: List<ChatMessage>,
    ) {
        val existingTitle = threadSummaries.firstOrNull { it.id == activeThreadId }?.title
        if (!existingTitle.isNullOrBlank() && existingTitle != "New conversation") {
            currentThreadTitle = existingTitle
            return
        }

        val derivedTitle = messages.firstUserTextTitle() ?: return
        runCatching {
            threadMetadataApi.updateThreadTitle(activeSession, activeThreadId, derivedTitle)
            currentThreadTitle = derivedTitle
            refreshThreadMetadata(activeSession)
        }.onFailure { throwable ->
            currentThreadTitle = derivedTitle
            Log.d(TAG, "Using local derived title only thread=$activeThreadId message=${throwable.message}")
        }
    }

    private suspend fun loadSuggestions(activeSession: AuthSession, activeThreadId: String): List<String> =
        runCatching {
            chatApi.getSuggestions(activeThreadId, activeSession).suggestions
        }.onFailure { throwable ->
            Log.d(TAG, "Suggestions unavailable thread=$activeThreadId message=${throwable.message}")
        }.getOrDefault(emptyList())

    private fun renderErrorBubble(message: String, failedMessage: String? = null) {
        Log.e(TAG, "Rendering error bubble: $message")
        val current = (_uiState.value as? ChatUiState.Ready)
        val activeThreadId = current?.threadId ?: threadId
        lastFailedMessage = failedMessage
        _uiState.value = readyState(
            threadIdOverride = activeThreadId,
            messages =
                current?.messages.orEmpty() +
                    ChatMessage(
                        id = "error-${UUID.randomUUID()}",
                        role = ChatRole.System,
                        parts = listOf(MessagePart.Text("Error: ${message.toUserFacingError()}")),
                        status = MessageStatus.Failed,
                    ),
            isStreaming = false,
        )
    }

    private fun setError(message: String) {
        Log.e(TAG, "Setting error state: $message")
        _uiState.value = ChatUiState.Error(message)
    }

    private fun optimisticUser(text: String): ChatMessage =
        optimisticUser(text, attachments = emptyList())

    private fun optimisticUser(
        text: String,
        attachments: List<UploadedAttachment>,
    ): ChatMessage =
        ChatMessage(
            id = "local-user-${UUID.randomUUID()}",
            role = ChatRole.User,
            parts =
                listOf(MessagePart.Text(text)) +
                    attachments.map {
                        MessagePart.Attachment(
                            filename = it.filename,
                            contentType = it.contentType,
                            sizeBytes = it.sizeBytes,
                        )
                    },
            status = MessageStatus.Sending,
        )

    private fun optimisticAssistant(): ChatMessage =
        ChatMessage(
            id = "local-assistant-${UUID.randomUUID()}",
            role = ChatRole.Assistant,
            parts = listOf(MessagePart.Text("")),
            status = MessageStatus.Streaming,
        )

    private fun readyState(
        messages: List<ChatMessage>,
        isStreaming: Boolean,
        threadIdOverride: String? = threadId,
        debugInfoOverride: TransportDebugInfo? = null,
    ): ChatUiState.Ready =
        ChatUiState.Ready(
            threadId = threadIdOverride,
            messages = messages.withPersistentReasoning(),
            isStreaming = isStreaming,
            debugInfo = debugInfoOverride ?: debugInfo(),
            recentThreadIds = recentThreadIds,
            recentThreads = threadSummaries,
            currentThreadTitle = currentThreadTitle,
            suggestedPrompts = if (isStreaming) emptyList() else suggestedPrompts,
            pendingToolInterruption = pendingToolInterruption,
            lastFailedMessage = lastFailedMessage,
            composerAttachments = composerAttachments,
        )

    private fun rememberThread(activeThreadId: String) {
        threadSessionRepository.saveCurrentThread(activeThreadId)
        recentThreadIds = threadSessionRepository.recentThreadIds()
    }

    private fun debugInfo(): TransportDebugInfo =
        lastDebugInfo?.copy(
            threadId = threadId ?: lastDebugInfo?.threadId,
            snapshotId = stateStore.snapshotId ?: lastDebugInfo?.snapshotId,
            streamStatus = if (streamJob?.isActive == true) "streaming" else "completed",
            currentMessageCount = stateStore.messages.size,
        ) ?: TransportDebugInfo(
            threadId = threadId,
            snapshotId = stateStore.snapshotId,
            lastSseRawPayload = null,
            lastOperationPath = null,
            lastSseEventType = null,
            streamStatus = "idle",
            currentMessageCount = stateStore.messages.size,
        )

    private fun String.toUserFacingError(): String =
        lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.take(160)
            ?: "Unexpected transport error"

    private fun updateReadyComposerAttachments() {
        val current = _uiState.value as? ChatUiState.Ready ?: return
        _uiState.value = readyState(
            messages = current.messages,
            isStreaming = current.isStreaming,
            threadIdOverride = current.threadId,
        )
    }

    /**
     * Keeps reasoning visible after the final assistant message replaces the
     * transient reasoning node for a turn.
     *
     * The backend may stream reasoning as a separate message and later replace
     * the assistant content with the final answer. Caching by preceding user
     * message lets the UI keep that reasoning card attached to the same turn.
     */
    private fun List<ChatMessage>.withPersistentReasoning(): List<ChatMessage> {
        rememberReasoningForTurns(this)
        if (reasoningByUserMessageId.isEmpty()) return this

        val result = mutableListOf<ChatMessage>()
        forEachIndexed { index, message ->
            result += message
            if (message.role == ChatRole.User) {
                val nextUserIndex =
                    indexOfFirstAfter(index) { it.role == ChatRole.User }
                        .takeIf { it >= 0 }
                        ?: size
                val hasReasoningForTurn =
                    subList(index + 1, nextUserIndex).any { candidate ->
                        candidate.parts.any { it is MessagePart.Reasoning }
                    }
                val cachedReasoning = reasoningByUserMessageId[message.id]
                if (!hasReasoningForTurn && cachedReasoning != null) {
                    result +=
                        ChatMessage(
                            id = "reasoning-${message.id}",
                            role = ChatRole.Assistant,
                            parts = cachedReasoning,
                            status = MessageStatus.Complete,
                        )
                }
            }
        }
        return result
    }

    private fun rememberReasoningForTurns(messages: List<ChatMessage>) {
        var currentUserId: String? = null
        messages.forEach { message ->
            if (message.role == ChatRole.User) {
                currentUserId = message.id
            }
            val reasoning = message.parts.filterIsInstance<MessagePart.Reasoning>()
            val userId = currentUserId
            if (userId != null && reasoning.isNotEmpty()) {
                reasoningByUserMessageId[userId] = reasoning
            }
        }

        val activeUserIds = messages.filter { it.role == ChatRole.User }.mapTo(mutableSetOf()) { it.id }
        reasoningByUserMessageId.keys.retainAll(activeUserIds)
    }

    private fun Uri.toComposerAttachment(): ComposerAttachment {
        var filename: String? = null
        var sizeBytes: Long? = null
        appContext.contentResolver
            .query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                filename = cursor.stringAt(OpenableColumns.DISPLAY_NAME)
                sizeBytes = cursor.longAt(OpenableColumns.SIZE)
            }

        return ComposerAttachment(
            id = "attachment-${UUID.randomUUID()}",
            uri = toString(),
            filename = filename?.takeIf { it.isNotBlank() } ?: lastPathSegment ?: "Attachment",
            contentType = appContext.contentResolver.getType(this) ?: "application/octet-stream",
            sizeBytes = sizeBytes,
            status = AttachmentUploadStatus.Uploading,
        ).withValidation()
    }

    private fun ComposerAttachment.withValidation(): ComposerAttachment {
        val error =
            when {
                contentType !in ALLOWED_ATTACHMENT_CONTENT_TYPES ->
                    "Unsupported type"
                sizeBytes == 0L ->
                    "File is empty"
                sizeBytes != null && sizeBytes > MAX_FILE_SIZE_BYTES ->
                    "Over 50 MB"
                contentType in TEXT_ATTACHMENT_CONTENT_TYPES && sizeBytes != null && sizeBytes > MAX_TEXT_FILE_SIZE_BYTES ->
                    "Text over 2 MB"
                else -> null
            }
        return if (error == null) {
            this
        } else {
            Log.d(TAG, "Rejected attachment filename=$filename contentType=$contentType size=${sizeBytes ?: -1} reason=$error")
            copy(status = AttachmentUploadStatus.Failed, error = error)
        }
    }

    private companion object {
        private const val TAG = "YoshiChatViewModel"
        private const val MAX_THREAD_TITLE_LENGTH = 50
        private const val MAX_ATTACHMENTS_PER_MESSAGE = 5
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L
        private const val MAX_TEXT_FILE_SIZE_BYTES = 2L * 1024L * 1024L
        private val ALLOWED_ATTACHMENT_CONTENT_TYPES =
            setOf(
                "application/pdf",
                "image/png",
                "image/jpeg",
                "image/webp",
                "text/plain",
                "text/markdown",
                "text/csv",
            )
        private val TEXT_ATTACHMENT_CONTENT_TYPES =
            setOf(
                "text/plain",
                "text/markdown",
                "text/csv",
            )
    }
}

private fun List<ComposerAttachment>.toUploadedAttachments(): List<UploadedAttachment> =
    mapNotNull { attachment ->
        val fileId = attachment.fileId ?: return@mapNotNull null
        UploadedAttachment(
            fileId = fileId,
            filename = attachment.filename,
            contentType = attachment.contentType,
            sizeBytes = attachment.sizeBytes,
        )
    }

private fun List<ChatMessage>.markStreamingComplete(): List<ChatMessage> =
    map { message ->
        if (message.status == MessageStatus.Streaming) {
            message.copy(status = MessageStatus.Complete)
        } else {
            message
        }
    }

private inline fun <T> List<T>.indexOfFirstAfter(
    index: Int,
    predicate: (T) -> Boolean,
): Int {
    for (i in index + 1 until size) {
        if (predicate(this[i])) return i
    }
    return -1
}

private fun List<ChatMessage>.firstUserTextTitle(): String? =
    firstOrNull { it.role == ChatRole.User }
        ?.parts
        ?.asSequence()
        ?.filterIsInstance<MessagePart.Text>()
        ?.firstOrNull()
        ?.value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
        ?.let { text ->
            if (text.length > 50) "${text.take(47).trim()}..." else text
        }

private fun String.shortId(): String =
    if (length <= 8) this else take(8)

private fun String.toDisplayTimestamp(): String =
    substringBefore('T').takeIf { it.isNotBlank() } ?: this

private fun ThreadStateDto.toPendingToolInterruption(): PendingToolInterruption? {
    val obj = pendingInterrupt as? JsonObject ?: return null
    val toolName =
        obj.stringValue("tool_name")
            ?: obj.stringValue("toolName")
            ?: obj.stringValue("name")
            ?: obj.stringValue("tool")
            ?: "Tool action"
    val displayText =
        obj.stringValue("display_text")
            ?: obj.stringValue("displayText")
            ?: obj.stringValue("message")
            ?: obj.stringValue("reason")
            ?: "The backend is waiting for a human-in-the-loop tool response."
    val toolCallId =
        obj.stringValue("tool_call_id")
            ?: obj.stringValue("toolCallId")
            ?: obj.stringValue("id")
    return PendingToolInterruption(toolName = toolName, toolCallId = toolCallId, displayText = displayText)
}

private fun JsonObject.stringValue(key: String): String? =
    get(key)?.runCatchingJsonString()

private fun kotlinx.serialization.json.JsonElement.runCatchingJsonString(): String? =
    runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun Cursor.stringAt(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst()) return null
    return getString(index)
}

private fun Cursor.longAt(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst() || isNull(index)) return null
    return getLong(index)
}
