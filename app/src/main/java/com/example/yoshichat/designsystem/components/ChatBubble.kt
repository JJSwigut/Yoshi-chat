package com.example.yoshichat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoshichat.designsystem.theme.YoshiColors
import com.example.yoshichat.designsystem.theme.YoshiShapes

enum class ChatBubbleVariant { Ai, User }

/**
 * Flat rounded chat bubble. The AI variant is the muted dusty green;
 * the user variant is the brighter sage. Place inside a Row with horizontal
 * arrangement of End/Start (or alignment modifiers) to position it on the
 * correct side of the conversation.
 */
@Composable
fun ChatBubble(
    text: String,
    variant: ChatBubbleVariant,
    modifier: Modifier = Modifier,
    shape: Shape = YoshiShapes.Large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
) {
    val bg = when (variant) {
        ChatBubbleVariant.Ai -> YoshiColors.ChatBubbleAi
        ChatBubbleVariant.User -> YoshiColors.ChatBubbleUser
    }
    val fg = when (variant) {
        ChatBubbleVariant.Ai -> YoshiColors.OnChatBubbleAi
        ChatBubbleVariant.User -> YoshiColors.OnChatBubbleUser
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .padding(contentPadding),
    ) {
        val styledText = remember(text) { text.toBasicMarkdownAnnotatedString() }
        Text(
            text = styledText,
            color = fg,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
}

private fun String.toBasicMarkdownAnnotatedString(): AnnotatedString {
    val output = AnnotatedString.Builder()
    var cursor = 0

    while (cursor < length) {
        val opener = indexOf("**", startIndex = cursor)
        if (opener < 0) {
            output.append(substring(cursor))
            break
        }

        val closer = indexOf("**", startIndex = opener + 2)
        if (closer < 0) {
            output.append(substring(cursor))
            break
        }

        output.append(substring(cursor, opener))
        output.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        output.append(substring(opener + 2, closer))
        output.pop()
        cursor = closer + 2
    }

    return output.toAnnotatedString()
}
