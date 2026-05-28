package com.example.yoshichat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.yoshichat.designsystem.modifier.neumorphicRecessed
import com.example.yoshichat.designsystem.theme.NeumorphicStyle
import com.example.yoshichat.designsystem.theme.YoshiColors
import com.example.yoshichat.designsystem.theme.YoshiNeumorphism
import com.example.yoshichat.designsystem.theme.YoshiShapes

/**
 * Pill-shaped text field that sits in a recessed neumorphic "well". Uses
 * [BasicTextField] under the hood, not [androidx.compose.material3.TextField],
 * so it never paints the Material 3 outline / underline.
 *
 * Pair with a small label drawn above the field (see the showcase preview)
 * rather than using the field's built-in label slot.
 */
@Composable
fun RecessedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    shape: Shape = YoshiShapes.Pill,
    style: NeumorphicStyle = YoshiNeumorphism.Recessed,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val resolvedTextStyle = LocalTextStyle.current.merge(
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = resolvedTextStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .clip(shape)
            .background(YoshiColors.RecessedFill)
            .neumorphicRecessed(shape, style),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        CompositionLocalProvider(LocalTextStyle provides resolvedTextStyle) {
                            leadingIcon()
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = resolvedTextStyle,
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        CompositionLocalProvider(LocalTextStyle provides resolvedTextStyle) {
                            trailingIcon()
                        }
                    }
                }
            }
        },
    )
}
