package com.scorigami.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scorigami.app.data.BoardCell
import com.scorigami.app.data.GradientType
import com.scorigami.app.data.ScorigamiUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HeatmapView(
    uiState: ScorigamiUiState,
    resetRequestId: Int,
    onCellTapped: (BoardCell) -> Unit,
    getCellColor: (BoardCell) -> Color,
    getCellTextColor: (BoardCell) -> Color
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val axisWidth = 34.dp
    val topAxisHeight = 48.dp
    val baseCellPx = with(density) { 8.dp.toPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var viewportAnimationJob by remember { mutableStateOf<Job?>(null) }
    var panMomentumJob by remember { mutableStateOf<Job?>(null) }
    var lastGestureAtMs by remember { mutableLongStateOf(0L) }
    var panDeltaPerEvent by remember { mutableStateOf(Offset.Zero) }
    var lastGestureZoomFactor by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val plotWidthPx = with(density) { (maxWidth - axisWidth).toPx().coerceAtLeast(1f) }
        val plotHeightPx = with(density) { (maxHeight - topAxisHeight).toPx().coerceAtLeast(1f) }

        val boardBaseWidth = (uiState.highestWinningScore + 1) * baseCellPx
        val boardBaseHeight = (uiState.highestLosingScore + 1) * baseCellPx

        fun clampOffset(target: Offset, s: Float): Offset {
            val minX = min(0f, plotWidthPx - boardBaseWidth * s)
            val minY = min(0f, plotHeightPx - boardBaseHeight * s)
            return Offset(
                x = target.x.coerceIn(minX, 0f),
                y = target.y.coerceIn(minY, 0f)
            )
        }

        LaunchedEffect(plotWidthPx, plotHeightPx, scale) {
            panOffset = clampOffset(panOffset, scale)
        }

        fun animateViewport(targetScale: Float, targetOffset: Offset, durationMs: Long = 650L) {
            viewportAnimationJob?.cancel()
            viewportAnimationJob = scope.launch {
                val startScale = scale
                val startOffset = panOffset
                val frames = 40
                val step = max(1L, durationMs / frames)
                repeat(frames + 1) { i ->
                    val t = i / frames.toFloat()
                    val eased = t * t * (3f - 2f * t)
                    val s = startScale + (targetScale - startScale) * eased
                    val o = Offset(
                        x = startOffset.x + (targetOffset.x - startOffset.x) * eased,
                        y = startOffset.y + (targetOffset.y - startOffset.y) * eased
                    )
                    scale = s
                    panOffset = clampOffset(o, s)
                    if (i < frames) delay(step)
                }
                scale = targetScale
                panOffset = clampOffset(targetOffset, targetScale)
                viewportAnimationJob = null
            }
        }

        LaunchedEffect(resetRequestId) {
            if (resetRequestId > 0) {
                animateViewport(1f, Offset.Zero, durationMs = 560L)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.board) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewportAnimationJob?.cancel()
                        panMomentumJob?.cancel()
                        val oldScale = scale
                        val newScale = (scale * zoom).coerceIn(1f, 9f)
                        val focusX = centroid.x - with(density) { axisWidth.toPx() }
                        val focusY = centroid.y - with(density) { topAxisHeight.toPx() }
                        val focus = Offset(focusX, focusY)
                        val scaleRatio = newScale / max(oldScale, 0.0001f)
                        // Anchor zoom to finger centroid, then apply pan delta.
                        val scaled = focus - ((focus - panOffset) * scaleRatio)
                        val translated = scaled + pan
                        scale = newScale
                        panOffset = clampOffset(translated, scale)
                        val now = android.os.SystemClock.uptimeMillis()
                        val panMag = hypot(pan.x.toDouble(), pan.y.toDouble()).toFloat()
                        if (panMag > 0.06f) {
                            // Smooth raw pan deltas; avoids dt spikes that cause post-release surges.
                            panDeltaPerEvent = Offset(
                                x = panDeltaPerEvent.x * 0.75f + pan.x * 0.25f,
                                y = panDeltaPerEvent.y * 0.75f + pan.y * 0.25f
                            )
                        }
                        lastGestureZoomFactor = zoom
                        lastGestureAtMs = now

                        panMomentumJob = scope.launch {
                            val stamp = lastGestureAtMs
                            delay(10L)
                            if (stamp != lastGestureAtMs) return@launch
                            if (kotlin.math.abs(lastGestureZoomFactor - 1f) > 0.03f) return@launch
                            var delta = panDeltaPerEvent
                            if (hypot(delta.x.toDouble(), delta.y.toDouble()) < 0.08) {
                                return@launch
                            }

                            repeat(36) {
                                val start = panOffset
                                val next = clampOffset(start + delta, scale)
                                val moved = next - start
                                panOffset = next

                                // Pure deceleration: no acceleration phase after finger-up.
                                delta *= 0.94f

                                val movedMag = hypot(moved.x.toDouble(), moved.y.toDouble())
                                val deltaMag = hypot(delta.x.toDouble(), delta.y.toDouble())
                                if (movedMag < 0.05 || deltaMag < 0.05) {
                                    return@launch
                                }
                                delay(16L)
                            }
                            panDeltaPerEvent = Offset.Zero
                        }
                    }
                }
                .pointerInput(uiState.board, scale, panOffset) {
                    detectTapGestures { tap ->
                        val tapX = tap.x - with(density) { axisWidth.toPx() }
                        val tapY = tap.y - with(density) { topAxisHeight.toPx() }
                        if (tapX < 0f || tapY < 0f) return@detectTapGestures
                        val scaledCell = baseCellPx * scale
                        val boardX = (tapX - panOffset.x) / scaledCell
                        val boardY = (tapY - panOffset.y) / scaledCell
                        val win = floor(boardX).toInt()
                        val lose = floor(boardY).toInt()
                        if (lose in uiState.board.indices && win in uiState.board[lose].indices) {
                            val cell = uiState.board[lose][win]
                            if (cell.validScore && cell.label.isNotEmpty()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val showLabels = scale >= 2.6f && scaledCell >= with(density) { 20.sp.toPx() }
                                val isAlreadyMax = scale >= 8.99f
                                if (!showLabels) {
                                    val targetScale = 9f
                                    val targetCell = baseCellPx * targetScale
                                    val centerX = win * targetCell + targetCell / 2f
                                    val centerY = lose * targetCell + targetCell / 2f
                                    val rawOffset = Offset(
                                        x = (plotWidthPx / 2f) - centerX,
                                        y = (plotHeightPx / 2f) - centerY
                                    )
                                    animateViewport(targetScale, clampOffset(rawOffset, targetScale))
                                } else if (!isAlreadyMax) {
                                    val targetScale = 9f
                                    val targetCell = baseCellPx * targetScale
                                    val centerX = win * targetCell + targetCell / 2f
                                    val centerY = lose * targetCell + targetCell / 2f
                                    val rawOffset = Offset(
                                        x = (plotWidthPx / 2f) - centerX,
                                        y = (plotHeightPx / 2f) - centerY
                                    )
                                    animateViewport(targetScale, clampOffset(rawOffset, targetScale))
                                    scope.launch {
                                        delay(140L)
                                        onCellTapped(cell)
                                    }
                                } else {
                                    onCellTapped(cell)
                                }
                            }
                        }
                    }
                }
        ) {
            drawRect(Color.Black)

            val axisWidthPx = with(density) { axisWidth.toPx() }
            val topAxisHeightPx = with(density) { topAxisHeight.toPx() }
            val scaledCell = baseCellPx * scale

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = with(density) { 14.sp.toPx() }
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                drawText("Winning Score", size.width / 2f, topAxisHeightPx - 52f, paint)
            }

            clipRect(left = axisWidthPx, top = topAxisHeightPx, right = size.width, bottom = size.height) {
                translate(left = axisWidthPx + panOffset.x, top = topAxisHeightPx + panOffset.y) {
                    val rounded = scale >= 2.2f
                    val inset = if (rounded) min(1.8f, scaledCell * 0.09f) else 0.25f
                    val radius = if (rounded) min(5f, scaledCell * 0.2f) else 0f

                    val visibleColStart = max(0, floor((-panOffset.x) / scaledCell).toInt())
                    val visibleColEnd = min(uiState.highestWinningScore, ceil((plotWidthPx - panOffset.x) / scaledCell).toInt())
                    val visibleRowStart = max(0, floor((-panOffset.y) / scaledCell).toInt())
                    val visibleRowEnd = min(uiState.highestLosingScore, ceil((plotHeightPx - panOffset.y) / scaledCell).toInt())

                    for (r in visibleRowStart..visibleRowEnd) {
                        val row = uiState.board[r]
                        for (c in visibleColStart..min(visibleColEnd, row.lastIndex)) {
                            val cell = row[c]
                            val rect = Rect(
                                left = c * scaledCell + inset,
                                top = r * scaledCell + inset,
                                right = (c + 1) * scaledCell - inset,
                                bottom = (r + 1) * scaledCell - inset
                            )
                            if (radius > 0f) {
                                drawRoundRect(
                                    color = getCellColor(cell),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                                )
                            } else {
                                drawRect(
                                    color = getCellColor(cell),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                                )
                            }
                        }
                    }

                    if (scale >= 2.6f && scaledCell >= with(density) { 20.sp.toPx() }) {
                        val textSizePx = min(
                            with(density) { 15.sp.toPx() },
                            max(with(density) { 8.sp.toPx() }, scaledCell * 0.32f)
                        )
                        drawVisibleCellLabels(
                            uiState = uiState,
                            visibleRowStart = visibleRowStart,
                            visibleRowEnd = visibleRowEnd,
                            visibleColStart = visibleColStart,
                            visibleColEnd = visibleColEnd,
                            scaledCell = scaledCell,
                            textSizePx = textSizePx,
                            scale = scale,
                            gradientType = uiState.gradientType,
                            getCellTextColor = getCellTextColor
                        )
                    }
                }
            }

            drawAxisLabels(
                uiState = uiState,
                axisWidthPx = axisWidthPx,
                topAxisHeightPx = topAxisHeightPx,
                scaledCell = scaledCell,
                panOffset = panOffset,
                scale = scale
            )
        }
    }
}

private fun DrawScope.drawVisibleCellLabels(
    uiState: ScorigamiUiState,
    visibleRowStart: Int,
    visibleRowEnd: Int,
    visibleColStart: Int,
    visibleColEnd: Int,
    scaledCell: Float,
    textSizePx: Float,
    scale: Float,
    gradientType: GradientType,
    getCellTextColor: (BoardCell) -> Color
) {
    val native = drawContext.canvas.nativeCanvas
    val showDetailLine = scale >= 4.0f
    val detailSizePx = max(8f, textSizePx * 0.72f)
    for (r in visibleRowStart..visibleRowEnd) {
        val row = uiState.board[r]
        for (c in visibleColStart..min(visibleColEnd, row.lastIndex)) {
            val cell = row[c]
            if (!cell.validScore || cell.label.isEmpty()) continue
            val textColor = getCellTextColor(cell)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    255,
                    (textColor.red * 255).toInt(),
                    (textColor.green * 255).toInt(),
                    (textColor.blue * 255).toInt()
                )
                this.textSize = textSizePx
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val centerX = c * scaledCell + scaledCell / 2f
            val scoreOffsetY = if (showDetailLine) scaledCell * 0.12f else 0f
            val centerY = r * scaledCell + scaledCell / 2f - scoreOffsetY - (paint.descent() + paint.ascent()) / 2f
            native.drawText(cell.label, centerX, centerY, paint)

            if (showDetailLine && cell.occurrences > 0) {
                val detail = if (gradientType == GradientType.RECENCY) {
                    "(${cell.lastGame.takeLast(4).toIntOrNull() ?: 1920})"
                } else {
                    "(${cell.occurrences})"
                }
                val detailPaint = android.graphics.Paint().apply {
                    color = paint.color
                    textSize = detailSizePx
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                val detailX = (c + 1) * scaledCell - max(1f, scaledCell * 0.08f)
                val detailY = (r + 1) * scaledCell - max(1f, scaledCell * 0.08f)
                native.drawText(detail, detailX, detailY, detailPaint)
            }
        }
    }
}

private fun DrawScope.drawAxisLabels(
    uiState: ScorigamiUiState,
    axisWidthPx: Float,
    topAxisHeightPx: Float,
    scaledCell: Float,
    panOffset: Offset,
    scale: Float
) {
    val native = drawContext.canvas.nativeCanvas
    val topPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val leftPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    val axisStep = if (scale > 2.6f) 1 else 5

    var x = 0
    while (x <= uiState.highestWinningScore) {
        val sx = axisWidthPx + panOffset.x + x * scaledCell + scaledCell / 2f
        if (sx in (axisWidthPx - 35f)..size.width) {
            native.drawText(x.toString(), sx, topAxisHeightPx - 2f, topPaint)
        }
        x += axisStep
    }

    var y = 0
    while (y <= uiState.highestLosingScore) {
        val cellCenterY = topAxisHeightPx + panOffset.y + y * scaledCell + scaledCell / 2f
        if (cellCenterY in (topAxisHeightPx - 8f)..size.height) {
            val baselineY = cellCenterY - (leftPaint.descent() + leftPaint.ascent()) / 2f
            native.drawText(y.toString(), axisWidthPx - 20f, baselineY, leftPaint)
        }
        y += axisStep
    }
}
