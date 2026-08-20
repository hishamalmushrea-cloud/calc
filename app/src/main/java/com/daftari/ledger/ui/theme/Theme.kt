package com.daftari.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0F7B5A)
private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Color(0xFF1B4F72),
    background = Color(0xFFF4F7F6),
    surface = Color.White,
    error = Color(0xFFB3261E)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5DDBB3),
    background = Color(0xFF0E1614),
    surface = Color(0xFF16211E)
)

@Composable
fun DaftariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
