package com.viyt.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// YouTube's dark theme.
val YtBg = Color(0xFF0F0F0F)
val YtRaised = Color(0xFF212121)
val YtChip = Color(0xFF272727)
val YtChipPressed = Color(0xFF3F3F3F)
val YtText = Color(0xFFF1F1F1)
val YtSecondary = Color(0xFFAAAAAA)
val YtRed = Color(0xFFFF0033)
val YtDivider = Color(0xFF303030)

private val scheme = darkColorScheme(
    primary = YtRed,
    onPrimary = Color.White,
    secondary = YtText,
    background = YtBg,
    onBackground = YtText,
    surface = YtBg,
    onSurface = YtText,
    surfaceVariant = YtChip,
    onSurfaceVariant = YtSecondary,
    surfaceContainer = YtRaised,
    surfaceContainerHigh = YtRaised,
    surfaceContainerLow = YtBg,
    surfaceContainerLowest = YtBg,
    outline = YtDivider,
    outlineVariant = YtDivider,
    error = Color(0xFFFF6B6B),
)

private val type = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ViytTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
