package com.faisal.iltizam.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Dark = darkColorScheme(
    primary = Color(0xFF7FA8FF),
    secondary = Color(0xFF9BC9A8),
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A20),
    surfaceVariant = Color(0xFF222630),
)
private val Light = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    secondary = Color(0xFF2E7D4F),
)

@Composable
fun IltizamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        // واجهة عربية من اليمين لليسار دائماً
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
