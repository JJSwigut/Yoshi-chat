package com.example.yoshichat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.yoshichat.designsystem.modifier.fastRecessed
import com.example.yoshichat.designsystem.theme.YoshiColors
import com.example.yoshichat.designsystem.theme.YoshiShapes

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything",
    enabled: Boolean = true,
    sendEnabled: Boolean = enabled,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    sendIcon: ImageVector = Icons.AutoMirrored.Filled.Send,
    stopIcon: ImageVector? = null,
    containerColor: Color = YoshiColors.RecessedFill,
    sendEnabledTint: Color = MaterialTheme.colorScheme.onSurface,
    sendDisabledTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val isStopping = onStop != null
    val canSend = value.isNotBlank() && enabled && sendEnabled
    val resolvedTextStyle = LocalTextStyle.current.merge(
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )

    Row(
        modifier = modifier
            .clip(YoshiShapes.Pill)
            .background(containerColor)
            .fastRecessed(YoshiShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = resolvedTextStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = resolvedTextStyle,
                        )
                    }
                    inner()
                }
            },
        )

        if (trailing != null) trailing()

        IconButton(
            onClick = { onStop?.invoke() ?: onSend() },
            enabled = isStopping || canSend,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (isStopping) stopIcon ?: sendIcon else sendIcon,
                contentDescription = if (isStopping) "Stop response" else "Send",
                tint = if (isStopping || canSend) sendEnabledTint else sendDisabledTint,
            )
        }
    }
}
