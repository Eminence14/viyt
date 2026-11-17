package com.viyt.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF5A4E)
val Bg = Color(0xFF0E0E11)
val Card = Color(0xFF18181D)
val Muted = Color(0xFF9A9AA5)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    background = Bg,
    onBackground = Color(0xFFF2F2F5),
    surface = Bg,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Card,
    onSurfaceVariant = Muted,
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFF202027),
    surfaceContainerLow = Color(0xFF141418),
    outline = Color(0xFF34343C),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ViytTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
