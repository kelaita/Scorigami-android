package com.scorigami.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.scorigami.app.ui.theme.ScorigamiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class GameRecord(
    val winningScore: Int,
    val losingScore: Int,
    val occurrences: Int,
    val lastGame: String
)

data class BoardCell(
    val winningScore: Int,
    val losingScore: Int,
    val label: String,
    val occurrences: Int,
    val gamesUrl: String,
    val lastGame: String,
    val frequencySaturation: Float,
    val recencySaturation: Float,
    val plural: String,
    val validScore: Boolean
)

enum class GradientType { FREQUENCY, RECENCY }
enum class ColorMapType { FULL_SPECTRUM, RED_SPECTRUM }

data class ScoreDetails(
    val score: String,
    val occurrences: Int,
    val gamesUrl: String,
    val lastGame: String,
    val plural: String
)

data class ScorigamiUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val board: List<List<BoardCell>> = emptyList(),
    val highestWinningScore: Int = 0,
    val highestLosingScore: Int = 0,
    val highestCounter: Int = 1,
    val earliestGameYear: Int = 1920,
    val gradientType: GradientType = GradientType.FREQUENCY,
    val colorMapType: ColorMapType = ColorMapType.FULL_SPECTRUM
)

class ScorigamiViewModel : ViewModel() {
    var uiState by mutableStateOf(ScorigamiUiState())
        private set

    private val sourceUrl = "https://pomace.net/scores.html"
    private val gameUrlTemplate = "https://www.pro-football-reference.com/boxscores/game_scores_find.cgi?pts_win=WWWW&pts_lose=LLLL"
    private val fullSpectrumPalette = buildFullSpectrumPalette()

    init {
        loadScores()
    }

    fun setGradientType(type: GradientType) {
        uiState = uiState.copy(gradientType = type)
    }

    fun toggleColorMapType() {
        val next = if (uiState.colorMapType == ColorMapType.FULL_SPECTRUM) {
            ColorMapType.RED_SPECTRUM
        } else {
            ColorMapType.FULL_SPECTRUM
        }
        uiState = uiState.copy(colorMapType = next)
    }

    fun getMinLegendValue(): String {
        return if (uiState.gradientType == GradientType.FREQUENCY) {
            "1"
        } else {
            uiState.earliestGameYear.toString()
        }
    }

    fun getMaxLegendValue(): String {
        return if (uiState.gradientType == GradientType.FREQUENCY) {
            uiState.highestCounter.toString()
        } else {
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        }
    }

    fun getCellColor(cell: BoardCell): Color {
        val sat = if (uiState.gradientType == GradientType.FREQUENCY) {
            cell.frequencySaturation
        } else {
            cell.recencySaturation
        }
        if (!cell.validScore || sat <= 0f) {
            return Color.Black
        }
        return if (uiState.colorMapType == ColorMapType.RED_SPECTRUM) {
            lerpColor(Color(0xFF606060), Color.Red, sat.coerceIn(0f, 1f))
        } else {
            val index = (sat * 99f).toInt().coerceIn(0, 99)
            fullSpectrumPalette[index]
        }
    }

    fun getCellTextColor(cell: BoardCell): Color {
        val sat = if (uiState.gradientType == GradientType.FREQUENCY) {
            cell.frequencySaturation
        } else {
            cell.recencySaturation
        }
        return if (sat < 0.2f || sat > 0.8f || uiState.colorMapType == ColorMapType.RED_SPECTRUM) {
            Color.White
        } else {
            Color.Black
        }
    }

    fun toScoreDetails(cell: BoardCell): ScoreDetails {
        return ScoreDetails(
            score = cell.label,
            occurrences = cell.occurrences,
            gamesUrl = cell.gamesUrl,
            lastGame = cell.lastGame,
            plural = cell.plural
        )
    }

    private fun loadScores() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val parsed = withContext(Dispatchers.IO) {
                    parseGames(URL(sourceUrl).readText())
                }
                val boardData = buildBoard(parsed)
                uiState = boardData.copy(isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Unable to load score data. Check internet connection and try again."
                )
            }
        }
    }

    private fun parseGames(html: String): List<GameRecord> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tbody tr")
        val games = mutableListOf<GameRecord>()
        for (row in rows) {
            val win = row.select("td[data-stat=pts_win]").text().toIntOrNull() ?: continue
            val lose = row.select("td[data-stat=pts_lose]").text().toIntOrNull() ?: continue
            val count = row.select("td[data-stat=counter]").text().toIntOrNull() ?: 0
            val rawLast = row.select("td[data-stat=last_game]").text()
            val last = rawLast.replace(" vs.", if (win == lose) " tied the" else " beat the")
            games += GameRecord(win, lose, count, last)
        }
        return games.sortedBy { it.winningScore }
    }

    private fun buildBoard(games: List<GameRecord>): ScorigamiUiState {
        val highestWinning = games.maxOfOrNull { it.winningScore } ?: 0
        val highestLosing = games.maxOfOrNull { it.losingScore } ?: 0
        val highestCounter = games.maxOfOrNull { it.occurrences }?.coerceAtLeast(1) ?: 1
        val earliestYear = games.minOfOrNull { extractYear(it.lastGame) } ?: 1920
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        val map = games.associateBy { it.winningScore to it.losingScore }

        val board = (0..highestLosing).map { losing ->
            (0..highestWinning).map { winning ->
                val game = map[winning to losing]
                val valid = winning >= losing
                if (game == null) {
                    BoardCell(
                        winningScore = winning,
                        losingScore = losing,
                        label = if (valid) "$winning-$losing" else "",
                        occurrences = 0,
                        gamesUrl = buildGameUrl(winning, losing),
                        lastGame = "",
                        frequencySaturation = 0f,
                        recencySaturation = 0f,
                        plural = "s",
                        validScore = valid
                    )
                } else {
                    BoardCell(
                        winningScore = winning,
                        losingScore = losing,
                        label = if (valid) "$winning-$losing" else "",
                        occurrences = game.occurrences,
                        gamesUrl = buildGameUrl(winning, losing),
                        lastGame = game.lastGame,
                        frequencySaturation = getSaturation(
                            minValue = 1,
                            maxValue = highestCounter,
                            value = game.occurrences,
                            skewLower = 0.01f,
                            skewUpper = 0.55f
                        ),
                        recencySaturation = getSaturation(
                            minValue = earliestYear,
                            maxValue = currentYear,
                            value = extractYear(game.lastGame),
                            skewLower = 0f,
                            skewUpper = 1f
                        ),
                        plural = if (game.occurrences == 1) "" else "s",
                        validScore = valid
                    )
                }
            }
        }

        return ScorigamiUiState(
            board = board,
            highestWinningScore = highestWinning,
            highestLosingScore = highestLosing,
            highestCounter = highestCounter,
            earliestGameYear = earliestYear
        )
    }

    private fun buildGameUrl(winning: Int, losing: Int): String {
        return gameUrlTemplate
            .replace("WWWW", winning.toString())
            .replace("LLLL", losing.toString())
    }

    private fun extractYear(lastGame: String): Int {
        return lastGame.takeLast(4).toIntOrNull() ?: 1920
    }

    private fun getSaturation(
        minValue: Int,
        maxValue: Int,
        value: Int,
        skewLower: Float,
        skewUpper: Float
    ): Float {
        if (maxValue <= minValue) return 1f
        val newMax = (maxValue - minValue) * skewUpper + minValue
        if (newMax <= minValue) return 1f
        val ratio = (value - minValue) / (newMax - minValue)
        val sat = (1f - skewLower) * ratio + skewLower
        return sat.coerceIn(0f, 1f)
    }

    private fun buildFullSpectrumPalette(): List<Color> {
        val palette = mutableListOf<Color>()
        for (i in 0..99) {
            val t = i / 99f
            palette += when {
                t < 0.25f -> lerpColor(Color(0xFF0000FF), Color(0xFF00FFFF), t / 0.25f)
                t < 0.5f -> lerpColor(Color(0xFF00FFFF), Color(0xFF00FF00), (t - 0.25f) / 0.25f)
                t < 0.75f -> lerpColor(Color(0xFF00FF00), Color(0xFFFFFF00), (t - 0.5f) / 0.25f)
                else -> lerpColor(Color(0xFFFFFF00), Color(0xFFFF0000), (t - 0.75f) / 0.25f)
            }
        }
        return palette
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val c = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * c,
            green = a.green + (b.green - a.green) * c,
            blue = a.blue + (b.blue - a.blue) * c,
            alpha = 1f
        )
    }

    private fun hsvColor(h: Float, s: Float, v: Float): Color {
        return Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScorigamiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    ScorigamiApp()
                }
            }
        }
    }
}

@Composable
private fun ScorigamiApp() {
    val vm: ScorigamiViewModel = viewModel()
    var showAbout by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableStateOf<ScoreDetails?>(null) }

    if (vm.uiState.isLoading) {
        LoadingScreen()
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.scorigami_title),
                        contentDescription = "Scorigami",
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    IconButton(onClick = { showAbout = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        },
        bottomBar = {
            BottomControls(
                uiState = vm.uiState,
                onGradientChange = vm::setGradientType,
                onToggleColorMap = vm::toggleColorMapType,
                minLegend = vm.getMinLegendValue(),
                maxLegend = vm.getMaxLegendValue()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when {
                vm.uiState.error != null -> {
                    Text(
                        text = vm.uiState.error ?: "Unknown error",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }

                else -> {
                    HeatmapView(
                        uiState = vm.uiState,
                        onCellTapped = { cell ->
                            selectedScore = vm.toScoreDetails(cell)
                        },
                        getCellColor = vm::getCellColor,
                        getCellTextColor = vm::getCellTextColor
                    )
                }
            }
        }
    }

    if (selectedScore != null) {
        ScoreSheet(details = selectedScore!!, onDismiss = { selectedScore = null })
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.scorigami_launch),
            contentDescription = "Scorigami Loading",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            contentScale = ContentScale.FillWidth
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreSheet(details: ScoreDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF171717)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = details.score,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (details.occurrences > 0) {
                Text(
                    text = "This score has happened ${details.occurrences} time${details.plural}.",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "Most recent game", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                Text(text = details.lastGame, color = Color.White, fontSize = 16.sp)
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(details.gamesUrl)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View games")
                }
            } else {
                Text(text = "SCORIGAMI!", color = Color(0xFFFFA500), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(text = "No game has ever ended with this score...yet.", color = Color.White, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun HeatmapView(
    uiState: ScorigamiUiState,
    onCellTapped: (BoardCell) -> Unit,
    getCellColor: (BoardCell) -> Color,
    getCellTextColor: (BoardCell) -> Color
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val axisWidth = 26.dp
    val topAxisHeight = 42.dp
    val baseCellPx = with(density) { 8.dp.toPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.board) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (scale * zoom).coerceIn(1f, 9f)
                        val focusX = centroid.x - with(density) { axisWidth.toPx() }
                        val focusY = centroid.y - with(density) { topAxisHeight.toPx() }
                        val contentX = (focusX - panOffset.x) / max(oldScale, 0.0001f)
                        val contentY = (focusY - panOffset.y) / max(oldScale, 0.0001f)
                        val moved = panOffset + pan
                        val scaled = Offset(
                            x = focusX - contentX * newScale,
                            y = focusY - contentY * newScale
                        )
                        val blended = Offset((moved.x + scaled.x) / 2f, (moved.y + scaled.y) / 2f)
                        scale = newScale
                        panOffset = clampOffset(blended, scale)
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
                                onCellTapped(cell)
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
                drawText("Winning Score", size.width / 2f, with(density) { 18.sp.toPx() }, paint)
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
    getCellTextColor: (BoardCell) -> Color
) {
    val native = drawContext.canvas.nativeCanvas
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
            val centerY = r * scaledCell + scaledCell / 2f - (paint.descent() + paint.ascent()) / 2f
            native.drawText(cell.label, centerX, centerY, paint)
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
            native.drawText(x.toString(), sx, topAxisHeightPx - 4f, topPaint)
        }
        x += axisStep
    }

    var y = 0
    while (y <= uiState.highestLosingScore) {
        val cellCenterY = topAxisHeightPx + panOffset.y + y * scaledCell + scaledCell / 2f
        if (cellCenterY in (topAxisHeightPx - 8f)..size.height) {
            val baselineY = cellCenterY - (leftPaint.descent() + leftPaint.ascent()) / 2f
            native.drawText(y.toString(), axisWidthPx - 2f, baselineY, leftPaint)
        }
        y += axisStep
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun BottomControls(
    uiState: ScorigamiUiState,
    onGradientChange: (GradientType) -> Unit,
    onToggleColorMap: () -> Unit,
    minLegend: String,
    maxLegend: String
) {
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
                .navigationBarsPadding()
                .padding(start = sidePadding, end = sidePadding, top = sidePadding, bottom = sidePadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Zoom in, then tap for score info",
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth()) {
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
                            .clickable { onGradientChange(GradientType.FREQUENCY) }
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
                            .clickable { onGradientChange(GradientType.RECENCY) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Recency", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    minLegend,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.width(minLegendWidth),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.width(legendGap))
                LegendBar(uiState = uiState, modifier = Modifier.width(legendWidth).height(20.dp))
                Spacer(modifier = Modifier.width(legendGap))
                Text(
                    maxLegend,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.width(maxLegendWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .widthIn(min = 110.dp)
                        .clickable(onClick = onToggleColorMap)
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Full Color",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
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
        }
    }
}

@Composable
private fun LegendBar(uiState: ScorigamiUiState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (uiState.colorMapType == ColorMapType.RED_SPECTRUM) {
            val slices = 42
            val w = size.width / slices
            for (i in 0 until slices) {
                val sat = ((i + 1) * 2.5f / 100f).coerceIn(0f, 1f)
                drawRect(
                    color = lerp(Color(0xFF606060), Color.Red, sat),
                    topLeft = Offset(i * w, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height)
                )
            }
        } else {
            val grad = listOf(Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
            val step = size.width / (grad.size - 1)
            for (i in 0 until grad.size - 1) {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(grad[i], grad[i + 1]),
                        startX = i * step,
                        endX = (i + 1) * step
                    ),
                    topLeft = Offset(i * step, 0f),
                    size = androidx.compose.ui.geometry.Size(step, size.height)
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(14.dp)
                .padding(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Text("Welcome to", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Image(
                painter = painterResource(id = R.drawable.scorigami_title),
                contentDescription = "Scorigami",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
            Text(
                "Scorigami, a term originally coined by sportswriter Jon Bois, refers to the final score of an NFL game that has never occurred.",
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "This app lets you browse every score combination and see which are Scorigamis. For scores that have occurred, you can see how many games ended in that score and when it last happened.",
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Thank you to pro-football-reference.com for the historical data.", color = Color.White)
        }

        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            color = Color(0xFF9E9E9E),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}
