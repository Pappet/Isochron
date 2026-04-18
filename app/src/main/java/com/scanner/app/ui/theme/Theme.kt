package com.scanner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Teal80 = Color(0xFF80CBC4)
val Teal40 = Color(0xFF00897B)
val TealDark = Color(0xFF004D40)
val Amber80 = Color(0xFFFFE082)
val Amber40 = Color(0xFFFFA000)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = TealDark,
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFA7F0E7),
    secondary = Amber80,
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = Color(0xFFBBC7DB),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
    background = Color(0xFF0A0A0A),
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = TealDark,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE082),
    onSecondaryContainer = Color(0xFF3E2E00),
    tertiary = Color(0xFF4A6375),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    background = Color(0xFFFAFAFA),
)

/**
 * Application theme based on Material 3.
 * Supports dynamic color on Android 12+ and respects system dark theme settings.
 *
 * @param darkTheme Whether to use dark or light color scheme.
 * @param dynamicColor Whether to use Material You dynamic colors if available.
 * @param content The Composable content to apply the theme to.
 */
@Composable
fun ScannerAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
