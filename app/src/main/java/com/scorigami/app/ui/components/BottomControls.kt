package com.scorigami.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scorigami.app.data.ColorMapType
import com.scorigami.app.data.FilteredOutRangeColor
import com.scorigami.app.data.GradientType
import com.scorigami.app.data.ScorigamiUiState
import kotlin.math.hypot
import kotlin.math.max

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BottomControls(
    uiState: ScorigamiUiState,
    onGradientChange: (GradientType) -> Unit,
    onToggleColorMap: () -> Unit,
    onRecencyStartYearChange: (Int) -> Unit,
    onFrequencyRangeChange: (Int, Int) -> Unit,
    onResetView: () -> Unit,
    minLegend: String,
    maxLegend: String
) {
    val haptics = LocalHapticFeedback.current
    val controlFontSize = 12.sp
    val controlFontWeight = FontWeight.SemiBold
    val minLegendWidth = 34.dp
    val maxLegendWidth = 44.dp
    val legendGap = 6.dp
    val sidePadding = 10.dp
    val reservedForFullColor = 126.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val legendWidth = (maxWidth - (sidePadding * 2) - minLegendWidth - legendGap - maxLegendWidth - legendGap - reservedForFullColor)
            .coerceIn(120.dp, 170.dp)
        val segmentedWidth = legendWidth + 20.dp
        val segmentedStartOffset = minLegendWidth + legendGap - 10.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Zoom in, then tap for score info",
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(segmentedStartOffset))
                Row(
                    modifier = Modifier
                        .width(segmentedWidth)
                        .background(Color(0xFF1A1D26), RoundedCornerShape(16.dp))
                        .padding(2.dp)
                ) {
                    val isFreq = uiState.gradientType == GradientType.FREQUENCY
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isFreq) Color(0xFF6B6E76) else Color.Transparent,
                                RoundedCornerShape(13.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onGradientChange(GradientType.FREQUENCY)
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Frequency", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (!isFreq) Color(0xFF6B6E76) else Color.Transparent,
                                RoundedCornerShape(13.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onGradientChange(GradientType.RECENCY)
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Recency", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .width(86.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetView()
                        }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        "Reset",
                        color = Color.White,
                        fontSize = controlFontSize,
                        fontWeight = controlFontWeight,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Reset view",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    minLegend,
                    color = Color.White,
                    fontSize = controlFontSize,
                    fontWeight = controlFontWeight,
                    modifier = Modifier.width(minLegendWidth),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.width(legendGap))
                LegendBar(
                    uiState = uiState,
                    onRecencyStartYearChange = onRecencyStartYearChange,
                    onFrequencyRangeChange = onFrequencyRangeChange,
                    modifier = Modifier.width(legendWidth + 16.dp).height(36.dp)
                )
                Spacer(modifier = Modifier.width(legendGap))
                Text(
                    maxLegend,
                    color = Color.White,
                    fontSize = controlFontSize,
                    fontWeight = controlFontWeight,
                    modifier = Modifier.width(maxLegendWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .width(122.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleColorMap()
                        }
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        "Full Color",
                        color = Color.White,
                        fontSize = controlFontSize,
                        fontWeight = controlFontWeight,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .requiredSize(18.dp)
                            .aspectRatio(1f)
                            .border(1.dp, Color.White, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.colorMapType == ColorMapType.FULL_SPECTRUM) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Enabled",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = if (uiState.gradientType == GradientType.FREQUENCY) {
                    "Move the sliders to show less or more popular scores"
                } else {
                    "Move the slider to only show scores since the first year."
                },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 0.dp)
            )
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun LegendBar(
    uiState: ScorigamiUiState,
    onRecencyStartYearChange: (Int) -> Unit,
    onFrequencyRangeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFrequencyHandle by remember { mutableStateOf<Int?>(null) }
    val currentUiState by rememberUpdatedState(uiState)
    val currentRecencyChange by rememberUpdatedState(onRecencyStartYearChange)
    val currentFrequencyChange by rememberUpdatedState(onFrequencyRangeChange)
    val edgeExtensionPx = with(LocalDensity.current) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val state = currentUiState
                    if (state.gradientType == GradientType.RECENCY) {
                        val width = (size.width.toFloat() - edgeExtensionPx * 2f).coerceAtLeast(1f)
                        val localX = (tap.x - edgeExtensionPx).coerceIn(0f, width)
                        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        val range = max(1, currentYear - state.earliestGameYear)
                        val fraction = (localX / width).coerceIn(0f, 1f)
                        currentRecencyChange(state.earliestGameYear + (range * fraction).toInt())
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val state = currentUiState
                        if (state.gradientType == GradientType.FREQUENCY) {
                            val width = (size.width.toFloat() - edgeExtensionPx * 2f).coerceAtLeast(1f)
                            val totalBuckets = max(1, state.highestCounter)
                            val lowerX = edgeExtensionPx + width * ((state.selectedFrequencyStartCount - 1).toFloat() / totalBuckets.toFloat())
                            val upperX = edgeExtensionPx + width * (state.selectedFrequencyEndCount.toFloat() / totalBuckets.toFloat())
                            val legendHeight = 20.dp.toPx()
                            val knobRadius = 4.dp.toPx()
                            val knobCenterY = legendHeight + knobRadius
                            val lineTouchHalfWidth = 12.dp.toPx()
                            val lowerDistance = hypot((offset.x - lowerX).toDouble(), (offset.y - knobCenterY).toDouble()).toFloat()
                            val upperDistance = hypot((offset.x - upperX).toDouble(), (offset.y - knobCenterY).toDouble()).toFloat()
                            val lowerOnLine = offset.y <= legendHeight && kotlin.math.abs(offset.x - lowerX) <= lineTouchHalfWidth
                            val upperOnLine = offset.y <= legendHeight && kotlin.math.abs(offset.x - upperX) <= lineTouchHalfWidth
                            activeFrequencyHandle = when {
                                (lowerDistance <= knobRadius * 2f || lowerOnLine) &&
                                    (upperDistance <= knobRadius * 2f || upperOnLine) ->
                                    if (lowerDistance <= upperDistance) 0 else 1
                                lowerDistance <= knobRadius * 2f || lowerOnLine -> 0
                                upperDistance <= knobRadius * 2f || upperOnLine -> 1
                                else -> if (lowerDistance <= upperDistance) 0 else 1
                            }
                        }
                    },
                    onDragEnd = { activeFrequencyHandle = null },
                    onDragCancel = { activeFrequencyHandle = null }
                ) { change, _ ->
                    val state = currentUiState
                    val width = (size.width.toFloat() - edgeExtensionPx * 2f).coerceAtLeast(1f)
                    val x = (change.position.x - edgeExtensionPx).coerceIn(0f, width)
                    if (state.gradientType == GradientType.RECENCY) {
                        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        val range = max(1, currentYear - state.earliestGameYear)
                        val fraction = (x / width).coerceIn(0f, 1f)
                        currentRecencyChange(state.earliestGameYear + (range * fraction).toInt())
                    } else {
                        val totalBuckets = max(1, state.highestCounter)
                        if (activeFrequencyHandle == 0) {
                            val nextStart = ((x / width).coerceIn(0f, 1f) * totalBuckets).toInt() + 1
                            currentFrequencyChange(
                                nextStart.coerceIn(1, state.selectedFrequencyEndCount),
                                state.selectedFrequencyEndCount
                            )
                        } else {
                            val nextEnd = kotlin.math.ceil(((x / width).coerceIn(0f, 1f) * totalBuckets).toDouble()).toInt().coerceAtLeast(1)
                            currentFrequencyChange(
                                state.selectedFrequencyStartCount,
                                nextEnd.coerceIn(state.selectedFrequencyStartCount, state.highestCounter)
                            )
                        }
                    }
                    change.consume()
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawLegendFill(uiState, edgeExtensionPx)
            drawRect(
                color = Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                topLeft = Offset(edgeExtensionPx, 0f),
                size = androidx.compose.ui.geometry.Size(size.width - edgeExtensionPx * 2f, 20.dp.toPx())
            )
            if (uiState.gradientType == GradientType.RECENCY) {
                drawRecencyMarker(uiState, edgeExtensionPx)
            } else {
                drawFrequencyMarkers(uiState, edgeExtensionPx)
            }
        }
    }
}

private fun DrawScope.drawLegendFill(uiState: ScorigamiUiState, edgeExtensionPx: Float) {
    val legendHeight = 20.dp.toPx()
    val barWidth = (size.width - edgeExtensionPx * 2f).coerceAtLeast(1f)
    if (uiState.gradientType == GradientType.RECENCY && uiState.selectedRecencyStartYear > uiState.earliestGameYear) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val totalRange = max(1, currentYear - uiState.earliestGameYear).toFloat()
        val excludedFraction = ((uiState.selectedRecencyStartYear - uiState.earliestGameYear).toFloat() / totalRange).coerceIn(0f, 1f)
        val excludedWidth = barWidth * excludedFraction
        drawRect(
            color = FilteredOutRangeColor,
            topLeft = Offset(edgeExtensionPx, 0f),
            size = androidx.compose.ui.geometry.Size(excludedWidth, legendHeight)
        )
        drawLegendGradient(uiState, edgeExtensionPx + excludedWidth, barWidth - excludedWidth, legendHeight)
    } else if (uiState.gradientType == GradientType.FREQUENCY) {
        val totalBuckets = max(1, uiState.highestCounter).toFloat()
        val lowerFraction = ((uiState.selectedFrequencyStartCount - 1).toFloat() / totalBuckets).coerceIn(0f, 1f)
        val upperFraction = (uiState.selectedFrequencyEndCount.toFloat() / totalBuckets).coerceIn(lowerFraction, 1f)
        val lowerWidth = barWidth * lowerFraction
        val activeWidth = barWidth * (upperFraction - lowerFraction)
        val upperWidth = barWidth - lowerWidth - activeWidth
        drawRect(
            color = FilteredOutRangeColor,
            topLeft = Offset(edgeExtensionPx, 0f),
            size = androidx.compose.ui.geometry.Size(lowerWidth, legendHeight)
        )
        drawLegendGradient(uiState, edgeExtensionPx + lowerWidth, activeWidth, legendHeight)
        drawRect(
            color = FilteredOutRangeColor,
            topLeft = Offset(edgeExtensionPx + lowerWidth + activeWidth, 0f),
            size = androidx.compose.ui.geometry.Size(upperWidth, legendHeight)
        )
    } else {
        drawLegendGradient(uiState, edgeExtensionPx, barWidth, legendHeight)
    }
}

private fun DrawScope.drawLegendGradient(
    uiState: ScorigamiUiState,
    startX: Float,
    width: Float,
    height: Float
) {
    if (width <= 0f) return
    if (uiState.colorMapType == ColorMapType.RED_SPECTRUM) {
        val slices = 42
        val w = width / slices
        for (i in 0 until slices) {
            val sat = ((i + 1) * 2.5f / 100f).coerceIn(0f, 1f)
            drawRect(
                color = lerp(Color(0xFF606060), Color.Red, sat),
                topLeft = Offset(startX + i * w, 0f),
                size = androidx.compose.ui.geometry.Size(w, height)
            )
        }
    } else {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red),
                startX = startX,
                endX = startX + width
            ),
            topLeft = Offset(startX, 0f),
            size = androidx.compose.ui.geometry.Size(width, height)
        )
    }
}

private fun DrawScope.drawRecencyMarker(uiState: ScorigamiUiState, edgeExtensionPx: Float) {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val range = max(1, currentYear - uiState.earliestGameYear).toFloat()
    val fraction = ((uiState.selectedRecencyStartYear - uiState.earliestGameYear).toFloat() / range).coerceIn(0f, 1f)
    val barWidth = (size.width - edgeExtensionPx * 2f).coerceAtLeast(1f)
    drawMarkerAt(edgeExtensionPx + (barWidth * fraction))
}

private fun DrawScope.drawFrequencyMarkers(uiState: ScorigamiUiState, edgeExtensionPx: Float) {
    val totalBuckets = max(1, uiState.highestCounter).toFloat()
    val barWidth = (size.width - edgeExtensionPx * 2f).coerceAtLeast(1f)
    val lowerX = edgeExtensionPx + barWidth * ((uiState.selectedFrequencyStartCount - 1).toFloat() / totalBuckets)
    val upperX = edgeExtensionPx + barWidth * (uiState.selectedFrequencyEndCount.toFloat() / totalBuckets)
    drawMarkerAt(lowerX)
    drawMarkerAt(upperX)
}

private fun DrawScope.drawMarkerAt(x: Float) {
    val legendHeight = 20.dp.toPx()
    val lineWidth = 2f
    val knobRadius = 4.dp.toPx()
    val clampedX = x.coerceIn(0f, size.width)
    val lineLeft = (clampedX - lineWidth / 2f).coerceIn(0f, size.width - lineWidth)
    drawRect(
        color = Color.White,
        topLeft = Offset(lineLeft, 0f),
        size = androidx.compose.ui.geometry.Size(lineWidth, legendHeight)
    )
    drawCircle(
        color = Color.White,
        radius = knobRadius,
        center = Offset(clampedX, legendHeight + knobRadius)
    )
}
