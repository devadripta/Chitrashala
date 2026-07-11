package com.dripta.galleryformoto.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Tools available in the markup/annotation editor. */
enum class MarkupTool { NONE, PEN, ARROW, TEXT }

/** A committed annotation, stored entirely in the source bitmap's own pixel coordinates. */
sealed class MarkupElement {
    abstract val color: Color

    data class DrawPath(val points: List<Offset>, val strokeWidth: Float, override val color: Color) : MarkupElement()
    data class Arrow(val start: Offset, val end: Offset, val strokeWidth: Float, override val color: Color) : MarkupElement()
    data class TextAnnotation(val text: String, val position: Offset, val fontSize: Float, override val color: Color) : MarkupElement()
    data class BlurRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) : MarkupElement() {
        override val color = Color.Transparent
    }
}

/** Maps between the bitmap's own pixel space and the space this composable is laid out in (ContentScale.Fit). */
class FitTransform(val bitmapW: Int, val bitmapH: Int, val containerW: Float, val containerH: Float) {
    val scale = min(containerW / bitmapW, containerH / bitmapH).takeIf { it > 0f } ?: 1f
    val offsetX = (containerW - bitmapW * scale) / 2f
    val offsetY = (containerH - bitmapH * scale) / 2f

    fun toBitmapSpace(containerOffset: Offset): Offset =
        Offset((containerOffset.x - offsetX) / scale, (containerOffset.y - offsetY) / scale)

    fun toContainerSpace(bitmapOffset: Offset): Offset =
        Offset(bitmapOffset.x * scale + offsetX, bitmapOffset.y * scale + offsetY)
}

/**
 * Transparent overlay drawn on top of the (already displayed) photo. Captures pen/arrow/text
 * gestures in container space, converts them to bitmap-pixel space so they bake correctly onto
 * the full-resolution image later, and renders committed + in-progress annotations live.
 */
@Composable
fun MarkupOverlay(
    bitmapW: Int,
    bitmapH: Int,
    tool: MarkupTool,
    elements: List<MarkupElement>,
    strokeColor: Color,
    strokeWidth: Float,
    onElementAdded: (MarkupElement) -> Unit,
    onTextTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val transform = remember(bitmapW, bitmapH, containerSize) {
            FitTransform(bitmapW, bitmapH, containerSize.width.toFloat(), containerSize.height.toFloat())
        }

        var currentPathPoints by remember(tool) { mutableStateOf<List<Offset>>(emptyList()) }
        var arrowStart by remember(tool) { mutableStateOf<Offset?>(null) }
        var arrowEnd by remember(tool) { mutableStateOf<Offset?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool) {
                    when (tool) {
                        MarkupTool.PEN -> detectDragGestures(
                            onDragStart = { offset -> currentPathPoints = listOf(transform.toBitmapSpace(offset)) },
                            onDrag = { change, _ ->
                                currentPathPoints = currentPathPoints + transform.toBitmapSpace(change.position)
                            },
                            onDragEnd = {
                                if (currentPathPoints.size > 1) {
                                    onElementAdded(MarkupElement.DrawPath(currentPathPoints, strokeWidth, strokeColor))
                                }
                                currentPathPoints = emptyList()
                            }
                        )
                        MarkupTool.ARROW -> detectDragGestures(
                            onDragStart = { offset ->
                                val bp = transform.toBitmapSpace(offset)
                                arrowStart = bp
                                arrowEnd = bp
                            },
                            onDrag = { change, _ -> arrowEnd = transform.toBitmapSpace(change.position) },
                            onDragEnd = {
                                val s = arrowStart; val e = arrowEnd
                                if (s != null && e != null && (s - e).getDistance() > 4f) {
                                    onElementAdded(MarkupElement.Arrow(s, e, strokeWidth, strokeColor))
                                }
                                arrowStart = null; arrowEnd = null
                            }
                        )
                        MarkupTool.TEXT -> detectTapGestures(
                            onTap = { offset -> onTextTap(transform.toBitmapSpace(offset)) }
                        )
                        MarkupTool.NONE -> {}
                    }
                }
        ) {
            elements.forEach { element -> drawElement(element, transform) }

            if (currentPathPoints.size > 1) {
                drawElement(MarkupElement.DrawPath(currentPathPoints, strokeWidth, strokeColor), transform)
            }
            val s = arrowStart; val e = arrowEnd
            if (s != null && e != null) {
                drawElement(MarkupElement.Arrow(s, e, strokeWidth, strokeColor), transform)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawElement(element: MarkupElement, transform: FitTransform) {
    when (element) {
        is MarkupElement.DrawPath -> {
            if (element.points.size < 2) return
            val path = Path()
            val start = transform.toContainerSpace(element.points.first())
            path.moveTo(start.x, start.y)
            for (i in 1 until element.points.size) {
                val p = transform.toContainerSpace(element.points[i])
                path.lineTo(p.x, p.y)
            }
            drawPath(
                path,
                color = element.color,
                style = Stroke(width = element.strokeWidth * transform.scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
        }
        is MarkupElement.Arrow -> {
            val start = transform.toContainerSpace(element.start)
            val end = transform.toContainerSpace(element.end)
            val strokeW = element.strokeWidth * transform.scale
            drawLine(element.color, start, end, strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val headLength = strokeW * 5f
            val headAngle = Math.PI / 7
            val p1 = Offset(
                (end.x - headLength * cos(angle - headAngle)).toFloat(),
                (end.y - headLength * sin(angle - headAngle)).toFloat()
            )
            val p2 = Offset(
                (end.x - headLength * cos(angle + headAngle)).toFloat(),
                (end.y - headLength * sin(angle + headAngle)).toFloat()
            )
            val headPath = Path().apply {
                moveTo(end.x, end.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                close()
            }
            drawPath(headPath, color = element.color)
        }
        is MarkupElement.TextAnnotation -> {
            val pos = transform.toContainerSpace(element.position)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = element.color.toArgb()
                    textSize = element.fontSize * transform.scale
                    isAntiAlias = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
                drawText(element.text, pos.x, pos.y, paint)
            }
        }
        is MarkupElement.BlurRegion -> {
            val topLeft = transform.toContainerSpace(Offset(element.left, element.top))
            val bottomRight = transform.toContainerSpace(Offset(element.right, element.bottom))
            drawRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                style = Stroke(width = 3f)
            )
        }
    }
}
