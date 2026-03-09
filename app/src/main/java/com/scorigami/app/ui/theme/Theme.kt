package com.scorigami.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ScorigamiDarkScheme = darkColorScheme(
    primary = AccentBlue,
    background = BackgroundBlack,
    surface = BackgroundBlack,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun ScorigamiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScorigamiDarkScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
