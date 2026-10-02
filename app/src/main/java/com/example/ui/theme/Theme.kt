package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TacticalCyanPrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CF3FF),
    secondary = TacticalCyanSecondary,
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D62),
    onSecondaryContainer = Color(0xFFBCE9FF),
    tertiary = TacticalEmerald,
    onTertiary = Color(0xFF003824),
    background = TacticalObsidianBackground,
    onBackground = Color(0xFFE2E8F0),
    surface = TacticalSlateSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = TacticalCardSurface,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = TacticalBorderColor
)

private val LightColorScheme = lightColorScheme(
    primary = CyberPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = CyberSecondaryLight,
    onSecondary = Color.White,
    tertiary = CyberTertiaryLight,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted tactical dark palette by default for maximum impact
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Force tactical dark palette for military / Kali aesthetic
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
