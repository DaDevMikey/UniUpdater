package com.example.uniupdater.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppTheme {
    ONEUI
}

object ThemeTokens {
    // Custom Premium Dark Color Scheme
    val MidnightBackground = Color(0xFF0C0D14)
    val CardSurface = Color(0xFF141622)
    val AccentIndigo = Color(0xFF5E6AD2)
    val AccentCyan = Color(0xFF29B6F6)
    val OneUiAccent = Color(0xFF1A73E8) // Classic Blue
    
    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9CA3AF)
    val DividerColor = Color(0xFF23263B)

    // Layout configuration
    val OneUiCornerRadius = 28.dp
    val OneUiPadding = 24.dp
}
