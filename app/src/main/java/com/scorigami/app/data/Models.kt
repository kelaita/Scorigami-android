package com.scorigami.app.data

import androidx.compose.ui.graphics.Color

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

val FilteredOutRangeColor = Color(0xFF2E2E2E)

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
    val selectedFrequencyStartCount: Int = 1,
    val selectedFrequencyEndCount: Int = 1,
    val selectedRecencyStartYear: Int = 1920,
    val gradientType: GradientType = GradientType.FREQUENCY,
    val colorMapType: ColorMapType = ColorMapType.FULL_SPECTRUM
)
