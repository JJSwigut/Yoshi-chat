package com.example.yoshichat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yoshichat.R
import com.example.yoshichat.designsystem.components.AmbientBackground
import com.example.yoshichat.designsystem.components.ChatBubble
import com.example.yoshichat.designsystem.components.ChatBubbleVariant
import com.example.yoshichat.designsystem.components.ChatComposer
import com.example.yoshichat.designsystem.components.NeumorphicElevation
import com.example.yoshichat.designsystem.components.NeumorphicSurface
import com.example.yoshichat.designsystem.components.RecessedTextField
import com.example.yoshichat.designsystem.theme.YoshiColors
import com.example.yoshichat.designsystem.theme.YoshiShapes
import com.example.yoshichat.designsystem.theme.YoshiTheme
import com.example.yoshichat.data.config.DevServerConfig
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
import kotlin.math.roundToInt

private object ChatSpacing {
    val ScreenHorizontal = 20.dp
    val ScreenVertical = 20.dp
    val SectionGap = 12.dp
    val ComposerGap = 8.dp
    val ComposerHeight = 64.dp
    val AttachmentTrayHeight = 58.dp
    val PromptTrayHeight = 52.dp
    val MessageGap = 14.dp
    val BubbleHorizontal = 22.dp
    val BubbleVertical = 14.dp
    val CardPadding = 16.dp
    val CompactCardPadding = 14.dp
    val CardGap = 8.dp
    val PromptGap = 10.dp
    val DrawerHorizontal = 22.dp
    val DrawerVertical = 24.dp
    val DrawerGap = 18.dp
    val DrawerRowGap = 10.dp
    val IconButtonSize = 40.dp
    val ThreadMenuSize = 56.dp
}

private val AttachmentMimeTypes =
    arrayOf(
        "application/pdf",
        "image/png",
        "image/jpeg",
        "image/webp",
        "text/plain",
        "text/markdown",
        "text/csv",
    )

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onRetryLastMessage: () -> Unit,
    onStartNewThread: () -> Unit,
    onOpenThread: (String) -> Unit,
    onRenameThread: (String) -> Unit,
    onDismissMessage: (String) -> Unit,
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var debugExpanded by remember { mutableStateOf(false) }
    var threadDrawerExpanded by remember { mutableStateOf(false) }
    val readyState = uiState as? ChatUiState.Ready
    val attachments = readyState?.composerAttachments.orEmpty()
    val attachmentsCanSend = attachments.none { it.status != AttachmentUploadStatus.Uploaded }
    val composerEnabled = readyState != null && !readyState.isStreaming
    val attachmentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            onAddAttachments(uris)
        }
    val density = LocalDensity.current
    val imeBottomPadding = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardLiftPadding =
        if (imeBottomPadding > 0.dp) {
            imeBottomPadding - ChatSpacing.ScreenVertical
        } else {
            0.dp
        }.coerceAtLeast(0.dp)
    val showPromptOverlay = readyState?.suggestedPrompts?.isNotEmpty() == true && readyState.isStreaming.not()
    val attachmentTrayClearance =
        if (attachments.isNotEmpty()) {
            ChatSpacing.ComposerGap + ChatSpacing.AttachmentTrayHeight
        } else {
            0.dp
        }
    val promptTrayClearance =
        if (showPromptOverlay) {
            ChatSpacing.ComposerGap + ChatSpacing.PromptTrayHeight
        } else {
            0.dp
        }
    val messageListBottomClearance =
        ChatSpacing.ScreenVertical +
            keyboardLiftPadding +
            ChatSpacing.ComposerHeight +
            attachmentTrayClearance +
            promptTrayClearance +
            ChatSpacing.MessageGap

    AmbientBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = ChatSpacing.ScreenHorizontal,
                                vertical = ChatSpacing.ScreenVertical,
                            ),
                    verticalArrangement = Arrangement.spacedBy(ChatSpacing.SectionGap),
                ) {
                    ThreadBar(
                        title = readyState?.currentThreadTitle,
                        threadId = readyState?.threadId,
                        onOpenThreads = { threadDrawerExpanded = true },
                        onOpenDebug = { debugExpanded = true },
                    )

                    MessageList(
                        uiState = uiState,
                        onDismissMessage = onDismissMessage,
                        bottomContentPadding = messageListBottomClearance,
                        modifier = Modifier.weight(1f),
                    )

                    readyState?.pendingToolInterruption?.let { interruption ->
                        PendingToolCard(interruption)
                    }

                    if (readyState?.lastFailedMessage != null && !readyState.isStreaming) {
                        RetryCard(onRetry = onRetryLastMessage)
                    }
                }

                ComposerPanel(
                    suggestions = readyState?.suggestedPrompts.orEmpty(),
                    attachments = attachments,
                    enabled = composerEnabled,
                    sendEnabled = attachmentsCanSend,
                    isStreaming = readyState?.isStreaming == true,
                    onSendMessage = onSendMessage,
                    onStopStreaming = onStopStreaming,
                    onOpenAttachmentPicker = { attachmentPicker.launch(AttachmentMimeTypes) },
                    onRemoveAttachment = onRemoveAttachment,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = ChatSpacing.ScreenVertical + keyboardLiftPadding,
                            ),
                )

                ThreadDrawer(
                    readyState = readyState,
                    expanded = threadDrawerExpanded,
                    onExpandedChange = { threadDrawerExpanded = it },
                    onStartNewThread = {
                        threadDrawerExpanded = false
                        onStartNewThread()
                    },
                    onOpenThread = { threadId ->
                        threadDrawerExpanded = false
                        onOpenThread(threadId)
                    },
                    onRenameThread = onRenameThread,
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                DebugDrawer(
                    debugInfo = readyState?.debugInfo,
                    expanded = debugExpanded,
                    onExpandedChange = { debugExpanded = it },
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun ThreadBar(
    title: String?,
    threadId: String?,
    onOpenThreads: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(ChatSpacing.ThreadMenuSize)
                    .clip(YoshiShapes.Pill)
                    .combinedClickable(
                        onClick = onOpenThreads,
                        onLongClick = onOpenDebug,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open conversations. Long press for debug.",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title?.takeIf { it.isNotBlank() } ?: threadId?.let { "Thread ${it.shortId()}" } ?: "No thread",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageList(
    uiState: ChatUiState,
    onDismissMessage: (String) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val readyState = uiState as? ChatUiState.Ready
    val shouldShowThinking = readyState?.messages?.shouldShowTurnThinkingIndicator(readyState.isStreaming) == true
    val itemCount =
        when (uiState) {
            is ChatUiState.Ready -> uiState.messages.size + if (shouldShowThinking) 1 else 0
            else -> 1
        }

    LaunchedEffect(readyState?.threadId, readyState?.messages?.size, readyState?.isStreaming, shouldShowThinking) {
        if (itemCount > 0 && readyState != null && !readyState.isStreaming) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChatSpacing.MessageGap),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
    ) {
        when (uiState) {
            is ChatUiState.Ready -> {
                items(
                    items = uiState.messages,
                    key = { message -> message.id },
                ) { message ->
                    MessageRow(
                        message = message,
                        onDismiss = { onDismissMessage(message.id) },
                        onCopyText = { text ->
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Yoshi message", text))
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (uiState.messages.shouldShowTurnThinkingIndicator(uiState.isStreaming)) {
                    item(key = "turn-thinking") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            ThinkingBubble()
                        }
                    }
                }
            }

            ChatUiState.Connecting -> item { StatusBubble("Connecting...") }
            ChatUiState.Hydrating -> item { StatusBubble("Loading messages...") }
            is ChatUiState.Error -> item { StatusBubble(uiState.message) }
        }
    }
}

@Composable
private fun ComposerPanel(
    suggestions: List<String>,
    attachments: List<ComposerAttachment>,
    enabled: Boolean,
    sendEnabled: Boolean,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onOpenAttachmentPicker: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputBlank by remember { mutableStateOf(true) }
    val showSuggestions = suggestions.isNotEmpty() && !isStreaming && inputBlank

    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChatSpacing.ComposerGap),
    ) {
        AnimatedVisibility(
            visible = showSuggestions,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            SuggestedPrompts(
                prompts = suggestions,
                onPromptSelected = { prompt ->
                    onSendMessage(prompt)
                },
            )
        }

        Box(modifier = Modifier.padding(horizontal = ChatSpacing.ScreenHorizontal)) {
            AttachmentTray(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
            )
        }

        Box(modifier = Modifier.padding(horizontal = ChatSpacing.ScreenHorizontal)) {
            ComposerInput(
                enabled = enabled,
                sendEnabled = sendEnabled,
                isStreaming = isStreaming,
                onInputBlankChange = { inputBlank = it },
                onSendMessage = onSendMessage,
                onStopStreaming = onStopStreaming,
                onOpenAttachmentPicker = onOpenAttachmentPicker,
            )
        }
    }
}

@Composable
private fun ComposerInput(
    enabled: Boolean,
    sendEnabled: Boolean,
    isStreaming: Boolean,
    onInputBlankChange: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onOpenAttachmentPicker: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    ChatComposer(
        value = input,
        onValueChange = { next ->
            val wasBlank = input.isBlank()
            input = next
            val isBlank = next.isBlank()
            if (wasBlank != isBlank) {
                onInputBlankChange(isBlank)
            }
        },
        onSend = {
            val text = input
            input = ""
            onInputBlankChange(true)
            onSendMessage(text)
        },
        onStop = if (isStreaming) onStopStreaming else null,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        sendEnabled = sendEnabled,
        placeholder = "Message Yoshi...",
        stopIcon = Icons.Default.Close,
        containerColor = MaterialTheme.colorScheme.surface,
        leading = {
            IconButton(
                onClick = onOpenAttachmentPicker,
                enabled = enabled,
                modifier = Modifier.size(ChatSpacing.IconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onCopyText: (String) -> Unit,
) {
    val horizontalArrangement =
        if (message.role == ChatRole.User) Arrangement.End else Arrangement.Start
    val variant =
        if (message.role == ChatRole.User) ChatBubbleVariant.User else ChatBubbleVariant.Ai
    val isFailed = message.status == MessageStatus.Failed

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
    ) {
        DismissibleMessageContainer(
            dismissible = isFailed,
            onDismiss = onDismiss,
            verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap),
            horizontalAlignment = if (message.role == ChatRole.User) Alignment.End else Alignment.Start,
        ) {
            message.parts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> {
                        if (part.value.isNotBlank()) {
                            if (isFailed) {
                                ErrorBubble(text = part.value)
                            } else {
                                CopyableChatBubble(
                                    text = part.value,
                                    variant = variant,
                                    onCopy = { onCopyText(part.value) },
                                )
                            }
                        }
                    }

                    is MessagePart.ToolInterruption -> ToolInterruptionSurface(part)
                    is MessagePart.Attachment -> AttachmentBubble(part)
                    is MessagePart.Reasoning -> ReasoningCard(part)
                }
            }
        }
    }
}

@Composable
private fun DismissibleMessageContainer(
    dismissible: Boolean,
    onDismiss: () -> Unit,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = tween(durationMillis = 160),
        label = "messageDismissOffset",
    )
    val backgroundColor by animateColorAsState(
        targetValue =
            if (dismissible && kotlin.math.abs(dragOffset) > 24f) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 160),
        label = "messageDismissColor",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth(0.86f)
                .clip(YoshiShapes.Large)
                .background(backgroundColor)
                .pointerInput(dismissible) {
                    if (!dismissible) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffset) > 160f) {
                                onDismiss()
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = (dragOffset + dragAmount).coerceIn(-220f, 220f)
                        },
                    )
                }
                .offset { IntOffset(animatedOffset.roundToInt(), 0) },
    ) {
        Column(
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

@Composable
private fun ErrorBubble(text: String) {
    Box(
        modifier =
            Modifier
                .clip(YoshiShapes.Large)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f))
                .padding(horizontal = ChatSpacing.BubbleHorizontal, vertical = ChatSpacing.BubbleVertical),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CopyableChatBubble(
    text: String,
    variant: ChatBubbleVariant,
    onCopy: () -> Unit,
) {
    ChatBubble(
        text = text,
        variant = variant,
        modifier =
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = onCopy,
            ),
    )
}

@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinkingDots")
    Row(
        modifier =
            Modifier
                .clip(YoshiShapes.Pill)
                .background(YoshiColors.ChatBubbleAi)
                .padding(horizontal = 20.dp, vertical = ChatSpacing.BubbleVertical),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 520, delayMillis = index * 120),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "thinkingDot$index",
            )
            Box(
                modifier =
                    Modifier
                        .width(8.dp)
                        .heightIn(min = 8.dp)
                        .clip(YoshiShapes.Pill)
                        .background(YoshiColors.OnChatBubbleAi)
                        .alpha(alpha),
            )
        }
    }
}

@Composable
private fun SuggestedPrompts(
    prompts: List<String>,
    onPromptSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ChatSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(ChatSpacing.PromptGap),
    ) {
        prompts.forEach { prompt ->
            NeumorphicSurface(
                modifier = Modifier.clickable { onPromptSelected(prompt) },
                elevation = NeumorphicElevation.Raised,
                shape = YoshiShapes.Pill,
                shadowPadding = 6.dp,
                contentPadding = PaddingValues(horizontal = ChatSpacing.CardPadding, vertical = ChatSpacing.PromptGap),
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AttachmentTray(
    attachments: List<ComposerAttachment>,
    onRemoveAttachment: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(120)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap),
        ) {
            attachments.forEach { attachment ->
                AttachmentChip(
                    attachment = attachment,
                    onRemove = { onRemoveAttachment(attachment.id) },
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
) {
    val isError = attachment.status == AttachmentUploadStatus.Failed
    val status =
        when (attachment.status) {
            AttachmentUploadStatus.Uploading -> "Uploading"
            AttachmentUploadStatus.Uploaded -> attachment.sizeBytes?.toDisplaySize() ?: "Ready"
            AttachmentUploadStatus.Failed -> attachment.error ?: "Failed"
        }
    NeumorphicSurface(
        elevation = NeumorphicElevation.Raised,
        shape = YoshiShapes.Pill,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shadowPadding = 6.dp,
        contentPadding = PaddingValues(horizontal = ChatSpacing.CompactCardPadding, vertical = ChatSpacing.CardGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttachmentBubble(attachment: MessagePart.Attachment) {
    NeumorphicSurface(
        elevation = NeumorphicElevation.Raised,
        shape = YoshiShapes.Large,
        shadowPadding = 6.dp,
        contentPadding = PaddingValues(horizontal = ChatSpacing.CompactCardPadding, vertical = ChatSpacing.PromptGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = attachment.filename,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = attachment.sizeBytes?.toDisplaySize() ?: attachment.contentType,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReasoningCard(reasoning: MessagePart.Reasoning) {
    var expanded by remember(reasoning.value) { mutableStateOf(false) }
    NeumorphicSurface(
        elevation = NeumorphicElevation.Recessed,
        shape = YoshiShapes.Large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = ChatSpacing.CardPadding, vertical = ChatSpacing.PromptGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (expanded) "Hide reasoning" else reasoning.value.summaryLine(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse reasoning" else "Expand reasoning",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(100)),
            ) {
                Text(
                    text = reasoning.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PendingToolCard(interruption: PendingToolInterruption) {
    NeumorphicSurface(
        elevation = NeumorphicElevation.Recessed,
        shape = YoshiShapes.Large,
        contentPadding = PaddingValues(ChatSpacing.CardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap)) {
            Text(
                text = "Tool action required",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${interruption.toolName}: ${interruption.displayText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            interruption.toolCallId?.let { toolCallId ->
                Text(
                    text = "Tool call ${toolCallId.shortId()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RetryCard(onRetry: () -> Unit) {
    NeumorphicSurface(
        elevation = NeumorphicElevation.Recessed,
        shape = YoshiShapes.Large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
        contentPadding = PaddingValues(horizontal = ChatSpacing.CardPadding, vertical = ChatSpacing.PromptGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Last send failed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ToolInterruptionSurface(interruption: MessagePart.ToolInterruption) {
    NeumorphicSurface(
        elevation = NeumorphicElevation.Raised,
        shape = YoshiShapes.Medium,
        shadowPadding = 8.dp,
        contentPadding = PaddingValues(ChatSpacing.CompactCardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap)) {
            Text(
                text = interruption.toolName,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = interruption.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        ChatBubble(text = text, variant = ChatBubbleVariant.Ai)
    }
}

@Composable
private fun DebugDrawer(
    debugInfo: TransportDebugInfo?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerWidth = 328.dp
    val context = LocalContext.current
    val debugSummary = debugInfo.toDebugSummary()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(animationSpec = tween(260)) { it } + fadeIn(animationSpec = tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut(animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            NeumorphicSurface(
                modifier =
                    Modifier
                        .width(drawerWidth)
                        .heightIn(min = 240.dp, max = 440.dp),
                elevation = NeumorphicElevation.Recessed,
                shape = YoshiShapes.ExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = PaddingValues(0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(ChatSpacing.DrawerGap),
                    verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Transport debug",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Yoshi debug summary", debugSummary))
                                    Toast.makeText(context, "Debug summary copied", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy_24),
                                    contentDescription = "Copy debug summary",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { onExpandedChange(false) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Hide debug panel",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    DebugLine("Thread ID", debugInfo?.threadId)
                    DebugLine("Snapshot ID", debugInfo?.snapshotId)
                    DebugLine("Stream", debugInfo?.streamStatus)
                    DebugLine("Last SSE raw payload", debugInfo?.lastSseRawPayload?.take(280))
                    DebugLine("Last operation path", debugInfo?.lastOperationPath)
                    DebugLine("Last SSE", debugInfo?.lastSseEventType)
                    DebugLine("Current message count", debugInfo?.currentMessageCount?.toString())
                }
            }
        }

    }
}

@Composable
private fun ThreadDrawer(
    readyState: ChatUiState.Ready?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStartNewThread: () -> Unit,
    onOpenThread: (String) -> Unit,
    onRenameThread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var titleInput by remember(readyState?.threadId, readyState?.currentThreadTitle) {
        mutableStateOf("")
    }
    var renaming by remember(readyState?.threadId) { mutableStateOf(false) }
    val currentTitle = readyState?.currentThreadTitle?.takeIf { it.isNotBlank() } ?: "New conversation"

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable { onExpandedChange(false) },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(animationSpec = tween(260)) { -it } + fadeIn(animationSpec = tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            NeumorphicSurface(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(360.dp),
                elevation = NeumorphicElevation.Recessed,
                shape = YoshiShapes.SideSheet,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = PaddingValues(horizontal = ChatSpacing.DrawerHorizontal, vertical = ChatSpacing.DrawerVertical),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(ChatSpacing.DrawerGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Threads",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = onStartNewThread) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New conversation",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    NeumorphicSurface(
                        elevation = NeumorphicElevation.Raised,
                        shape = YoshiShapes.Large,
                        shadowPadding = 6.dp,
                        contentPadding = PaddingValues(horizontal = ChatSpacing.CardPadding, vertical = ChatSpacing.CompactCardPadding),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChatSpacing.DrawerRowGap)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = readyState?.threadId?.let { "ID ${it.shortId()}" } ?: "Not connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        titleInput = ""
                                        renaming = !renaming
                                    },
                                    enabled = readyState?.threadId != null,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename conversation",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = renaming,
                                enter = fadeIn(animationSpec = tween(140)),
                                exit = fadeOut(animationSpec = tween(120)),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap),
                                ) {
                                    RecessedTextField(
                                        value = titleInput,
                                        onValueChange = { titleInput = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = currentTitle,
                                    )
                                    TextButton(
                                        onClick = {
                                            onRenameThread(titleInput)
                                            renaming = false
                                        },
                                        enabled = readyState?.threadId != null && titleInput.isNotBlank(),
                                    ) {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }

                    val recentThreads = readyState?.recentThreads.orEmpty()
                    if (recentThreads.isEmpty()) {
                        EmptyThreadList(onStartNewThread = onStartNewThread)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ChatSpacing.DrawerRowGap),
                            contentPadding = PaddingValues(bottom = ChatSpacing.CardPadding),
                        ) {
                            items(recentThreads, key = { it.id }) { thread ->
                                ThreadSummaryRow(
                                    thread = thread,
                                    selected = thread.id == readyState?.threadId,
                                    onClick = { onOpenThread(thread.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyThreadList(onStartNewThread: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChatSpacing.DrawerRowGap),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.weight(1f, fill = false))
        Text(
            text = "No saved conversations yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onStartNewThread) {
            Text("Start one")
        }
    }
}

@Composable
private fun ThreadSummaryRow(
    thread: ThreadSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NeumorphicSurface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = if (selected) NeumorphicElevation.Recessed else NeumorphicElevation.Raised,
        shape = YoshiShapes.Large,
        shadowPadding = if (selected) 0.dp else 6.dp,
        contentPadding = PaddingValues(horizontal = ChatSpacing.CompactCardPadding, vertical = ChatSpacing.SectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChatSpacing.CardGap)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = thread.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private fun TransportDebugInfo?.toDebugSummary(): String =
    buildString {
        appendLine("Backend URL: ${DevServerConfig.agentsBaseUrl}")
        appendLine("Connection: ${DevServerConfig.connectionMode}")
        appendLine("Thread ID: ${this@toDebugSummary?.threadId ?: "-"}")
        appendLine("Hydration: ${if (this@toDebugSummary?.threadId != null) "complete" else "pending"}")
        appendLine("Stream: ${this@toDebugSummary?.streamStatus ?: "idle"}")
        appendLine("Last SSE: ${this@toDebugSummary?.lastSseEventType ?: "-"}")
        appendLine("Messages: ${this@toDebugSummary?.currentMessageCount ?: 0}")
    }.trimEnd()

@Composable
private fun DebugLine(label: String, value: String?) {
    Text(
        text = "$label: ${value ?: "-"}",
        style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun String.shortId(): String =
    if (length <= 8) this else take(8)

private fun List<ChatMessage>.shouldShowTurnThinkingIndicator(isStreaming: Boolean): Boolean {
    if (!isStreaming) return false
    val lastUserIndex = indexOfLast { it.role == ChatRole.User }
    if (lastUserIndex < 0) return false
    return drop(lastUserIndex + 1)
        .none { message ->
            message.role == ChatRole.Assistant &&
                message.parts.any { part -> part is MessagePart.Text && part.value.isNotBlank() }
        }
}

private fun Long.toDisplaySize(): String =
    when {
        this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
        this >= 1024L -> "${this / 1024L} KB"
        else -> "$this B"
    }

private fun String.summaryLine(): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    if (compact.isBlank()) return "Reasoning available"
    return if (compact.length > 72) "${compact.take(69).trim()}..." else compact
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    YoshiTheme(darkTheme = true) {
        ChatScreen(
            uiState =
                ChatUiState.Ready(
                    threadId = null,
                    messages =
                        listOf(
                            ChatMessage(
                                id = "preview-message",
                                role = ChatRole.Assistant,
                                parts = listOf(MessagePart.Text("Ready to connect to the local Yoshi agent.")),
                                status = MessageStatus.Complete,
                            ),
                        ),
                    isStreaming = false,
                    debugInfo =
                        TransportDebugInfo(
                            threadId = "thread-preview",
                            snapshotId = "snapshot-preview",
                            lastSseRawPayload = """{"type":"update-state","operations":[]}""",
                            lastOperationPath = """["messages",0]""",
                            lastSseEventType = "update-state",
                            streamStatus = "completed",
                            currentMessageCount = 1,
                        ),
            ),
            onSendMessage = {},
            onStopStreaming = {},
            onRetryLastMessage = {},
            onStartNewThread = {},
            onOpenThread = {},
            onRenameThread = {},
            onDismissMessage = {},
            onAddAttachments = {},
            onRemoveAttachment = {},
        )
    }
}
