package com.example.uniupdater.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumColorScheme = darkColorScheme(
    primary = ThemeTokens.AccentIndigo,
    onPrimary = Color.White,
    secondary = ThemeTokens.AccentCyan,
    onSecondary = Color.Black,
    background = ThemeTokens.MidnightBackground,
    onBackground = ThemeTokens.TextPrimary,
    surface = ThemeTokens.CardSurface,
    onSurface = ThemeTokens.TextPrimary,
    surfaceVariant = ThemeTokens.CardSurface,
    onSurfaceVariant = ThemeTokens.TextSecondary,
    outline = ThemeTokens.DividerColor
)

@Composable
fun UniUpdaterTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = PremiumColorScheme,
    typography = Typography,
    content = content
  )
}
