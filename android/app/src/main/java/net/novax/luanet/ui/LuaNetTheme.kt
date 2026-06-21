package net.novax.luanet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Color(0xFF54D67A),
    onPrimary = Color(0xFF08210F),
    primaryContainer = Color(0xFF153D23),
    onPrimaryContainer = Color(0xFF9AF5B3),
    secondary = Color(0xFFB6CCBD),
    secondaryContainer = Color(0xFF293A2F),
    tertiary = Color(0xFF9FCBDD),
    background = Color(0xFF0A0F0C),
    onBackground = Color(0xFFE3E9E4),
    surface = Color(0xFF101713),
    surfaceVariant = Color(0xFF1A231E),
    onSurfaceVariant = Color(0xFFB9C3BB),
    outline = Color(0xFF829087),
    error = Color(0xFFFFB4AB),
)
private val Light = lightColorScheme(
    primary = Color(0xFF087A37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F5B9),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF4F6354),
    secondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFF3B6472),
    background = Color(0xFFF7FBF7),
    surface = Color(0xFFF7FBF7),
    surfaceVariant = Color(0xFFE5EBE5),
    onSurfaceVariant = Color(0xFF414942),
)

private val LuaNetTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val LuaNetShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun LuaNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = LuaNetTypography,
        shapes = LuaNetShapes,
        content = content,
    )
}
