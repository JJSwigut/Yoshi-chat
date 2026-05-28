package com.example.yoshichat.designsystem.modifier

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yoshichat.designsystem.theme.NeumorphicStyle

fun Modifier.neumorphicRaised(
    shape: Shape,
    style: NeumorphicStyle,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val blurPx = style.blur.toPx()
    val distPx = style.distance.toPx()
    val shadowPaint = Paint().apply {
        color = style.shadowColor
        asFrameworkPaint().maskFilter =
            BlurMaskFilter(blurPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
    }
    val highlightPaint = Paint().apply {
        color = style.highlightColor
        asFrameworkPaint().maskFilter =
            BlurMaskFilter(blurPx.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
    }

    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(distPx, distPx)
            canvas.drawOutline(outline, shadowPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(-distPx, -distPx)
            canvas.drawOutline(outline, highlightPaint)
            canvas.restore()
        }
    }
}

fun Modifier.neumorphicRecessed(
    shape: Shape,
    style: NeumorphicStyle,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val blurPx = style.blur.toPx()
    val distPx = style.distance.toPx()
    val bounds = Rect(Offset.Zero, size)

    onDrawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            drawInsetEdge(canvas, outline, bounds, style.shadowColor, blurPx, distPx, distPx)
            drawInsetEdge(canvas, outline, bounds, style.highlightColor, blurPx, -distPx, -distPx)
        }
    }
}

fun Modifier.fastRecessed(
    shape: Shape,
    topShadow: Color = Color.Black.copy(alpha = 0.28f),
    bottomHighlight: Color = Color.White.copy(alpha = 0.08f),
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokeWidthPx = strokeWidth.toPx()
    val fillBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    topShadow.copy(alpha = 0.16f),
                    Color.Transparent,
                    bottomHighlight.copy(alpha = 0.06f),
                ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        )

    onDrawWithContent {
        drawContent()
        drawOutline(outline = outline, brush = fillBrush)
        drawLine(
            color = topShadow,
            start = Offset(size.width * 0.12f, strokeWidthPx),
            end = Offset(size.width * 0.88f, strokeWidthPx),
            strokeWidth = strokeWidthPx,
        )
        drawLine(
            color = topShadow.copy(alpha = topShadow.alpha * 0.65f),
            start = Offset(strokeWidthPx, size.height * 0.22f),
            end = Offset(strokeWidthPx, size.height * 0.78f),
            strokeWidth = strokeWidthPx,
        )
        drawLine(
            color = bottomHighlight,
            start = Offset(size.width * 0.12f, size.height - strokeWidthPx),
            end = Offset(size.width * 0.88f, size.height - strokeWidthPx),
            strokeWidth = strokeWidthPx,
        )
        drawLine(
            color = bottomHighlight.copy(alpha = bottomHighlight.alpha * 0.75f),
            start = Offset(size.width - strokeWidthPx, size.height * 0.22f),
            end = Offset(size.width - strokeWidthPx, size.height * 0.78f),
            strokeWidth = strokeWidthPx,
        )
        drawOutline(
            outline = outline,
            color = bottomHighlight.copy(alpha = 0.10f),
            style = Stroke(width = strokeWidthPx),
        )
    }
}

private fun drawInsetEdge(
    canvas: Canvas,
    outline: Outline,
    bounds: Rect,
    color: Color,
    blurPx: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val paint = Paint().apply { this.color = color }
    canvas.saveLayer(bounds, paint)
    canvas.drawOutline(outline, paint)

    paint.asFrameworkPaint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        if (blurPx > 0f) {
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        this.color = android.graphics.Color.BLACK
    }
    canvas.translate(offsetX, offsetY)
    canvas.drawOutline(outline, paint)
    canvas.restore()
}
