package com.example.yoshichat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yoshichat.ui.ChatScreen
import com.example.yoshichat.ui.ChatViewModel

@Composable
fun YoshiChatApp() {
    val context = LocalContext.current.applicationContext
    val viewModelFactory =
        remember(context) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return ChatViewModel(context) as T
                    }
                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
    val uiState by chatViewModel.uiState.collectAsState()

    ChatScreen(
        uiState = uiState,
        onSendMessage = chatViewModel::sendMessage,
        onStopStreaming = chatViewModel::stopStreaming,
        onRetryLastMessage = chatViewModel::retryLastFailedSend,
        onStartNewThread = chatViewModel::startNewThread,
        onOpenThread = chatViewModel::openThread,
        onRenameThread = chatViewModel::renameCurrentThread,
        onDismissMessage = chatViewModel::dismissMessage,
        onAddAttachments = chatViewModel::addAttachments,
        onRemoveAttachment = chatViewModel::removeAttachment,
    )
}
