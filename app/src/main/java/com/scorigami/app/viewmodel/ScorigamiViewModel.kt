package com.scorigami.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scorigami.app.data.BoardCell
import com.scorigami.app.data.ColorMapType
import com.scorigami.app.data.FilteredOutRangeColor
import com.scorigami.app.data.GameRecord
import com.scorigami.app.data.GradientType
import com.scorigami.app.data.ScoreDetails
import com.scorigami.app.data.ScorigamiUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

class ScorigamiViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(ScorigamiUiState())
        private set

    private val sourceUrl = "https://pomace.net/scores.html"
    private val gameUrlTemplate = "https://www.pro-football-reference.com/boxscores/game_scores_find.cgi?pts_win=WWWW&pts_lose=LLLL"
    private val fullSpectrumPalette = buildFullSpectrumPalette()
    private val prefs = application.getSharedPreferences("scorigami_refresh", Context.MODE_PRIVATE)
    private val refreshIntervalMs = 24L * 60L * 60L * 1000L
    private val lastPollKey = "last_poll_at"
    private var isRefreshingData = false

    init {
        loadScores(showLoading = true, isBackgroundRefresh = false)
    }

    fun setGradientType(type: GradientType) {
        uiState = uiState.copy(gradientType = type)
    }

    fun updateRecencyStartYear(year: Int) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        uiState = uiState.copy(
            selectedRecencyStartYear = year.coerceIn(uiState.earliestGameYear, currentYear)
        )
    }

    fun updateFrequencyRange(startCount: Int, endCount: Int) {
        val clampedStart = startCount.coerceIn(1, uiState.highestCounter)
        val clampedEnd = endCount.coerceIn(clampedStart, uiState.highestCounter)
        uiState = uiState.copy(
            selectedFrequencyStartCount = clampedStart,
            selectedFrequencyEndCount = clampedEnd
        )
    }

    fun isRecencyFilterActive(): Boolean {
        return uiState.gradientType == GradientType.RECENCY &&
            uiState.selectedRecencyStartYear > uiState.earliestGameYear
    }

    fun isFrequencyFilterActive(): Boolean {
        return uiState.gradientType == GradientType.FREQUENCY &&
            (uiState.selectedFrequencyStartCount > 1 || uiState.selectedFrequencyEndCount < uiState.highestCounter)
    }

    fun toggleColorMapType() {
        val next = if (uiState.colorMapType == ColorMapType.FULL_SPECTRUM) {
            ColorMapType.RED_SPECTRUM
        } else {
            ColorMapType.FULL_SPECTRUM
        }
        uiState = uiState.copy(colorMapType = next)
    }

    fun refreshDataInBackground() {
        if (isRefreshingData) return
        loadScores(showLoading = uiState.board.isEmpty(), isBackgroundRefresh = true)
    }

    fun shouldAutoRefreshOnResume(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (uiState.isLoading || isRefreshingData) return false
        val lastPollAt = prefs.getLong(lastPollKey, 0L)
        if (lastPollAt <= 0L) return false
        return nowMs - lastPollAt >= refreshIntervalMs
    }

    fun getMinLegendValue(): String {
        return if (uiState.gradientType == GradientType.FREQUENCY) {
            uiState.selectedFrequencyStartCount.toString()
        } else {
            uiState.selectedRecencyStartYear.toString()
        }
    }

    fun getMaxLegendValue(): String {
        return if (uiState.gradientType == GradientType.FREQUENCY) {
            uiState.selectedFrequencyEndCount.toString()
        } else {
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        }
    }

    fun getCellColor(cell: BoardCell): Color {
        if (!cell.validScore) {
            return Color.Black
        }
        if (cell.occurrences == 0) {
            return Color.Black
        }
        val sat = if (uiState.gradientType == GradientType.FREQUENCY) {
            if (!isCellWithinFrequencyRange(cell)) {
                return FilteredOutRangeColor
            }
            currentFrequencySaturation(cell)
        } else {
            if (!isCellVisibleForCurrentFilters(cell)) {
                return FilteredOutRangeColor
            }
            currentRecencySaturation(cell)
        }
        if (sat <= 0f) {
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
            if (!isCellWithinFrequencyRange(cell)) {
                return Color.White
            }
            currentFrequencySaturation(cell)
        } else {
            if (!isCellVisibleForCurrentFilters(cell)) {
                return Color.White
            }
            currentRecencySaturation(cell)
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

    private fun isCellWithinFrequencyRange(cell: BoardCell): Boolean {
        return cell.occurrences > 0 &&
            cell.occurrences >= uiState.selectedFrequencyStartCount &&
            cell.occurrences <= uiState.selectedFrequencyEndCount
    }

    private fun isCellVisibleForCurrentFilters(cell: BoardCell): Boolean {
        if (!cell.validScore || cell.label.isEmpty()) return false
        return extractYear(cell.lastGame) >= uiState.selectedRecencyStartYear
    }

    private fun currentRecencySaturation(cell: BoardCell): Float {
        val floor = if (isRecencyFilterActive()) 0.01f else 0f
        return getSaturation(
            minValue = uiState.selectedRecencyStartYear,
            maxValue = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            value = extractYear(cell.lastGame),
            skewLower = floor,
            skewUpper = 1f
        )
    }

    private fun currentFrequencySaturation(cell: BoardCell): Float {
        val floor = 0.01f
        return getSaturation(
            minValue = uiState.selectedFrequencyStartCount,
            maxValue = uiState.selectedFrequencyEndCount,
            value = cell.occurrences,
            skewLower = floor,
            skewUpper = 0.55f
        )
    }

    private fun loadScores(showLoading: Boolean, isBackgroundRefresh: Boolean) {
        if (isRefreshingData) return
        isRefreshingData = true
        viewModelScope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    parseGames(URL(sourceUrl).readText())
                }
                val boardData = buildBoard(parsed)
                val nextFrequencyStart = uiState.selectedFrequencyStartCount.coerceIn(1, boardData.highestCounter)
                val nextFrequencyEnd = if (uiState.selectedFrequencyEndCount >= uiState.highestCounter) {
                    boardData.highestCounter
                } else {
                    uiState.selectedFrequencyEndCount.coerceIn(nextFrequencyStart, boardData.highestCounter)
                }
                val nextRecencyStartYear = if (uiState.selectedRecencyStartYear <= uiState.earliestGameYear) {
                    boardData.earliestGameYear
                } else {
                    uiState.selectedRecencyStartYear.coerceIn(boardData.earliestGameYear, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                }
                uiState = boardData.copy(
                    isLoading = false,
                    error = null,
                    gradientType = uiState.gradientType,
                    colorMapType = uiState.colorMapType,
                    selectedFrequencyStartCount = nextFrequencyStart,
                    selectedFrequencyEndCount = nextFrequencyEnd,
                    selectedRecencyStartYear = nextRecencyStartYear
                )
            } catch (e: Exception) {
                if (showLoading || !isBackgroundRefresh || uiState.board.isEmpty()) {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Unable to load score data. Check internet connection and try again."
                    )
                } else {
                    uiState = uiState.copy(isLoading = false)
                }
            } finally {
                prefs.edit().putLong(lastPollKey, System.currentTimeMillis()).apply()
                isRefreshingData = false
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
            earliestGameYear = earliestYear,
            selectedFrequencyStartCount = 1,
            selectedFrequencyEndCount = highestCounter,
            selectedRecencyStartYear = earliestYear
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
