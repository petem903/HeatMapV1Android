package com.yanfeng.thermaldrone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF6D00),          // thermal orange
    onPrimary = Color.Black,
    secondary = Color(0xFF00B0FF),
    onSecondary = Color.Black,
    background = Color(0xFF0D0D0F),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF232328),
    onSurfaceVariant = Color(0xFFBBBBBB),
    error = Color(0xFFFF5252)
)

val SimYellow = Color(0xFFFFD600)
val WarnAmber = Color(0xFFFFAB00)
val OkGreen = Color(0xFF00C853)

@Composable
fun HeatMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
