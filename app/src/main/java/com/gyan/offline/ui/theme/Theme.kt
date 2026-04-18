package com.gyan.offline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GyanColorScheme = lightColorScheme(
    primary          = GyanGreen,
    onPrimary        = GyanOnGreen,
    primaryContainer = GyanGreenLight,
    secondary        = GyanAmber,
    background       = GyanBackground,
    surface          = GyanSurface,
    onBackground     = GyanGreenDark,
    onSurface        = GyanGreenDark,
)

@Composable
fun GyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GyanColorScheme,
        content     = content,
    )
}
