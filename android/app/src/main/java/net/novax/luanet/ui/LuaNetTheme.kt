package net.novax.luanet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF54D67A),
    onPrimary = Color(0xFF08210F),
    background = Color(0xFF0E1310),
    surface = Color(0xFF151C18),
)
private val Light = lightColorScheme(primary = Color(0xFF167A36), secondary = Color(0xFF3C6850))

@Composable
fun LuaNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

